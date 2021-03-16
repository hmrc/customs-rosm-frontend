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

package unit.services.subscription

import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import play.api.test.Helpers
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.connector.TaxEnrolmentsConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.{Eori, SafeId, TaxEnrolmentsRequest, TaxEnrolmentsResponse}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.TaxEnrolmentsService
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec

import scala.concurrent.Future

class TaxEnrolmentsServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfter {

  private val mockTaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]
  private implicit val ec = Helpers.stubControllerComponents().executionContext
  private val service = new TaxEnrolmentsService(mockTaxEnrolmentsConnector)
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  before {
    reset(mockTaxEnrolmentsConnector)
  }

  val serviceName = "HMRC-CUS-ORG"
  val safeId = SafeId("safeid")
  val eori = Eori("GB99999999")
  val formBundleId = "formBundleId"
  val date = LocalDate.parse("2010-04-28")
  val taxEnrolmentResponse = TaxEnrolmentsResponse(serviceName = serviceName)
  "TaxEnrolmentsService" should {

    "determine enrolmentExists" in {
      when(mockTaxEnrolmentsConnector.getEnrolments(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(List(taxEnrolmentResponse)))

      await(service.doesEnrolmentExist(safeId)) shouldBe true

      verify(mockTaxEnrolmentsConnector).getEnrolments(any[String])(meq(headerCarrier))
    }

    "make  issuer call" in {
      when(
        mockTaxEnrolmentsConnector
          .enrol(any[TaxEnrolmentsRequest], any[String])(any[HeaderCarrier])
      ).thenReturn(Future.successful(NO_CONTENT))

      await(service.issuerCall(formBundleId, eori, Some(date))) shouldBe NO_CONTENT

      verify(mockTaxEnrolmentsConnector)
        .enrol(any[TaxEnrolmentsRequest], any[String])(any[HeaderCarrier])
    }
  }
}
