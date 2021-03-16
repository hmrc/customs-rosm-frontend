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

package uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching

import play.api.libs.json._
import uk.gov.hmrc.customs.rosmfrontend.domain.CaseClassAuditHelper
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging._

case class Organisation(organisationName: String, organisationType: String) extends CaseClassAuditHelper

object Organisation {
  implicit val formats = Json.format[Organisation]
}

case class RequestDetail(
  IDType: String,
  IDNumber: String,
  requiresNameMatch: Boolean,
  isAnAgent: Boolean,
  organisation: Option[Organisation] = None,
  individual: Option[Individual] = None
) extends CaseClassAuditHelper {
  val ignoredFields = List("organisation", "individual")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val om = organisation.fold(Map.empty[String, String])(_.toMap())
    val im = individual.fold(Map.empty[String, String])(_.toMap())
    m ++ om ++ im
  }
}

object RequestDetail {
  implicit val formats = Json.format[RequestDetail]
}

case class MatchingRequest(requestCommon: RequestCommon, requestDetail: RequestDetail) {
  def keyValueMap(): Map[String, String] = {
    val rc = requestCommon.keyValueMap()
    val rm = requestDetail.keyValueMap()
    rc ++ rm
  }
}

object MatchingRequest {
  implicit val formats = Json.format[MatchingRequest]
}

case class MatchingRequestHolder(registerWithIDRequest: MatchingRequest) {
  def keyValueMap(): Map[String, String] =
    registerWithIDRequest.keyValueMap()
}

object MatchingRequestHolder {
  implicit val formats = Json.format[MatchingRequestHolder]
}

case class IndividualResponse(
  firstName: String,
  middleName: Option[String],
  lastName: String,
  dateOfBirth: Option[String]
) extends IndividualName with CaseClassAuditHelper

object IndividualResponse {
  implicit val formats = Json.format[IndividualResponse]
}

case class OrganisationResponse(
  organisationName: String,
  code: Option[String],
  isAGroup: Option[Boolean],
  organisationType: Option[String]
) extends CaseClassAuditHelper

object OrganisationResponse {
  implicit val formats = Json.format[OrganisationResponse]
}

case class ContactResponse(
  phoneNumber: Option[String] = None,
  mobileNumber: Option[String] = None,
  faxNumber: Option[String] = None,
  emailAddress: Option[String] = None
) extends CaseClassAuditHelper

object ContactResponse {
  implicit val jsonFormat = Json.format[ContactResponse]
}

case class ResponseDetail(
  SAFEID: String,
  ARN: Option[String] = None,
  isEditable: Boolean,
  isAnAgent: Boolean,
  isAnIndividual: Boolean,
  individual: Option[IndividualResponse] = None,
  organisation: Option[OrganisationResponse] = None,
  address: Address,
  contactDetails: ContactResponse
) extends CaseClassAuditHelper {
  val ignoredFields = List("organisation", "individual", "contactDetails", "address")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val om = prefixMapKey("organisation.", organisation.fold(Map.empty[String, String])(_.toMap()))
    val im = prefixMapKey("individual.", individual.fold(Map.empty[String, String])(_.toMap()))
    val cd = prefixMapKey("contactDetail.", contactDetails.toMap())
    val am = prefixMapKey("address.", address.toMap())

    m ++ om ++ im ++ cd ++ am
  }

  def jsObject(): JsValue = {
    val m = Json.toJson(toMap(this, ignoredFields = ignoredFields))
    val om = Json.toJson(organisation.fold(Map.empty[String, String])(_.toMap()))
    val im = Json.toJson(individual.fold(Map.empty[String, String])(_.toMap()))
    val cd = Json.toJson(Map("contactDetail" -> contactDetails.toMap()))
    val am = Json.toJson(Map("address" -> Json.toJson(address.toMap())))

    m.as[JsObject]
      .deepMerge(
        om.as[JsObject]
          .deepMerge(
            im.as[JsObject]
              .deepMerge(
                cd.as[JsObject]
                  .deepMerge(am.as[JsObject])
              )
          )
      )
      .as[JsValue]
  }
}

object ResponseDetail {
  implicit val formats = Json.format[ResponseDetail]
}

case class RegisterWithIDResponse(responseCommon: ResponseCommon, responseDetail: Option[ResponseDetail]) {
  def keyValueMap(): Map[String, String] = {
    val rc = responseCommon.keyValueMap()
    val rd = responseDetail.map(_.keyValueMap())
    rc ++ rd.fold(Map.empty[String, String])(m => m)
  }

  def jsObject(): JsValue =
    Json
      .toJson(responseCommon.keyValueMapNamedParams())
      .as[JsObject]
      .deepMerge(responseDetail.fold(Json.toJson(Map.empty[String, String]))(x => x.jsObject()).as[JsObject])
}

object RegisterWithIDResponse {
  implicit val formats = Json.format[RegisterWithIDResponse]
}

case class MatchingResponse(registerWithIDResponse: RegisterWithIDResponse) {
  def keyValueMap(): Map[String, Object] =
    registerWithIDResponse.keyValueMap()

  def jsObject(): JsValue =
    registerWithIDResponse.jsObject()
}

object MatchingResponse {
  implicit val formats = Json.format[MatchingResponse]
}
