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

package uk.gov.hmrc.customs.rosmfrontend.forms.subscription

import org.joda.time.LocalDate
import play.api.data.Forms._
import play.api.data.validation._
import play.api.data.{Form, Forms, Mapping}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.CompanyShortNameViewModel
import uk.gov.hmrc.customs.rosmfrontend.forms.FormUtils._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription._
import uk.gov.hmrc.customs.rosmfrontend.playext.form.ConditionalMapping
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.voa.play.form.ConditionalMappings.{isEqual, isNotEqual, mandatoryIf}
import uk.gov.voa.play.form.MandatoryOptionalMapping

import scala.util.matching.Regex

object SubscriptionForm {

  private val validConfirmIndividualTypes = Set(CdsOrganisationType.SoleTraderId, CdsOrganisationType.IndividualId)

  val eoriRegex: Regex = "(?i)GB(\\s*\\d\\s*){11,15}$".r
  val sicCodeRegex: Regex = "^(\\s*\\d\\s*){4,5}$".r

  private val confirmIndividualTypeError = "cds.confirm-individual-type.error.individual-type"
  val confirmIndividualTypeForm: Form[CdsOrganisationType] = Form(
    "individual-type" -> mandatoryString(confirmIndividualTypeError)(oneOf(validConfirmIndividualTypes))
      .transform[CdsOrganisationType](CdsOrganisationType.forId, _.id)
  )
  private val Length2 = 2

  private val trueAnswer = "true"
  private val falseAnswer = "false"
  private val trueOrFalse: String => Boolean = oneOf(Set(trueAnswer, falseAnswer))

  def trueFalseFieldMapping(fieldName: String): (String, Mapping[Boolean]) =
    fieldName -> optional(text.verifying(messageKeyOptionInvalid, trueOrFalse))
      .verifying(messageKeyOptionInvalid, _.isDefined)
      .transform[Boolean](s => s.get == trueAnswer, b => if (b) Some(trueAnswer) else None)

  val subscriptionVatUKDetailsForm: Form[SubscriptionVatUKDetailsFormModel] = {

    val hasGbVatsFieldName = "vat-gb-id"
    val gbVatsNonEmpty = Constraint(
      (vatNumbers: List[String]) =>
        if (vatNumbers.isEmpty) Invalid("cds.subscription.vat-uk.incomplete.entry") else Valid
    )
    val gbVatsMapping: (String, Mapping[Option[List[VatIdentification]]]) =
      "vat-gb-number" -> mandatoryIf(
        isEqual(hasGbVatsFieldName, trueAnswer),
        mapping = list(
          text(maxLength = 15)
            .verifying("cds.subscription.vat-uk.required.error", _.nonEmpty)
        ).verifying(gbVatsNonEmpty)
      ).transform[Option[List[VatIdentification]]](
        SubscriptionVatUKDetailsFormModel.convertRequestForGbVatsToModel,
        SubscriptionVatUKDetailsFormModel.convertModelForGbVatsToRequest
      )

    Form(
      mapping(trueFalseFieldMapping(hasGbVatsFieldName), gbVatsMapping)(SubscriptionVatUKDetailsFormModel.apply)(
        SubscriptionVatUKDetailsFormModel.unapply
      )
    )
  }

  val subscriptionVatEUDetailsForm: Form[SubscriptionVatEUDetailsFormModel] = {

    val hasEuVatsFieldName = "vat-eu-id"
    val euVatsMapping: (String, Mapping[Option[List[VatIdentification]]]) =

      "eu-vats" -> mandatoryIf(
        isEqual(hasEuVatsFieldName, trueAnswer),
        mapping(
          "vat-eu-country" -> list(
            text(maxLength = 2).verifying("cds.subscription.eu-vats.vat-eu-country.required.error", _.nonEmpty)
          ),
          "vat-eu-number" -> list(text.verifying(validEUVATNumber))
        )(
          (vatCountryCodes: List[String], vatNumbers: List[String]) =>
            SubscriptionVatEUDetailsFormModel.stringListsToVats(vatCountryCodes, vatNumbers)
        )(SubscriptionVatEUDetailsFormModel.vatsToStringLists)
          .verifying("cds.subscription.vat-eu.incomplete.entry", { vatIds =>
            vatIds.flatMap {
              case VatIdentification(Some(_), None) => Some(())
              case VatIdentification(None, Some(_)) => Some(())
              case _                                => None
            }.isEmpty
          })
      )

    Form(
      mapping(trueFalseFieldMapping(hasEuVatsFieldName), euVatsMapping)(SubscriptionVatEUDetailsFormModel.apply)(
        SubscriptionVatEUDetailsFormModel.unapply
      )
    )
  }

  val subscriptionDateOfEstablishmentForm: Form[LocalDate] = Form(
    "date-of-establishment" -> mandatoryDateTodayOrBefore(
      onEmptyError = "cds.subscription.date-of-establishment.error.required.date-of-establishment",
      onInvalidDateError = "cds.subscription.date-of-establishment.error.invalid.date-of-establishment",
      onDateInFutureError = "cds.subscription.date-of-establishment.error.in-future.date-of-establishment"
    )
  )

  val subscriptionCompanyShortNameForm: Form[CompanyShortNameViewModel] =
    Form(mapping("short-name" ->
      MandatoryOptionalMapping(text.verifying(validShortName)))(CompanyShortNameViewModel.apply)(CompanyShortNameViewModel.unapply))

  val subscriptionCompanyShortNameYesNoForm: Form[YesNo] =
    Form(
      mapping(
        "use-short-name" -> optional(boolean)
          .verifying("cds.subscription.short-name.error.use-short-name", x => x.fold(false)(oneOf(Set(true, false)))
        ).transform[Boolean](str => str.get, bool => Option(bool))
      )(YesNo.apply)(YesNo.unapply)
    )

  val subscriptionPartnershipShortNameForm: Form[CompanyShortNameViewModel] =
    Form(mapping("short-name" ->
      MandatoryOptionalMapping(text.verifying(validPartnershipShortName)))(CompanyShortNameViewModel.apply)(CompanyShortNameViewModel.unapply))

  val subscriptionPartnershipShortNameYesNoForm: Form[YesNo] =
    Form(
      mapping(
        "use-short-name" -> optional(boolean).verifying(
          "cds.subscription.partnership.short-name.error.use-short-name",
          x => x.fold(false)(oneOf(Set(true, false)))
        ).transform[Boolean](str => str.get, bool => Option(bool))
      )(YesNo.apply)(YesNo.unapply)
    )

  val sicCodeform = Form(
    Forms.mapping("sic" -> text.verifying(validSicCode))(SicCodeViewModel.apply)(SicCodeViewModel.unapply)
  )

  private def validShortName: Constraint[String] =
    Constraint({
      case s if s.trim == ""       => Invalid(ValidationError("cds.subscription.short-name.error.short-name"))
      case s if s.trim.length > 70 => Invalid(ValidationError("cds.subscription.short-name.error.short-name.too-long"))
      case _                       => Valid
    })

  private def validPartnershipShortName: Constraint[String] =
    Constraint({
      case s if s.trim == ""       => Invalid(ValidationError("cds.subscription.partnership.short-name.error.short-name"))
      case s if s.trim.length > 70 => Invalid(ValidationError("cds.subscription.short-name.error.short-name.too-long"))
      case _                       => Valid
    })

  private def validSicCode: Constraint[String] =
    Constraint("constraints.sic")({
      case s if s.trim.isEmpty       => Invalid(ValidationError("cds.subscription.sic.error.empty"))
      case s if !s.matches(sicCodeRegex.regex) => Invalid(ValidationError("cds.subscription.sic.error.wrong-format"))
      case _                         => Valid
    })

  private def validEUVATNumber: Constraint[String] =
    Constraint("constraints.eu.vat")({
      case v if v.trim.isEmpty => Invalid(ValidationError("cds.subscription.eu-vats.vat-eu-number.required.error"))
      case v if v.length > 15  => Invalid(ValidationError("cds.subscription.eu-vats.vat-eu-number.too-long"))
      case v if !v.matches("^[a-zA-Z0-9]*$") =>
        Invalid(ValidationError("cds.subscription.eu-vats.vat-eu-number.invalid"))
      case _ => Valid
    })

  def validEori: Constraint[String] =
    Constraint({
      case e if e.trim.isEmpty                 => Invalid(ValidationError("cds.matching-error.eori.isEmpty"))
       case e if !e.matches(eoriRegex.regex) => Invalid(ValidationError("cds.matching-error.eori"))
      case _                                   => Valid
    })

  def validEmail: Constraint[String] =
    Constraint({
      case e if e.trim.isEmpty => Invalid(ValidationError("cds.subscription.contact-details.form-error.email"))
      case e if e.length > 50  => Invalid(ValidationError("cds.subscription.contact-details.form-error.email.too-long"))
      case e if !EmailAddress.isValid(e) =>
        Invalid(ValidationError("cds.subscription.contact-details.form-error.email.wrong-format"))
      case _ => Valid
    })

  def validFullName: Constraint[String] =
    Constraint({
      case s if s.trim.isEmpty => Invalid(ValidationError("cds.subscription.contact-details.form-error.full-name"))
      case s if s.length > 70  => Invalid(ValidationError("cds.subscription.full-name.error.too-long"))
      case _                   => Valid
    })

  val validMultipleChoice = optional(text)
    .verifying(messageKeyOptionInvalid, _.fold(false)(oneOf(Set("true", "false"))))
    .transform[Boolean](str => str.contains("true"), bool => if (bool) Some("true") else Some("false"))

  def validMultipleChoiceWithCustomError(errorMessage: String = messageKeyOptionInvalid) =
    optional(text)
      .verifying(errorMessage, _.fold(false)(oneOf(Set("true", "false"))))
      .transform[Boolean](str => str.contains("true"), bool => if (bool) Some("true") else Some("false"))

  val eoriNumberForm = Form(
    Forms.mapping("eori-number" -> text.verifying(validEori))(EoriNumberViewModel.apply)(EoriNumberViewModel.unapply)
  )

  val euVatForm = Form(
    Forms.mapping("vatCountry" -> validateEuCountry, "vatNumber" -> validateEuVatNumber)(VatEUDetailsModel.apply)(
      VatEUDetailsModel.unapply
    )
  )

  private def validateVatNumberIfFieldIsNotBlank(field: String) =
    mandatoryIf(isNotEqual(field, ""), text.verifying(validEUVATNumber))

  private def validateVatCountryIfFieldIsNotBlank(field: String) =
    mandatoryIf(
      isNotEqual(field, ""),
      text.verifying("cds.subscription.contact-details.form-error.country", _.length == Length2)
    )

  private def validateEuVatNumber =
    text.verifying(validEUVATNumber)

  private def validateEuCountry =
    text.verifying("cds.subscription.eu-vat-details.form-error.country", _.length == Length2)

  val emailForm = Form(
    Forms.mapping("email" -> text.verifying(validEmail))(EmailViewModel.apply)(EmailViewModel.unapply)
  )
}
