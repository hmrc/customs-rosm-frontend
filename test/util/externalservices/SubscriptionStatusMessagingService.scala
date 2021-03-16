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
import com.github.tomakehurst.wiremock.matching.UrlPattern
import org.scalacheck.Gen
import play.mvc.Http.HeaderNames.CONTENT_TYPE
import play.mvc.Http.MimeTypes.JSON
import play.mvc.Http.Status.OK

object SubscriptionStatusMessagingService {
  val StatusOk: String = "00"
  val UserSubscribeAlready: String = "11"
  val rejectedSubscriptionStatus: String = Gen.oneOf("05", "99").sample.get
  val processingSubscriptionStatus: String = Gen.oneOf("01", "11", "14").sample.get
  val subscriptionAlreadyExistsSubscriptionStatus: String = "04"

  private val SubscriptionStatusPath: UrlPattern = urlMatching("subscription-status")

  private def responseWithStatus(status: String = StatusOk) =
    s"""
       |{
       |  "subscriptionStatusResponse": {
       |    "responseCommon": {
       |      "status": "OK",
       |      "processingDate": "2016-03-17T09:30:47Z"
       |    },
       |    "responseDetail": {
       |      "subscriptionStatus": "$status",
       |      "idType": "EORI",
       |      "idValue": "1234567890"
       |    }
       |  }
       |}
      """.stripMargin

  def returnSubscriptionStatusForSapNumber(status: String, idType: String, id: String): Unit = {
    val urlPattern = s"/subscription-status\\?receiptDate\\=.*Z&regime=CDS&${idType}=$id"
    stubFor(
      get(urlMatching(urlPattern))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(responseWithStatus(status))
            .withHeader(CONTENT_TYPE, JSON)
        )
    )
  }

  def returnTheSubscriptionResponseWhenReceiveRequest(url: String, response: String): Unit =
    stubTheSubscriptionResponse(url, response, OK)

  def stubTheSubscriptionResponse(url: String, response: String, status: Int): Unit =
    stubFor(
      get(urlEqualTo(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(response)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

  def verifySubscriptionStatusIsNotCalled(): Unit =
    verify(0, getRequestedFor(SubscriptionStatusPath))

}
