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

/*
 * Copyright 2018 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.matching.{RequestPatternBuilder, UrlPattern}
import play.mvc.Http.HeaderNames.CONTENT_TYPE
import play.mvc.Http.MimeTypes.JSON
import play.mvc.Http.Status.OK
import ExternalServicesConfig.etmpFormBundleId

object SubscriptionService {

  val SubscribePath: UrlPattern = urlMatching("/subscribe")

  def stubSubscribeEndpoint() {
    stubFor(
      post(SubscribePath).willReturn(
        aResponse()
          .withStatus(OK)
          .withBody(s"""
               |{
               |  "subscriptionCreateResponse": {
               |    "responseCommon": {
               |      "status": "OK",
               |      "processingDate": "2016-08-18T14:01:05Z",
               |      "returnParameters": [
               |        {
               |          "paramName": "ETMPFORMBUNDLENUMBER",
               |          "paramValue": "$etmpFormBundleId"
               |        },
               |        {
               |          "paramName": "POSITION",
               |          "paramValue": "GENERATE"
               |        }
               |      ]
               |    },
               |    "responseDetail": {
               |      "EORINo": "${ExternalServicesConfig.subscriptionEoriNumber}"
               |    }
               |  }
               |}
            """.stripMargin)
          .withHeader(CONTENT_TYPE, JSON)
      )
    )
  }

  def stubSubscribeLinkEndpoint() {
    stubFor(
      post(SubscribePath).willReturn(
        aResponse()
          .withStatus(OK)
          .withBody(s"""
               |{
               |  "subscriptionCreateResponse": {
               |    "responseCommon": {
               |      "status": "OK",
               |      "processingDate": "2016-08-18T14:01:05Z",
               |      "returnParameters": [
               |        {
               |          "paramName": "ETMPFORMBUNDLENUMBER",
               |          "paramValue": "$etmpFormBundleId"
               |        },
               |        {
               |          "paramName": "POSITION",
               |          "paramValue": "LINK"
               |        }
               |      ]
               |    },
               |    "responseDetail": {
               |      "EORINo": "${ExternalServicesConfig.subscriptionEoriNumber}"
               |    }
               |  }
               |}
            """.stripMargin)
          .withHeader(CONTENT_TYPE, JSON)
      )
    )
  }

  def stubSubscribePendingEndpoint() {
    stubFor(
      post(SubscribePath).willReturn(
        aResponse()
          .withStatus(OK)
          .withBody(s"""
              |{
              |  "subscriptionCreateResponse": {
              |    "responseCommon": {
              |      "status": "OK",
              |      "processingDate": "2016-08-18T14:01:05Z",
              |      "returnParameters": [
              |        {
              |          "paramName": "ETMPFORMBUNDLENUMBER",
              |          "paramValue": "$etmpFormBundleId"
              |        },
              |        {
              |          "paramName": "POSITION",
              |          "paramValue": "WORKLIST"
              |        }
              |      ]
              |    }
              |  }
              |}
            """.stripMargin)
          .withHeader(CONTENT_TYPE, JSON)
      )
    )
  }

  def stubSubscribeRejectedEndpoint() {
    stubFor(
      post(SubscribePath).willReturn(
        aResponse()
          .withStatus(OK)
          .withBody(s"""
              |{
              |  "subscriptionCreateResponse": {
              |    "responseCommon": {
              |      "status": "OK",
              |      "processingDate": "2016-08-18T14:01:05Z",
              |      "returnParameters": [
              |        {
              |          "paramName": "POSITION",
              |          "paramValue": "FAIL"
              |        }
              |      ]
              |    }
              |  }
              |}
            """.stripMargin)
          .withHeader(CONTENT_TYPE, JSON)
      )
    )
  }

  def stubSubscribe068FailEndpoint() {
    stubFor(
      post(SubscribePath).willReturn(
        aResponse()
          .withStatus(OK)
          .withBody(s"""
               |{
               |  "subscriptionCreateResponse": {
               |    "responseCommon": {
               |      "status": "OK",
               |      "statusText": "068 - Subscription already in-progress or active",
               |      "processingDate": "2019-03-08T10:35:23Z",
               |      "returnParameters": [
               |        {
               |          "paramName": "POSITION",
               |          "paramValue": "FAIL"
               |        }
               |      ]
               |    }
               |  }
               |}
            """.stripMargin)
          .withHeader(CONTENT_TYPE, JSON)
      )
    )
  }

  def returnFailResponseForSubscriptionCreate003FailRejectedEndpoint() {
    stubFor(
      post(SubscribePath).willReturn(
        aResponse()
          .withStatus(OK)
          .withBody(s"""
               | {
               |  "subscriptionCreateResponse": {
               |    "responseCommon": {
               |      "status": "OK",
               |      "statusText": "003 - Request could not be processed",
               |      "processingDate": "2019-03-08T10:35:23Z",
               |      "returnParameters": [
               |        {
               |          "paramName": "POSITION",
               |          "paramValue": "FAIL"
               |        }
               |      ]
               |    }
               |   }
               |  }
            """.stripMargin)
          .withHeader(CONTENT_TYPE, JSON)
      )
    )
  }

  def returnResponseWhenReceiveRequest(url: String, request: String, response: String): Unit =
    returnResponseWhenReceiveRequest(url, request, response, OK)

  def returnResponseWhenReceiveRequest(url: String, request: String, response: String, status: Int): Unit =
    stubFor(
      post(urlMatching(url))
        .withRequestBody(equalToJson(request))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(response)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

  def getRequestMadeToSubscriptionService: String =
    WireMock.findAll(new RequestPatternBuilder(RequestMethod.ANY, SubscribePath)).get(0).getBodyAsString
}
