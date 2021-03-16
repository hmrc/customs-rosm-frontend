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

package uk.gov.hmrc.customs.rosmfrontend.domain

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.CommonHeader

case class SubscriptionStatusQueryParams(receiptDate: DateTime, regime: String, idType: String, id: String)
    extends CaseClassAuditHelper {
  def queryParams: Seq[(String, String)] = {
    val receiptDateAsString = receiptDate.toString(ISODateTimeFormat.dateTimeNoMillis().withZoneUTC())
    Seq("receiptDate" -> receiptDateAsString, "regime" -> regime, idType -> id)
  }

  def keyValueMap(): Map[String, String] =
    toMap(this)

  def jsObject(): JsValue =
    Json.toJson(this.keyValueMap())
}

case class SubscriptionStatusResponseCommon(status: String, processingDate: DateTime) extends CaseClassAuditHelper {
  def keyValueMap(): Map[String, String] =
    toMap(this)

  def jsObject(): JsValue =
    Json.toJson(this.keyValueMap())
}

object SubscriptionStatusResponseCommon extends CommonHeader {
  implicit val jsonFormat = Json.format[SubscriptionStatusResponseCommon]
}

case class SubscriptionStatusResponseDetail(subscriptionStatus: String, idValue: Option[String])
    extends CaseClassAuditHelper {
  def keyValueMap(): Map[String, String] =
    toMap(this)

  def jsObject(): JsValue =
    Json.toJson(this.keyValueMap())
}

object SubscriptionStatusResponseDetail {
  implicit val jsonFormat = Json.format[SubscriptionStatusResponseDetail]
}

case class SubscriptionStatusResponse(
  responseCommon: SubscriptionStatusResponseCommon,
  responseDetail: SubscriptionStatusResponseDetail
) {
  def jsObject(): JsValue =
    responseCommon.jsObject().as[JsObject].deepMerge(responseDetail.jsObject().as[JsObject])
}

object SubscriptionStatusResponse {
  implicit val jsonFormat = Json.format[SubscriptionStatusResponse]
}

case class SubscriptionStatusResponseHolder(subscriptionStatusResponse: SubscriptionStatusResponse) {
  def jsObject(): JsValue =
    subscriptionStatusResponse.jsObject()
}

object SubscriptionStatusResponseHolder {
  implicit val jsonFormat = Json.format[SubscriptionStatusResponseHolder]
}

case class RequestResponse(request: JsValue, response: JsValue)

object RequestResponse {
  implicit val jsonFormat = Json.format[RequestResponse]
}
