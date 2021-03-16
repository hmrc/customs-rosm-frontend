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

import play.api.libs.json.Json
import uk.gov.hmrc.customs.rosmfrontend.domain.CaseClassAuditHelper
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching.{
  ContactResponse,
  IndividualResponse,
  OrganisationResponse
}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.{Address, MessagingServiceParam, NonUKIdentification}

case class ResponseDetail(
  SAFEID: String,
  ARN: Option[String] = None,
  nonUKIdentification: Option[NonUKIdentification] = None,
  isEditable: Boolean,
  isAnAgent: Boolean,
  isAnIndividual: Boolean,
  individual: Option[IndividualResponse] = None,
  organisation: Option[OrganisationResponse] = None,
  address: Address,
  contactDetails: ContactResponse
) extends CaseClassAuditHelper {
  require(
    isAnIndividual && individual.isDefined && organisation.isEmpty || !isAnIndividual && individual.isEmpty && organisation.isDefined
  )
}

object ResponseDetail {
  implicit val jsonFormat = Json.format[ResponseDetail]
}

case class ResponseCommon(
  status: String,
  statusText: Option[String],
  processingDate: String,
  returnParameters: Option[List[MessagingServiceParam]] = None,
  taxPayerID: Option[String]
) extends CaseClassAuditHelper {
  val ignoredFields = List("returnParameters")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val rp = returnParameters.fold(Map.empty[String, String])(_.flatMap(_.toMap()).toMap)
    m ++ rp
  }

  def keyValueMapNamedParams(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val rp = returnParameters.fold(Map.empty[String, String])(_.flatMap(_.keyValueParams).toMap)
    m ++ rp
  }
}

object ResponseCommon {
  implicit val format = Json.format[ResponseCommon]
}

case class RegistrationDisplayResponse(responseCommon: ResponseCommon, responseDetail: Option[ResponseDetail]) {
  def keyValueMap(): Map[String, String] = {
    val rc = responseCommon.keyValueMap()
    val rs = responseDetail.fold(Map.empty[String, String])(_.toMap())
    rc ++ rs
  }
}

object RegistrationDisplayResponse {
  implicit val format = Json.format[RegistrationDisplayResponse]
}

case class RegistrationDisplayResponseHolder(registrationDisplayResponse: RegistrationDisplayResponse)

object RegistrationDisplayResponseHolder {
  implicit val format = Json.format[RegistrationDisplayResponseHolder]
}
