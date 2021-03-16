/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package unit.services.email

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Span}
import uk.gov.hmrc.customs.rosmfrontend.connector.httpparsers.{
  HttpErrorResponse,
  ServiceUnavailable,
  VerifiedEmailRequest,
  VerifiedEmailResponse
}
import uk.gov.hmrc.customs.rosmfrontend.connector.{UpdateCustomsDataStoreConnector, UpdateVerifiedEmailConnector}
import uk.gov.hmrc.customs.rosmfrontend.domain.email.UpdateVerifiedEmailResponse
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.MessagingServiceParam.{
  formBundleIdParamName,
  positionParamName,
  Fail
}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.{MessagingServiceParam, RequestCommon, ResponseCommon}
import uk.gov.hmrc.customs.rosmfrontend.services.RequestCommonGenerator
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.UpdateVerifiedEmailService
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UpdateVerifiedEmailServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ScalaFutures {
  override implicit def patienceConfig: PatienceConfig =
    super.patienceConfig.copy(timeout = Span(defaultTimeout.toMillis, Millis))
  implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  val ExpectedRequestCommon =
    RequestCommon("CDS", new DateTime("2016-07-08T08:35:13Z"), "4482baa8-1c84-4d23-a8db-3fc180325e7a")
  private val mockConnector = mock[UpdateVerifiedEmailConnector]
  private val mockUpdateDataStoreConnector = mock[UpdateCustomsDataStoreConnector]
  private val requestCommonGenerator = mock[RequestCommonGenerator]
  private val eoriNumber = "GBXXXXXXXXXXXX"
  private val email = "test@email.com"
  private val dateTime = DateTime.now()
  val service = new UpdateVerifiedEmailService(requestCommonGenerator, mockConnector, mockUpdateDataStoreConnector)

  private val bundleIdUpdateVerifiedEmailResponse = VerifiedEmailResponse(
    UpdateVerifiedEmailResponse(
      ResponseCommon("OK", None, dateTime, Some(List(MessagingServiceParam(formBundleIdParamName, "testValue"))))
    )
  )
  private val businessErrorUpdateVerifiedEmailResponse = VerifiedEmailResponse(
    UpdateVerifiedEmailResponse(
      ResponseCommon(
        "OK",
        Some("004 - Duplicate Acknowledgement Reference"),
        dateTime,
        Some(List(MessagingServiceParam(positionParamName, Fail)))
      )
    )
  )
  private val serviceUnavailableResponse = ServiceUnavailable

  def mockGetEmailVerificationState(response: Either[HttpErrorResponse, VerifiedEmailResponse]): Unit =
    when(mockConnector.updateVerifiedEmail(any[VerifiedEmailRequest], any[Option[String]])(any[HeaderCarrier])) thenReturn Future
      .successful(response)
  when(requestCommonGenerator.generate())
    .thenReturn(ExpectedRequestCommon)

  override protected def beforeEach(): Unit =
    reset(mockConnector)
  when(mockUpdateDataStoreConnector.updateCustomsDataStore(any())(any())) thenReturn Future
    .successful(())

  "Calling UpdateVerifiedEmailService updateVerifiedEmail" should {
    "return Some(true) when VerifiedEmailResponse returned with bundleId" in {
      mockGetEmailVerificationState(Right(bundleIdUpdateVerifiedEmailResponse))
      when(mockUpdateDataStoreConnector.updateCustomsDataStore(any())(any())) thenReturn Future
        .successful(())
      service
        .updateVerifiedEmail(None, email, eoriNumber)
        .futureValue shouldBe Some(true)
    }

    "return None when VerifiedEmailResponse returned without bundleId" in {
      mockGetEmailVerificationState(Right(businessErrorUpdateVerifiedEmailResponse))
      service
        .updateVerifiedEmail(None, email, eoriNumber)
        .futureValue shouldBe Some(false)

    }

    "return None when HttpErrorResponse returned" in {
      mockGetEmailVerificationState(Left(serviceUnavailableResponse))

      service
        .updateVerifiedEmail(None, email, eoriNumber)
        .futureValue shouldBe None

    }
  }
}
