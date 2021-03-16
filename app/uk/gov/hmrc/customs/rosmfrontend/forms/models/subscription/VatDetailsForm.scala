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

package uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription

import org.joda.time.LocalDate
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.customs.rosmfrontend.domain.VatIdentification
import uk.gov.hmrc.customs.rosmfrontend.forms.FormUtils._
import uk.gov.hmrc.customs.rosmfrontend.forms.FormValidation._

case class VatDetails(postcode: String, number: String, effectiveDate: LocalDate) {
  def getVatId = VatIdentification(Some("GB"), Some(number))
  def normalizeVATNumber = number.replaceAll("[GB| |]", "")
}

object VatDetails {
  implicit val format: Format[VatDetails] = Json.format[VatDetails]
}

object VatDetailsForm {

  def validPostcode: Constraint[String] =
    Constraint({
      case s if s.matches(postcodeRegex.regex) => Valid
      case _                                   => Invalid(ValidationError("cds.subscription.vat-details.postcode.required.error"))
    })

  def validVatNumber: Constraint[String] =
    Constraint({
      case s if s.trim.isEmpty => Invalid(ValidationError("cds.subscription.vat-uk.required.error"))
      case s if !s.matches("(?i)GB(\\s*\\d\\s*){9}$|^(\\s*\\d\\s*){9}$") =>
        Invalid(ValidationError("cds.subscription.vat-uk.length.error"))
      case _ => Valid
    })

  val vatDetailsForm = Form(
    mapping(
      "postcode" -> text.verifying(validPostcode),
      "vat-number" -> text.verifying(validVatNumber),
      "vat-effective-date" -> mandatoryDateTodayOrBefore(
        onEmptyError = "cds.subscription.vat-details.vat-effective-date.required.error"
      )
    )(VatDetails.apply)(VatDetails.unapply)
  )
}
