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

package unit.connector

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.customs.rosmfrontend.audit.Auditable
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.connector.{RegistrationDisplayConnector, ServiceUnavailableResponse}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Address
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching.{ContactResponse, IndividualResponse}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.registration._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException}
import util.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class RegistrationDisplayConnectorSpec extends UnitSpec with MockitoSugar {

  val mockHttpClient = mock[HttpClient]
  val mockConfig = mock[AppConfig]
  val mockAudit = mock[Auditable]
  val testConnector = new RegistrationDisplayConnector(mockHttpClient, mockConfig, mockAudit) {
    override val url: String = "service url"
  }

  val registrationDisplayRequest = RegistrationDisplayRequest(RequestCommon(DateTime.now, Seq.empty))

  val responseCommon = ResponseCommon("OK", None, "2016-09-02T09:30:47Z", taxPayerID = Some("1111111111"))

  val individualResponse = IndividualResponse("John", None, "Doe", Some("1989-01-01"))

  val address = Address("Line1", Some("Line2"), None, None, Some("postcode"), "GB")

  val contactDetails = ContactResponse(Some("01234567890"), None, None, Some("test@example.com"))

  val responseDetail = ResponseDetail(
    "XY0001111111111",
    None,
    None,
    true,
    false,
    true,
    Some(individualResponse),
    None,
    address,
    contactDetails
  )

  val responseHolder = RegistrationDisplayResponseHolder(
    RegistrationDisplayResponse(responseCommon, Some(responseDetail))
  )

  "RegistrationDisplayConnector" should {
    "return successful response when registration display service call succeeds" in {
      when(
        mockHttpClient.POST[RegistrationDisplayRequestHolder, RegistrationDisplayResponseHolder](any(), any(), any())(
          any(),
          any(),
          any(),
          any()
        )
      ).thenReturn(Future.successful(responseHolder))
      await(
        testConnector.registrationDisplay(RegistrationDisplayRequestHolder(registrationDisplayRequest))(
          HeaderCarrier(),
          ExecutionContext.global
        )
      ) shouldBe Right(RegistrationDisplayResponse(responseCommon, Some(responseDetail)))
    }

    "return service unavailable response when registration display service call fails" in {
      when(
        mockHttpClient.POST[RegistrationDisplayRequestHolder, RegistrationDisplayResponseHolder](any(), any(), any())(
          any(),
          any(),
          any(),
          any()
        )
      ).thenReturn(Future.failed(new HttpException("Service Unavailable", 500)))
      await(
        testConnector.registrationDisplay(RegistrationDisplayRequestHolder(registrationDisplayRequest))(
          HeaderCarrier(),
          ExecutionContext.global
        )
      ) shouldBe Left(ServiceUnavailableResponse)
    }
  }
}
