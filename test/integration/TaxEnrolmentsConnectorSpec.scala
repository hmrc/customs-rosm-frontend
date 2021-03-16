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

package integration

import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{BAD_REQUEST, NO_CONTENT}
import play.mvc.Http.Status.{FORBIDDEN, INTERNAL_SERVER_ERROR}
import uk.gov.hmrc.customs.rosmfrontend.connector.TaxEnrolmentsConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.{TaxEnrolmentsRequest, TaxEnrolmentsResponse}
import uk.gov.hmrc.http._
import util.externalservices.ExternalServicesConfig._
import util.externalservices.TaxEnrolmentsService

class TaxEnrolmentsConnectorSpec extends IntegrationTestsSpec with ScalaFutures {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      Map(
        "microservice.services.tax-enrolments.host" -> Host,
        "microservice.services.tax-enrolments.port" -> Port,
        "microservice.services.tax-enrolments.context" -> "tax-enrolments",
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> Host,
        "auditing.consumer.baseUri.port" -> Port
      )
    )
    .build()

  private lazy val taxEnrolmentsConnector = app.injector.instanceOf[TaxEnrolmentsConnector]
  private val safeId = "XE111123456789"
  private val expectedGetUrl = s"/tax-enrolments/businesspartners/$safeId/subscriptions"

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val formBundleId = "bundle-id"
  private val expectedPutUrl = s"/tax-enrolments/subscriptions/$formBundleId/issuer"

  val responseWithOk: JsValue =
    Json.parse("""[{
        |    "created": 1482329348256,
        |    "lastModified": 1482329348256,
        |    "credId": "d8474a25-71b6-45ed-859e-77dd5f087be6",
        |    "serviceName": "516b9976-00fd-4da6-b59c-4d09054912bb",
        |    "identifiers": [{
        |        "key": "f52b4104-7e69-4bb8-baec-5aaf9897e849",
        |        "value": "97541e00-a712-452b-af21-0be4db0b7b1d"
        |    }, {
        |        "key": "d3e222b8-b9ff-4571-8f83-1a0fbc221195",
        |        "value": "547d7434-8c33-431d-bffa-35a7d1103c30"
        |    }],
        |    "callback": "url passed in by the subscriber service",
        |    "state": "PENDING",
        |    "etmpId": "da4053bf-2ea3-4cb8-bb9c-65b70252b656",
        |    "groupIdentifier": "c808798d-0d81-4a34-82c2-bbf13b3ac2fa"
        |}]""".stripMargin)

  val validTaxEnrolmentsIssuerRequestJson = Json.parse("""{
                                                     |    "serviceName": "HMRC-CUS-ORG",
                                                     |    "identifiers": [
                                                     |        {
                                                     |            "key": "EORINUMBER",
                                                     |            "value": "GB9999999999"
                                                     |        }
                                                     |    ],
                                                     |    "verifiers": [
                                                     |        {
                                                     |            "key": "DATEOFESTABLISHMENT",
                                                     |            "value": "28/04/2010"
                                                     |        }
                                                     |    ],
                                                     |    "subscriptionState": "SUCCEEDED"
                                                     |
                                                     |}""".stripMargin)

  private val taxEnrolmentsRequest = validTaxEnrolmentsIssuerRequestJson.as[TaxEnrolmentsRequest]

  before {
    resetMockServer()
  }

  override def beforeAll: Unit =
    startMockServer()

  override def afterAll: Unit =
    stopMockServer()

  "TaxEnrolments" should {
    "return successful response with OK status when TaxEnrolments  returns 200" in {

      TaxEnrolmentsService.returnTheTaxEnrolmentsResponseOK("XE111123456789")
      await(taxEnrolmentsConnector.getEnrolments(safeId)) must be(responseWithOk.as[List[TaxEnrolmentsResponse]])
    }

    "fail when Internal Server Error" in {
      TaxEnrolmentsService.stubTheTaxEnrolmentsResponse(expectedGetUrl, responseWithOk.toString, INTERNAL_SERVER_ERROR)

      val caught: UpstreamErrorResponse = intercept[UpstreamErrorResponse] {
        await(taxEnrolmentsConnector.getEnrolments(safeId))
      }

      caught.statusCode mustBe INTERNAL_SERVER_ERROR
      caught.getMessage must startWith("GET of ")
    }

    "http exception when 4xx status code is received" in {
      TaxEnrolmentsService.stubTheTaxEnrolmentsResponse(expectedGetUrl, responseWithOk.toString, FORBIDDEN)

      val caught: UpstreamErrorResponse = intercept[UpstreamErrorResponse] {
        await(taxEnrolmentsConnector.getEnrolments(safeId))
      }

      caught.statusCode mustBe FORBIDDEN
      caught.getMessage must startWith("GET of ")
    }

    "return successful future with correct status when enrolment status service returns good status(204)" in {
      TaxEnrolmentsService.returnEnrolmentResponseWhenReceiveRequest(
        expectedPutUrl,
        validTaxEnrolmentsIssuerRequestJson.toString,
        NO_CONTENT
      )
      taxEnrolmentsConnector.enrol(taxEnrolmentsRequest, formBundleId).futureValue mustBe NO_CONTENT
    }

    "return successful future with correct status when enrolment status service returns any fail status" in {
      TaxEnrolmentsService.returnEnrolmentResponseWhenReceiveRequest(
        expectedPutUrl,
        validTaxEnrolmentsIssuerRequestJson.toString,
        BAD_REQUEST
      )
      taxEnrolmentsConnector.enrol(taxEnrolmentsRequest, formBundleId).futureValue mustBe BAD_REQUEST
    }
  }
}
