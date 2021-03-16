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

package util.externalservices

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import play.mvc.Http.HeaderNames.CONTENT_TYPE
import play.mvc.Http.MimeTypes.JSON
import play.mvc.Http.Status._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.email.EmailStatus
import uk.gov.hmrc.http.HeaderCarrier

object Save4LaterService {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val body = """{"name":"test","tel":12}""".stripMargin

  val getOkBody =
    """{
      |            "internalId" : {
      |                "id" : "a14b5b18-9444-4ea1-99ad-a667047682ad"
      |            },
      |            "safeId" : {
      |                "id" : "XA1234567890123"
      |            }
      |        }
    """.stripMargin

  val getMongoSafeIdOkBody =
    """{
      |        "safeId" : {
      |            "id" : "ORG000123456789"
      |        },
      |        "orgType" : {
      |            "id" : "third-country-organisation"
      |        }
      |    }
    """.stripMargin

  val responseJson = Json.parse(body)

  val id = "id-12345678"
  val emailKey = "email"

  val groupdId = "groupId-abcd-1234"
  val groupdIdRcm = "gg-id-rcm-cases"
  val key = "cachedGroupId"

  case class User(name: String, tel: Int)

  object User {
    implicit val jsonFormat = Json.format[User]
  }

  val expectedUrl = s"/save4later/$id/$emailKey"
  val expectedUrlGroupIdRcm = s"/save4later/$groupdIdRcm/$key"
  val expectedPutGroupIdUrlRcm = s"/save4later/$groupdIdRcm/$key"
  val expectedPutGroupIdUrl = s"/save4later/$groupdId/$key"
  val expectedDeleteUrl = s"/save4later/$id"
  val expectedDeleteUrlGroupId = s"/save4later/$groupdIdRcm"

  val regexIntId = "([0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12})"

  private val intId = "a14b5b18-9444-4ea1-99ad-a667047682ad"
  private val safeIdKey = "safeId"
  private val orgTypeKey = "orgType"
  private val mongoEmailKey = "email"

  private val emailNotVerified = EmailStatus("john.doe@example.com")
  private val emailVerified = EmailStatus("john.doe@example.com", isVerified = true)
  private val emailConfirmedVerified = EmailStatus("john.doe@example.com", isVerified = true, Some(true))

  private val emailNotVerifiedJson = Json.toJson(emailNotVerified).toString()
  private val emailVerifiedJson = Json.toJson(emailVerified).toString()
  private val emailConfirmedVerifiedJson = Json.toJson(emailConfirmedVerified).toString()

  val safeIdMongoStubUrl = s"/save4later/$regexIntId/$safeIdKey"

  val emailMongoStubUrl = s"/save4later/$regexIntId/$mongoEmailKey"

  val safeIdOrgTypeMongoStubUrl = s"/save4later/$regexIntId/$orgTypeKey"

  def stubSafeIdMongoGET_OK() =
    stubSave4LaterGETResponse(safeIdMongoStubUrl, getMongoSafeIdOkBody, OK)

  def stubSafeIdMongoNotFound() =
    stubSave4LaterGETResponse(safeIdMongoStubUrl, "", NOT_FOUND)

  def stubSave4LaterPUTMongo() =
    stubSave4LaterPUTGroupIdResponse(safeIdMongoStubUrl, body, CREATED)

  def stubSave4LaterPUTOrgTypeMongo() =
    stubSave4LaterPUTGroupIdResponse(safeIdOrgTypeMongoStubUrl, body, CREATED)

  def stubSave4LaterGET_OK() =
    stubSave4LaterGETResponse(expectedUrl, body, OK)

  def stubSave4LaterGroupRcmGET_OK() =
    stubSave4LaterGETResponse(expectedUrlGroupIdRcm, getOkBody, OK)

  def stubSave4LaterGET_NOTFOUND() =
    stubSave4LaterGETResponse(expectedUrl, body, NOT_FOUND)

  def stubSave4LaterGET_BAD_REQUEST() =
    stubSave4LaterGETResponse(expectedUrl, body, BAD_REQUEST)

  def stubSave4LaterPUT() =
    stubSave4LaterPUTResponse(expectedUrl, body, CREATED)

  def stubSave4LaterPUT_BAD_REQUEST() =
    stubSave4LaterPUTResponse(expectedUrl, body, BAD_REQUEST)

  def stubSave4LaterPUTWithGroupId() =
    stubSave4LaterPUTGroupIdResponse(expectedPutGroupIdUrl, body, CREATED)

  def stubSave4LaterPUTWithRcmGroupId() =
    stubSave4LaterPUTGroupIdResponse(expectedPutGroupIdUrlRcm, body, CREATED)

  def stubSave4LaterDELETE() =
    stubSave4LaterDeleteResponse(expectedDeleteUrl, NO_CONTENT)

  def stubSave4LaterDELETEGroupId() =
    stubSave4LaterDeleteResponse(expectedDeleteUrlGroupId, NO_CONTENT)

  def stubSave4LaterNotFoundDELETE() =
    stubSave4LaterDeleteResponse(expectedDeleteUrl, NOT_FOUND)

  def stubSave4LaterGETResponse(url: String, response: String, status: Int): Unit =
    stubFor(
      get(urlMatching(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(response)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

  def stubSave4LaterPUTResponse(url: String, response: String, status: Int): Unit =
    stubFor(
      put(urlMatching(url))
        .withRequestBody(containing(body))
        .willReturn(
          aResponse()
            .withBody(response)
            .withStatus(status)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

  def stubSave4LaterPUTGroupIdResponse(url: String, response: String, status: Int): Unit =
    stubFor(
      put(urlMatching(url))
        .willReturn(
          aResponse()
            .withBody(response)
            .withStatus(status)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

  def stubSave4LaterDeleteResponse(url: String, status: Int): Unit =
    stubFor(
      delete(urlMatching(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

  def stubSave4LaterEmailNotVerifiedGET() =
    stubSave4LaterGETResponse(emailMongoStubUrl, emailNotVerifiedJson, OK)
  def stubSave4LaterEmailVerifiedGetEmail() =
    stubSave4LaterGETResponse(emailMongoStubUrl, emailVerifiedJson, OK)

  def stubSave4LaterEmailVerifiedGetConfirmed() =
    stubSave4LaterGETResponse(emailMongoStubUrl, emailConfirmedVerifiedJson, OK)

  def stubSave4LaterPUTEmail() = {
    stubSave4LaterPUTEmailNotVerifiedResponse(emailMongoStubUrl, emailNotVerifiedJson, CREATED)
    stubSave4LaterPUTEmailVerifiedResponse(emailMongoStubUrl, emailVerifiedJson, CREATED)
  }

  def stubSave4LaterPUTEmailNotVerified() =
    stubSave4LaterPUTEmailNotVerifiedResponse(emailMongoStubUrl, emailNotVerifiedJson, CREATED)

  def stubSave4LaterEmailConfirmedPUTEmail() = {
    stubSave4LaterPUTEmailVerifiedResponse(
      emailMongoStubUrl,
      emailConfirmedVerifiedJson,
      CREATED,
      emailConfirmedVerifiedJson
    )

    stubSave4LaterPUTEmailVerifiedResponse(
      emailMongoStubUrl,
      emailConfirmedVerifiedJson,
      CREATED,
      emailConfirmedVerifiedJson
    )
  }

  def stubSave4LaterGETResponseEmail(url: String, response: String, status: Int): Unit =
    stubFor(
      get(urlMatching(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(response)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

  def stubSave4LaterPUTEmailNotVerifiedResponse(url: String, response: String, status: Int): Unit =
    stubFor(
      put(urlMatching(url))
        .withRequestBody(containing(emailNotVerifiedJson))
        .willReturn(
          aResponse()
            .withBody(response)
            .withStatus(status)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

  def stubSave4LaterPUTEmailVerifiedResponse(
    url: String,
    response: String,
    status: Int,
    body: String = emailVerifiedJson
  ): Unit =
    stubFor(
      put(urlMatching(url))
        .withRequestBody(containing(body))
        .willReturn(
          aResponse()
            .withBody(response)
            .withStatus(status)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

}
