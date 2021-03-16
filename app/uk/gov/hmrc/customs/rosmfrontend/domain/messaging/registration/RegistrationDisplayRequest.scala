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

package uk.gov.hmrc.customs.rosmfrontend.domain.messaging.registration

import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.customs.rosmfrontend.domain.CaseClassAuditHelper
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.{CommonHeader, RequestParameter}

case class RequestCommon(receiptDate: DateTime, requestParameters: Seq[RequestParameter]) extends CaseClassAuditHelper {
  val ignoredFields = List("receiptDate", "requestParameters")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val rp = requestParameters.flatMap(_.toMap())
    m ++ rp
  }
}

object RequestCommon extends CommonHeader {
  implicit val format = Json.format[RequestCommon]
}

case class RegistrationDisplayRequest(requestCommon: RequestCommon)

object RegistrationDisplayRequest {
  implicit val format = Json.format[RegistrationDisplayRequest]
}

case class RegistrationDisplayRequestHolder(registrationDisplayRequest: RegistrationDisplayRequest)

object RegistrationDisplayRequestHolder {
  implicit val format = Json.format[RegistrationDisplayRequestHolder]
}
