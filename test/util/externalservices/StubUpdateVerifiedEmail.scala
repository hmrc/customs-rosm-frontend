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
import play.mvc.Http.HeaderNames.CONTENT_TYPE
import play.mvc.Http.MimeTypes.JSON
import play.mvc.Http.Status.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, OK}

object StubUpdateVerifiedEmail {

  private val expectedUrl = "/update-verified-email"
  private val updateVerifiedEmailContextPath = urlMatching(expectedUrl)

  val verifiedEmailRequest: String = {
    """{
      |  "updateVerifiedEmailRequest": {
      |    "requestCommon": {
      |      "regime": "CDS",
      |      "receiptDate": "2019-08-22T13:55:55Z",
      |      "acknowledgementReference": "16061ef4ea8740128ac49e9787d3d1f3"
      |    },
      |    "requestDetail": {
      |      "IDType": "EORI",
      |      "IDNumber": "GB123456789012345",
      |      "emailAddress": "test@email.com",
      |      "emailVerificationTimestamp": "2019-08-22T13:55:55Z"
      |    }
      |  }
      |}""".stripMargin
  }

  val updatedVerifiedEmailResponse: String = {
    """{
      |  "updateVerifiedEmailResponse": {
      |    "responseCommon": {
      |      "status": "OK",
      |      "processingDate": "2019-08-22T13:55:53Z",
      |      "returnParameters": [
      |        {
      |          "paramName": "ETMPFORMBUNDLENUMBER",
      |          "paramValue": "123456789012"
      |        }
      |      ]
      |    }
      |  }
      |}""".stripMargin
  }

  val badRequestResponse: String = {
    """{
      |  "errorDetail": {
      |    "timestamp": "2016-08-24T10:15:27Z",
      |    "correlationId": "f058ebd602f74d3f942e904344e8cde5",
      |    "errorCode": "400",
      |    "errorMessage": "Request cannot be processed ",
      |    "source": "JSON validation",
      |    "sourceFaultDetail": {
      |      "detail": [
      |        "Invalid Regime"
      |      ]
      |    }
      |  }
      |}""".stripMargin
  }

  private val serviceUnavailableResponse: String = {
    """{
      |  "errorDetail": {
      |    "timestamp": "2016-08-16T18:15:41Z",
      |    "correlationId": "",
      |    "errorCode": "500",
      |    "errorMessage": "Send timeout",
      |    "source": "ct-api",
      |    "sourceFaultDetail": {
      |      "detail": [
      |        "101504 - Send timeout"
      |      ]
      |    }
      |  }
      |}""".stripMargin
  }

  private val forbiddenResponse: String = {
    """{
      |  "errorDetail": {
      |    "timestamp": "2016-08-24T10:15:27Z",
      |    "correlationId": "f058ebd602f74d3f942e904344e8cde5",
      |    "errorCode": "403",;
      |    "errorMessage": "Forbidden",
      |    "source": "JSON validation",
      |    "sourceFaultDetail": {
      |      "detail": [
      |        "Forbidden Reason"
      |      ]
      |    }
      |  }
      |}""".stripMargin
  }

  private val failResponse200: String = {
    """{
      |  "updateVerifiedEmailResponse": {
      |    "responseCommon": {
      |      "status": "OK",
      |      "statusText": "003 - Request could not be processed",
      |      "processingDate": "2016-08-17T19:33:47Z",
      |      "returnParameters": [
      |        {
      |          "paramName": "POSITION",
      |          "paramValue": "FAIL"
      |        }
      |      ]
      |    }
      |  }
      |}""".stripMargin
  }

  def stubEmailUpdated(okResponse: String) =
    stubUpdateVerifiedEmailRequest(okResponse, OK)

  def stubUpdateVerifiedBadRequest() =
    stubUpdateVerifiedEmailRequest(badRequestResponse, BAD_REQUEST)

  def stubUpdateVerifiedServiceUnavailable() =
    stubUpdateVerifiedEmailRequest(serviceUnavailableResponse, INTERNAL_SERVER_ERROR)

  def stubUpdateVerified200FailResponse() =
    stubUpdateVerifiedEmailRequest(failResponse200, OK)

  def stubUpdateVerifiedForbidden() =
    stubUpdateVerifiedEmailRequest(forbiddenResponse, FORBIDDEN)

  private def stubUpdateVerifiedEmailRequest(response: String, status: Int): Unit =
    stubFor(
      put(urlMatching(expectedUrl))
        .willReturn(
          aResponse()
            .withBody(response)
            .withStatus(status)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

  def stubEmailUpdatedResponseWithStatus(response: String, status: Int): Unit =
    stubFor(
      put(urlMatching(expectedUrl))
        .willReturn(
          aResponse()
            .withBody(response)
            .withStatus(status)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

  def verifyUpdateVerifiedEmailIsCalled(times: Int): Unit =
    verify(times, putRequestedFor(updateVerifiedEmailContextPath))
}
