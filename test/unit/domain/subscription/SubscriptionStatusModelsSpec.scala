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

package unit.domain.subscription

import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.customs.rosmfrontend.domain._
import util.UnitSpec

class SubscriptionStatusModelsSpec extends UnitSpec {

  private val receiptDate = (new DateTime).withDate(2016, 1, 8).withTime(9, 0, 0, 0)

  private val regime = "CDS"
  private val idType = "SAFE"
  private val id = "XE0000000086619"

  private val status = "OK"
  private val subscriptionStatus = "00"
  private val idValue = Some("IDVALUE")

  private val expectedJsonWithoutIdValue = Json.parse("""{
      |  "request": {
      |    "receiptDate": "2016-01-08T09:00:00.000Z",
      |    "regime": "CDS",
      |    "idType": "SAFE",
      |    "id": "XE0000000086619"
      |  },
      |  "response": {
      |    "status": "OK",
      |    "processingDate": "2016-01-08T09:00:00.000Z",
      |    "subscriptionStatus": "00"
      |  }
      |}""".stripMargin)

  private val expectedJsonWithIdValue = Json.parse("""{
      |  "request": {
      |    "receiptDate": "2016-01-08T09:00:00.000Z",
      |    "regime": "CDS",
      |    "idType": "SAFE",
      |    "id": "XE0000000086619"
      |  },
      |  "response": {
      |    "status": "OK",
      |    "processingDate": "2016-01-08T09:00:00.000Z",
      |    "subscriptionStatus": "00",
      |    "idValue":"IDVALUE"
      |  }
      |}""".stripMargin)

  private val subscriptionStatusRequest = SubscriptionStatusQueryParams(receiptDate, regime, idType, id)
  private val subscriptionResponseWithoutIdValue = SubscriptionStatusResponseHolder(
    SubscriptionStatusResponse(
      SubscriptionStatusResponseCommon(status, receiptDate),
      SubscriptionStatusResponseDetail(subscriptionStatus, None)
    )
  )
  private val subscriptionResponseWithIdValue = SubscriptionStatusResponseHolder(
    SubscriptionStatusResponse(
      SubscriptionStatusResponseCommon(status, receiptDate),
      SubscriptionStatusResponseDetail(subscriptionStatus, idValue)
    )
  )

  "SubscriptionStatusModel" should {
    "return a valid json representation request response without idValue" in {
      Json.toJson(RequestResponse(subscriptionStatusRequest.jsObject(), subscriptionResponseWithoutIdValue.jsObject())) shouldBe expectedJsonWithoutIdValue
    }

    "return a valid json representation request response with idValue" in {
      Json.toJson(RequestResponse(subscriptionStatusRequest.jsObject(), subscriptionResponseWithIdValue.jsObject())) shouldBe expectedJsonWithIdValue
    }

  }
}
