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

package uk.gov.hmrc.customs.rosmfrontend.domain.messaging

import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json._
import uk.gov.hmrc.customs.rosmfrontend.domain.{AddressValidator, CaseClassAuditHelper}

case class Header(originatingSystem: String, requestTimeStamp: String, correlationId: String)

object Header {
  implicit val jsonFormat = Json.format[Header]
}

abstract case class Address(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  addressLine4: Option[String],
  postalCode: Option[String],
  countryCode: String
) extends CaseClassAuditHelper with AddressValidator {
  override def toMap(caseClassObject: AnyRef = this, ignoredFields: List[String] = List.empty): Map[String, String] =
    Map(
      "addressLine1" -> addressLine1,
      "addressLine2" -> addressLine2.getOrElse(""),
      "addressLine3" -> addressLine3.getOrElse(""),
      "addressLine4" -> addressLine4.getOrElse(""),
      "postalCode" -> postalCode.getOrElse(""),
      "countryCode" -> countryCode
    )
}

object Address {
  implicit val jsonFormat = Json.format[Address]

  def apply(
    addressLine1: String,
    addressLine2: Option[String],
    addressLine3: Option[String],
    addressLine4: Option[String],
    postalCode: Option[String],
    countryCode: String
  ): Address =
    new Address(
      addressLine1,
      addressLine2,
      addressLine3,
      addressLine4,
      postalCode.filter(_.nonEmpty),
      countryCode.toUpperCase()
    ) {}
}

trait IndividualName {
  def firstName: String

  def middleName: Option[String]

  def lastName: String

  final def fullName: String = s"$firstName ${middleName.getOrElse("")} $lastName"
}

case class Individual(firstName: String, middleName: Option[String], lastName: String, dateOfBirth: String)
    extends IndividualName with CaseClassAuditHelper

object Individual {

  def noMiddle(firstName: String, lastName: String, dateOfBirth: String): Individual =
    Individual(firstName, middleName = None, lastName, dateOfBirth)

  def withLocalDate(firstName: String, lastName: String, dateOfBirth: LocalDate): Individual =
    withLocalDate(firstName, middleName = None, lastName, dateOfBirth)

  def withLocalDate(
    firstName: String,
    middleName: Option[String],
    lastName: String,
    dateOfBirth: LocalDate
  ): Individual =
    Individual(firstName, middleName, lastName, dateOfBirth.toString)

  implicit val formats = Json.format[Individual]
}

trait CommonHeader {

  private def dateTimeWritesIsoUtc: Writes[DateTime] = new Writes[DateTime] {
    def writes(d: org.joda.time.DateTime): JsValue =
      JsString(d.toString(ISODateTimeFormat.dateTimeNoMillis().withZoneUTC()))
  }

  private def dateTimeReadsIso: Reads[DateTime] = new Reads[DateTime] {
    def reads(value: JsValue): JsResult[DateTime] =
      try {
        JsSuccess(ISODateTimeFormat.dateTimeParser.parseDateTime(value.as[String]))
      } catch {
        case e: Exception => JsError(s"Could not parse '${value.toString()}' as an ISO date. Reason: $e")
      }
  }

  implicit val dateTimeReads = dateTimeReadsIso
  implicit val dateTimeWrites = dateTimeWritesIsoUtc
}

case class MessagingServiceParam(paramName: String, paramValue: String) extends CaseClassAuditHelper {
  private val param1CamelCase =
    if (paramName.contains("_")) paramName.toLowerCase().replace("sap_number", "sapNumber")
    else paramName

  val ignoredFields = List("keyValueParams", "param1CamelCase")
  val keyValueParams: Map[String, String] = Map(param1CamelCase -> paramValue)

  def keyValueMap(): Map[String, String] = toMap(this, ignoredFields = ignoredFields)
}

object MessagingServiceParam {
  implicit val formats = Json.format[MessagingServiceParam]

  val positionParamName = "POSITION"
  val Generate = "GENERATE"
  val Link = "LINK"
  val Pending = "WORKLIST"
  val Fail = "FAIL"

  val formBundleIdParamName = "ETMPFORMBUNDLENUMBER"
}

case class RequestParameter(paramName: String, paramValue: String) extends CaseClassAuditHelper

object RequestParameter {
  implicit val formats = Json.format[RequestParameter]
}

case class RequestCommon(
  regime: String,
  receiptDate: DateTime,
  acknowledgementReference: String,
  originatingSystem: Option[String] = None,
  requestParameters: Option[Seq[RequestParameter]] = None
) extends CaseClassAuditHelper {
  val ignoredFields = List("requestParameters")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val rp = requestParameters.fold(Map.empty[String, String])(_.flatMap(_.toMap()).toMap)
    m ++ rp
  }
}

object RequestCommon extends CommonHeader {
  implicit val requestParamFormat = Json.format[RequestParameter]
  implicit val formats = Json.format[RequestCommon]
}

case class ResponseCommon(
  status: String,
  statusText: Option[String] = None,
  processingDate: DateTime,
  returnParameters: Option[List[MessagingServiceParam]] = None
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

object ResponseCommon extends CommonHeader {
  val StatusOK = "OK"
  val StatusNotOK = "NOT_OK"
  implicit val formats = Json.format[ResponseCommon]
}
