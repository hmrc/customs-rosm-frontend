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
import play.mvc.Http.Status._
import uk.gov.hmrc.customs.rosmfrontend.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.{EnrolmentResponse, EnrolmentStoreProxyResponse, Eori, GroupIds}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import util.externalservices.EnrolmentStoreProxyService
import util.externalservices.ExternalServicesConfig._

class EnrolmentStoreProxyConnectorSpec extends IntegrationTestsSpec with ScalaFutures {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      Map(
        "microservice.services.enrolment-store-proxy.host" -> Host,
        "microservice.services.enrolment-store-proxy.port" -> Port,
        "microservice.services.enrolment-store-proxy.context" -> "enrolment-store-proxy",
        "auditing.enabled" -> false,
        "auditing.consumer.baseUri.host" -> Host,
        "auditing.consumer.baseUri.port" -> Port
      )
    )
    .build()

  private lazy val enrolmentStoreProxyConnector = app.injector.instanceOf[EnrolmentStoreProxyConnector]
  private val groupId = "2e4589d9-484c-468a-8099-02a06fb1cd8c"
  private val expectedGetUrl =
    s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments?type=principal&service=HMRC-CUS-ORG"
  private val expectedGetUrlForAllEnrolments =
    s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments?type=principal"
  val cdsEnrolmentPattern = s"HMRC-CUS-ORG~EORINumber~%s"
  val eori = Eori("GB11111111111111")
  val enrolmentKey = cdsEnrolmentPattern.format(eori.id)
  private val expectedGroupsGetUrl =
    s"/enrolment-store-proxy/enrolment-store/enrolments/$enrolmentKey/groups"

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val responseWithOk: JsValue =
    Json.parse {
      """{
        |	"startRecord": 1,
        |	"totalRecords": 2,
        |	"enrolments": [{
        |			"service": "HMRC-CUS-ORG",
        |			"state": "NotYetActivated",
        |			"friendlyName": "My First Client's SA Enrolment",
        |			"enrolmentDate": "2018-10-05T14:48:00.000Z",
        |			"failedActivationCount": 1,
        |			"activationDate": "2018-10-13T17:36:00.000Z",
        |			"identifiers": [{
        |				"key": "UTR",
        |				"value": "1111111111"
        |			}]
        |		},
        |		{
        |			"service": "HMRC-CUS-ORG",
        |			"state": "Activated",
        |			"friendlyName": "My Second Client's SA Enrolment",
        |			"enrolmentDate": "2017-06-25T12:24:00.000Z",
        |			"failedActivationCount": 1,
        |			"activationDate": "2017-07-01T09:52:00.000Z",
        |			"identifiers": [{
        |				"key": "UTR",
        |				"value": "2234567890"
        |			}]
        |		}
        |	]
        |}""".stripMargin
    }

  val responseWithGroups = Json.parse { """{
                                          |    "principalGroupIds": [
                                          |       "ABCEDEFGI1234567",
                                          |       "ABCEDEFGI1234568"
                                          |    ],
                                          |    "delegatedGroupIds": [
                                          |       "ABCEDEFGI1234567",
                                          |       "ABCEDEFGI1234568"
                                          |    ]
                                          |}""".stripMargin }

  before {
    resetMockServer()
  }

  override def beforeAll: Unit =
    startMockServer()

  override def afterAll: Unit =
    stopMockServer()

  "EnrolmentStoreProxy for CDS enrolment" should {
    "return successful response with OK status when Enrolment Store Proxy returns 200" in {
      EnrolmentStoreProxyService.returnEnrolmentStoreProxyResponseOkForCds("2e4589d9-484c-468a-8099-02a06fb1cd8c")
      await(enrolmentStoreProxyConnector.getCdsEnrolmentByGroupId(groupId)) must be(
        responseWithOk.as[EnrolmentStoreProxyResponse]
      )
    }

    "return No Content status when no data is returned in response" in {
      EnrolmentStoreProxyService.stubTheEnrolmentStoreProxyResponse(expectedGetUrl, "", NO_CONTENT)
      enrolmentStoreProxyConnector.getCdsEnrolmentByGroupId(groupId).futureValue mustBe EnrolmentStoreProxyResponse(
        enrolments = List.empty[EnrolmentResponse]
      )
    }

    "fail when Service unavailable" in {
      EnrolmentStoreProxyService.stubTheEnrolmentStoreProxyResponse(
        expectedGetUrl,
        responseWithOk.toString(),
        SERVICE_UNAVAILABLE
      )

      val caught = intercept[BadRequestException] {
        await(enrolmentStoreProxyConnector.getCdsEnrolmentByGroupId(groupId))
      }

      caught.getMessage must startWith("Enrolment Store Proxy Status : 503")
    }

    "http exception when 4xx status code is received" in {
      EnrolmentStoreProxyService.stubTheEnrolmentStoreProxyResponse(
        expectedGetUrl,
        responseWithOk.toString(),
        BAD_REQUEST
      )

      val caught = intercept[BadRequestException] {
        await(enrolmentStoreProxyConnector.getCdsEnrolmentByGroupId(groupId))
      }
      caught.getMessage must startWith("Enrolment Store Proxy Status : 400")
    }
  }

  "EnrolmentStoreProxy for all enrolments" should {
    "return successful response with OK status when Enrolment Store Proxy returns 200" in {
      EnrolmentStoreProxyService.returnEnrolmentStoreProxyResponseOkForAll("2e4589d9-484c-468a-8099-02a06fb1cd8c")
      await(enrolmentStoreProxyConnector.getAllEnrolmentsByGroupId(groupId)) must be(
        responseWithOk.as[EnrolmentStoreProxyResponse]
      )
    }

    "return No Content status when no data is returned in response" in {
      EnrolmentStoreProxyService.stubTheEnrolmentStoreProxyResponse(expectedGetUrlForAllEnrolments, "", NO_CONTENT)
      enrolmentStoreProxyConnector.getAllEnrolmentsByGroupId(groupId).futureValue mustBe EnrolmentStoreProxyResponse(
        enrolments = List.empty[EnrolmentResponse]
      )
    }

    "fail when Service unavailable" in {
      EnrolmentStoreProxyService.stubTheEnrolmentStoreProxyResponse(
        expectedGetUrlForAllEnrolments,
        responseWithOk.toString(),
        SERVICE_UNAVAILABLE
      )

      val caught = intercept[BadRequestException] {
        await(enrolmentStoreProxyConnector.getAllEnrolmentsByGroupId(groupId))
      }

      caught.getMessage must startWith("Enrolment Store Proxy Status : 503")
    }

    "http exception when 4xx status code is received" in {
      EnrolmentStoreProxyService.stubTheEnrolmentStoreProxyResponse(
        expectedGetUrlForAllEnrolments,
        responseWithOk.toString(),
        BAD_REQUEST
      )

      val caught = intercept[BadRequestException] {
        await(enrolmentStoreProxyConnector.getAllEnrolmentsByGroupId(groupId))
      }
      caught.getMessage must startWith("Enrolment Store Proxy Status : 400")
    }
  }

  "EnrolmentStoreProxy for Group query" should {

    "return successful response with OK status when Enrolment Store Proxy returns 200" in {
      EnrolmentStoreProxyService.returnGroupsForThisEnrolmentKey(enrolmentKey)
      await(enrolmentStoreProxyConnector.getGroupIdsForCdsEnrolment(eori)) must be(responseWithGroups.as[GroupIds])
    }

    "return No Content status when no data is returned in response" in {
      EnrolmentStoreProxyService.stubTheEnrolmentStoreProxyResponse(expectedGroupsGetUrl, "", NO_CONTENT)
      enrolmentStoreProxyConnector.getGroupIdsForCdsEnrolment(eori).futureValue mustBe GroupIds(
        List.empty[String],
        List.empty[String]
      )

    }

    "fail when Service unavailable" in {
      EnrolmentStoreProxyService.stubTheEnrolmentStoreProxyResponse(
        expectedGroupsGetUrl,
        responseWithGroups.toString(),
        SERVICE_UNAVAILABLE
      )

      val caught = intercept[BadRequestException] {
        await(enrolmentStoreProxyConnector.getGroupIdsForCdsEnrolment(eori))
      }

      caught.getMessage must startWith("Enrolment Store Proxy Status : 503")
    }

    "http exception when 4xx status code is received" in {
      EnrolmentStoreProxyService.stubTheEnrolmentStoreProxyResponse(
        expectedGetUrl,
        responseWithGroups.toString(),
        BAD_REQUEST
      )

      val caught = intercept[BadRequestException] {
        await(enrolmentStoreProxyConnector.getCdsEnrolmentByGroupId(groupId))
      }
      caught.getMessage must startWith("Enrolment Store Proxy Status : 400")
    }
  }
}
