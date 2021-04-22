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

package uk.gov.hmrc.customs.rosmfrontend.forms

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.i18n.Messages
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Address
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.{Nino, Utr, _}
import uk.gov.hmrc.customs.rosmfrontend.forms.FormUtils.{mandatoryDateTodayOrBefore, _}
import uk.gov.hmrc.customs.rosmfrontend.forms.FormValidation._
import uk.gov.hmrc.domain.{Nino => nino}
import uk.gov.voa.play.form.ConditionalMappings._

object MatchingForms {

  val Length35 = 35
  val Length34 = 34
  private val Length2 = 2
  private val nameRegex = "[a-zA-Z0-9-' ]*"
  private def validUtrFormat(utr: Option[String]): Boolean = {

    val ZERO = 0
    val ONE = 1
    val TWO = 2
    val THREE = 3
    val FOUR = 4
    val FIVE = 5
    val SIX = 6
    val SEVEN = 7
    val EIGHT = 8
    val NINE = 9
    val TEN = 10

    def isValidUtr(remainder: Int, checkDigit: Int): Boolean = {
      val mapOfRemainders = Map(
        ZERO -> TWO,
        ONE -> ONE,
        TWO -> NINE,
        THREE -> EIGHT,
        FOUR -> SEVEN,
        FIVE -> SIX,
        SIX -> FIVE,
        SEVEN -> FOUR,
        EIGHT -> THREE,
        NINE -> TWO,
        TEN -> ONE
      )
      mapOfRemainders.get(remainder).contains(checkDigit)
    }

    utr match {
      case Some(u) =>
        val utrWithoutK = u.trim.stripSuffix("K").stripSuffix("k")
        utrWithoutK.length == TEN && utrWithoutK.forall(_.isDigit) && {
          val actualUtr = utrWithoutK.toList
          val checkDigit = actualUtr.head.asDigit
          val restOfUtr = actualUtr.tail
          val weights = List(SIX, SEVEN, EIGHT, NINE, TEN, FIVE, FOUR, THREE, TWO)
          val weightedUtr = for ((w1, u1) <- weights zip restOfUtr) yield {
            w1 * u1.asDigit
          }
          val total = weightedUtr.sum
          val remainder = total % 11
          isValidUtr(remainder, checkDigit)
        }
      case None => false
    }
  }

  val journeyTypeForm: Form[JourneyTypeDetails] = Form(
    mapping("journeytype" -> text.verifying("cds.error.option.invalid", oneOf(Set("subscribe-for-cds", "register-for-cds"))))(
      JourneyTypeDetails.apply
    )(JourneyTypeDetails.unapply)
  )

  val organisationTypeDetailsForm: Form[CdsOrganisationType] = Form(
    "organisation-type" -> optional(text)
      .verifying(
        "cds.matching.organisation-type.page-error.organisation-type-field.error.required",
        x => {
          x.fold(false)(oneOf(CdsOrganisationType.validOrganisationTypes.keySet).apply(_))
        }
      )
      .transform[CdsOrganisationType](
        o =>
          CdsOrganisationType(
            CdsOrganisationType
              .forId(
                o.getOrElse(throw new IllegalArgumentException("Could not create CdsOrganisationType for empty ID."))
              )
              .id
        ),
        x => Some(x.id)
      )
  )

  val userLocationForm: Form[UserLocationDetails] = Form(
    "location" -> optional(text)
      .verifying("cds.registration.user-location.error.location", x => {
        x.fold(false)(oneOf(UserLocation.validLocations).apply(_))
      })
      .transform[UserLocationDetails](o => UserLocationDetails(o), x => x.location)
  )

  private val validYesNoAnswerOptions = Set("true", "false")

  private def minLengthOrEmpty(length: Int, minErrorMsgKey: String = "error.minLength"): Constraint[String] =
    Constraint { s =>
      require(length >= 0, "string minLength must not be negative")
      if (s.trim.isEmpty || s.trim.length >= length) Valid else Invalid(ValidationError(minErrorMsgKey, length))
    }

  def yesNoAnswerForm(implicit messages: Messages): Form[YesNo] = createYesNoAnswerForm()

  def yesNoUtrAnswerForm(invalidErrorMsgKey: String, formId: String = "have-utr")(implicit messages: Messages): Form[YesNo] = createYesNoAnswerForm(invalidErrorMsgKey, formId)

  def disclosePersonalDetailsYesNoAnswerForm()(implicit messages: Messages): Form[YesNo] =
    createYesNoAnswerForm("cds.subscription.organisation-disclose-personal-details-consent.error.yes-no-answer")

  def isleOfManYesNoAnswerForm()(implicit messages: Messages): Form[YesNo] =
    createYesNoAnswerForm("cds.registration.isle-of-man.error.yes-no-answer")

  def vatRegisteredUkYesNoAnswerForm(isPartnership: Boolean = false)(implicit messages: Messages): Form[YesNo] =
    if (isPartnership) createYesNoAnswerForm("cds.registration.vat-registered-uk.partnership.error.yes-no-answer")
    else createYesNoAnswerForm("cds.registration.vat-registered-uk.error.yes-no-answer")

  def vatRegisteredEuYesNoAnswerForm(isPartnership: Boolean = false)(implicit messages: Messages): Form[YesNo] =
    if (isPartnership) createYesNoAnswerForm("cds.subscription.vat-registered-eu.partnership.page-error.yes-no-answer")
    else createYesNoAnswerForm("cds.subscription.vat-registered-eu.page-error.yes-no-answer")

  def vatGroupYesNoAnswerForm()(implicit messages: Messages): Form[YesNo] =
    createYesNoAnswerForm("cds.subscription.vat-group.page-error.yes-no-answer")

  def euVatLimitNotReachedYesNoAnswerForm()(implicit messages: Messages): Form[YesNo] =
    createOptionalVatYesNoAnswerForm("cds.subscription.vat-details-eu-confirm.select-one-error.yes-no-answer", "false")

  def euVatLimitReachedYesNoAnswerForm()(implicit messages: Messages): Form[YesNo] =
    createOptionalVatYesNoAnswerForm("cds.subscription.vat-details-eu-confirm.select-one-error.yes-no-answer", "true")

  def removeVatYesNoAnswer()(implicit messages: Messages): Form[YesNo] =
    createYesNoAnswerForm("cds.subscription.vat-details-eu.page-error.yes-no-answer")

  def useThisEoriYesNoAnswer()(implicit messages: Messages): Form[YesNo] =
    createYesNoAnswerForm("cds.subscription.this.eori.yes-no-answer")

  def  isThisRightContactAddressYesNoAnswer()(implicit messages: Messages): Form[YesNo] =
    createYesNoAnswerForm("cds.subscription.is-this-contact-address.yes-no-answer")

  def  confirmIdentityYesNoAnswer()(implicit messages: Messages): Form[YesNo] =
    createYesNoAnswerForm("cds.subscription.nino.utr.invalid")

  private def createYesNoAnswerForm(invalidErrorMsgKey: String = messageKeyOptionInvalid, formId: String = "yes-no-answer")(implicit messages: Messages): Form[YesNo] = Form(
    mapping(
      formId -> optional(text.verifying(messages(invalidErrorMsgKey), oneOf(validYesNoAnswerOptions)))
        .verifying(messages(invalidErrorMsgKey), _.isDefined)
        .transform[Boolean](str => str.get.toBoolean, bool => Option(String.valueOf(bool)))
    )(YesNo.apply)(YesNo.unapply)
  )

  private def createOptionalVatYesNoAnswerForm(
    invalidErrorMsgKey: String = messageKeyOptionInvalid,
    vatLimitReached: String = "true"
  )(implicit messages: Messages): Form[YesNo] = Form(
    mapping(
      "yes-no-answer" -> optional(text)
        .verifying(messages(invalidErrorMsgKey), x => x.fold(vatLimitReached.toBoolean)(oneOf(validYesNoAnswerOptions)))
        .transform[Boolean](str => str.getOrElse(vatLimitReached).toBoolean, bool => Option(String.valueOf(bool)))
    )(YesNo.apply)(YesNo.unapply)
  )

  private def validBusinessName: Constraint[String] =
    Constraint({
      case s if s.isEmpty      => Invalid(ValidationError("cds.matching-error.business-details.business-name.isEmpty"))
      case s if s.length > 105 => Invalid(ValidationError("cds.matching-error.business-details.business-name.too-long"))
      case _                   => Valid
    })

  private def validPartnershipName: Constraint[String] =
    Constraint({
      case s if s.isEmpty => Invalid(ValidationError("cds.matching-error.business-details.partnership-name.isEmpty"))
      case s if s.length > 105 =>
        Invalid(ValidationError("cds.matching-error.business-details.partnership-name.too-long"))
      case _ => Valid
    })

  private def validCompanyName: Constraint[String] =
    Constraint({
      case s if s.isEmpty      => Invalid(ValidationError("cds.matching-error.business-details.company-name.isEmpty"))
      case s if s.length > 105 => Invalid(ValidationError("cds.matching-error.business-details.company-name.too-long"))
      case _                   => Valid
    })

  private def validOrganisationName: Constraint[String] =
    Constraint({
      case s if s.isEmpty      => Invalid(ValidationError("cds.matching.organisation-name.error.name"))
      case s if s.length > 105 => Invalid(ValidationError("cds.matching-error.business-details.business-name.too-long"))
      case _                   => Valid
    })

  private def validUtr: Constraint[String] = {

    def validLength: String => Boolean = s => s.length == 10 || (s.endsWith("k") || s.endsWith("K") && s.length == 11)

    Constraint({
      case s if s.isEmpty                => Invalid(ValidationError("cds.matching-error.business-details.utr.isEmpty"))
      case s if !validLength(s)          => Invalid(ValidationError("cds.matching-error.utr.length"))
      case s if !validUtrFormat(Some(s)) => Invalid(ValidationError("cds.matching-error.utr.invalid"))
      case _                             => Valid
    })
  }

  val nameUtrOrganisationForm: Form[NameIdOrganisationMatchModel] = Form(
    mapping("name" -> text.verifying(validBusinessName), "utr" -> text.verifying(validUtr))(
      NameIdOrganisationMatchModel.apply
    )(NameIdOrganisationMatchModel.unapply)
  )

  val nameUtrPartnershipForm: Form[NameIdOrganisationMatchModel] = Form(
    mapping("name" -> text.verifying(validPartnershipName), "utr" -> text.verifying(validUtr))(
      NameIdOrganisationMatchModel.apply
    )(NameIdOrganisationMatchModel.unapply)
  )

  val nameUtrCompanyForm: Form[NameIdOrganisationMatchModel] = Form(
    mapping("name" -> text.verifying(validCompanyName), "utr" -> text.verifying(validUtr))(
      NameIdOrganisationMatchModel.apply
    )(NameIdOrganisationMatchModel.unapply)
  )

  val nameOrganisationForm: Form[NameOrganisationMatchModel] = Form(
    mapping("name" -> text.verifying(validBusinessName))(NameOrganisationMatchModel.apply)(
      NameOrganisationMatchModel.unapply
    )
  )

  val ninoForm: Form[NinoMatch] = Form(
    mapping(
      "first-name" -> text.verifying(validFirstName),
      "last-name" -> text.verifying(validLastName),
      "date-of-birth" -> mandatoryDateTodayOrBefore(
        onEmptyError = "cds.registration-model.form-error.date-of-birth.empty",
        onInvalidDateError = "cds.registration-model.form-error.date-of-birth"
      ),
      "nino" -> text.verifying(validNino)
    )(NinoMatch.apply)(NinoMatch.unapply)
  )

  val enterNameDobForm: Form[NameDobMatchModel] = Form(
    mapping(
      "first-name" -> text.verifying(validFirstName),
      "middle-name" -> optional(text.verifying(validMiddleName)),
      "last-name" -> text.verifying(validLastName),
      "date-of-birth" -> mandatoryDateTodayOrBefore(
        onEmptyError = "cds.registration-model.form-error.date-of-birth.empty",
        onInvalidDateError = "cds.registration-model.form-error.date-of-birth"
      )
    )(NameDobMatchModel.apply)(NameDobMatchModel.unapply)
  )

  private def validFirstName: Constraint[String] =
    Constraint("constraints.first-name")({
      case s if s.isEmpty => Invalid(ValidationError("cds.subscription.first-name.error.empty"))
      case s if !s.matches(nameRegex) =>
        Invalid(ValidationError("cds.subscription.first-name.error.wrong-format"))
      case s if s.length > 35 => Invalid(ValidationError("cds.subscription.first-name.error.too-long"))
      case _                  => Valid
    })

  private def validGivenName: Constraint[String] =
    Constraint("constraints.first-name")({
      case s if s.isEmpty => Invalid(ValidationError("cds.subscription.given-name.error.empty"))
      case s if !s.matches(nameRegex) =>
        Invalid(ValidationError("cds.subscription.given-name.error.wrong-format"))
      case s if s.length > 35 => Invalid(ValidationError("cds.subscription.given-name.error.too-long"))
      case _                  => Valid
    })

  private def validMiddleName: Constraint[String] =
    Constraint("constraints.first-name")({
      case s if !s.matches(nameRegex) =>
        Invalid(ValidationError("cds.subscription.middle-name.error.wrong-format"))
      case s if s.length > 35 => Invalid(ValidationError("cds.subscription.middle-name.error.too-long"))
      case _                  => Valid
    })

  private def validLastName: Constraint[String] =
    Constraint("constraints.last-name")({
      case s if s.isEmpty => Invalid(ValidationError("cds.subscription.last-name.error.empty"))
      case s if !s.matches(nameRegex) =>
        Invalid(ValidationError("cds.subscription.last-name.error.wrong-format"))
      case s if s.length > 35 => Invalid(ValidationError("cds.subscription.last-name.error.too-long"))
      case _                  => Valid
    })

  private def validFamilyName: Constraint[String] =
    Constraint("constraints.last-name")({
      case s if s.isEmpty => Invalid(ValidationError("cds.subscription.family-name.error.empty"))
      case s if !s.matches(nameRegex) =>
        Invalid(ValidationError("cds.subscription.family-name.error.wrong-format"))
      case s if s.length > 35 => Invalid(ValidationError("cds.subscription.family-name.error.too-long"))
      case _                  => Valid
    })

  private def validNino: Constraint[String] =
    Constraint({
      case s if s.isEmpty                    => Invalid(ValidationError("cds.subscription.nino.error.empty"))
      case s if s.length != 9                => Invalid(ValidationError("cds.subscription.nino.error.wrong-length"))
      case s if !s.matches("[a-zA-Z0-9]*")   => Invalid(ValidationError("cds.matching.nino.invalid"))
      case s if !nino.isValid(s.toUpperCase) => Invalid(ValidationError("cds.matching.nino.invalid"))
      case _                                 => Valid
    })

  val subscriptionNinoForm: Form[IdMatchModel] = Form(
    mapping("nino" -> text.verifying(validNino))(IdMatchModel.apply)(IdMatchModel.unapply)
  )

  val ninoIdentityForm: Form[Nino] = Form(
    mapping(
      "id" -> text.verifying(validNino)
    )(Nino.apply)(Nino.unapply)
  )

  val utrIdentityForm: Form[Utr] = Form(
    mapping(
      "id" -> text.verifying(validUtr)
    )(Utr.apply)(Utr.unapply)
  )

  val ninoOrUtrForm: Form[NinoOrUtr] = Form(
    mapping(
      "nino" -> mandatoryIfEqual("ninoOrUtrRadio", "nino", text.verifying(validNino)),
      "utr" -> mandatoryIfEqual("ninoOrUtrRadio", "utr", text.verifying(validUtr)),
      "ninoOrUtrRadio" -> optional(text)
        .verifying("cds.subscription.nino.utr.invalid", _.fold(false)(x => x.trim.nonEmpty))
    )(NinoOrUtr.apply)(NinoOrUtr.unapply)
  )


  private val countryCodeGB = "GB"
  private val countryCodeGG = "GG"
  private val countryCodeJE = "JE"

  private val rejectGB: Constraint[String] = Constraint {
    case `countryCodeGB` => Invalid("cds.matching-error.country.unacceptable")
    case _               => Valid
  }

  private val acceptOnlyIslands: Constraint[String] = Constraint {
    case `countryCodeGG` | `countryCodeJE` => Valid
    case _                                 => Invalid("cds.matching-error.country.unacceptable")
  }

  val thirdCountrySixLineAddressForm: Form[SixLineAddressMatchModel] = sixLineAddressFormFactory(rejectGB)

  val islandsSixLineAddressForm: Form[SixLineAddressMatchModel] = sixLineAddressFormFactory(acceptOnlyIslands)

  private def sixLineAddressFormFactory(countryConstraints: Constraint[String]*): Form[SixLineAddressMatchModel] =
    Form(
      mapping(
        "line-1" -> text.verifying(validLine1),
        "line-2" -> optional(text.verifying(validLine2)),
        "line-3" -> text.verifying(validLine3),
        "line-4" -> optional(text.verifying(validLine4)),
        "postcode" -> postcodeMapping,
        "countryCode" -> mandatoryString("cds.matching-error.country.invalid")(s => s.length == Length2)
          .verifying(countryConstraints: _*)
      )(SixLineAddressMatchModel.apply)(SixLineAddressMatchModel.unapply)
    )

  def validLine1: Constraint[String] =
    Constraint({
      case s if s.trim.isEmpty => Invalid(ValidationError("cds.matching.organisation-address.line-1.error.empty"))
      case s if s.trim.length > 35 =>
        Invalid(ValidationError("cds.matching.organisation-address.line-1.error.too-long"))
      case _ => Valid
    })

  def validLine2: Constraint[String] =
    Constraint({
      case s if s.trim.length > 34 =>
        Invalid(ValidationError("cds.matching.organisation-address.line-2.error.too-long"))
      case _ => Valid
    })

  def validLine3: Constraint[String] =
    Constraint({
      case s if s.trim.isEmpty => Invalid(ValidationError("cds.matching.organisation-address.line-3.error.empty"))
      case s if s.trim.length > 34 =>
        Invalid(ValidationError("cds.matching.organisation-address.line-3.error.too-long"))
      case _ => Valid
    })

  def validLine4: Constraint[String] =
    Constraint({
      case s if s.trim.length > 35 =>
        Invalid(ValidationError("cds.matching.organisation-address.line-4.error.too-long"))
      case _ => Valid
    })

  def createSixLineAddress(value: Address): SixLineAddressMatchModel =
    SixLineAddressMatchModel(
      value.addressLine1,
      value.addressLine2,
      value.addressLine3.getOrElse(""),
      value.addressLine4,
      value.postalCode,
      value.countryCode
    )

  val thirdCountryIndividualNameDateOfBirthForm: Form[IndividualNameAndDateOfBirth] = {
    Form(
      mapping(
        "given-name" -> text.verifying(validGivenName),
        "middle-name" -> optional(text.verifying(validMiddleName)),
        "family-name" -> text.verifying(validFamilyName),
        "date-of-birth" -> mandatoryDateTodayOrBefore(
          onEmptyError = "cds.registration-model.form-error.date-of-birth.empty",
          onInvalidDateError = "cds.registration-model.form-error.date-of-birth"
        )
      )(IndividualNameAndDateOfBirth.apply)(IndividualNameAndDateOfBirth.unapply)
    )
  }

  val organisationNameForm: Form[NameMatchModel] = Form(
    mapping("name" -> text.verifying(validOrganisationName))(NameMatchModel.apply)(NameMatchModel.unapply)
  )

  def validHaveUtr: Constraint[Option[Boolean]] =
    Constraint({
      case None => Invalid(ValidationError("cds.matching.organisation-utr.field-error.have-utr"))
      case _    => Valid
    })

  val haveUtrForm: Form[HaveUtrMatchModel] = Form(
    mapping(
      "have-utr" -> optional(boolean).verifying(validHaveUtr),
      "utr" -> mandatoryIfTrue("have-utr", text.verifying(validUtr))
    )(HaveUtrMatchModel.apply)(HaveUtrMatchModel.unapply)
  )

  val utrForm: Form[UtrMatchModel] = Form(
    mapping(
      "utr" -> mandatory(text.verifying(validUtr))
    )(UtrMatchModel.apply)(UtrMatchModel.unapply)
  )

  def validHaveNino: Constraint[Option[Boolean]] =
    Constraint({
      case None => Invalid(ValidationError("cds.matching.nino.row.yes-no.error"))
      case _    => Valid
    })

  val rowIndividualsNinoForm: Form[NinoMatchModel] = Form(
    mapping(
      "have-nino" -> optional(boolean).verifying(validHaveNino),
      "nino" -> mandatoryIfTrue("have-nino", text.verifying(validNino))
    )(NinoMatchModel.apply)(NinoMatchModel.unapply)
  )
}
