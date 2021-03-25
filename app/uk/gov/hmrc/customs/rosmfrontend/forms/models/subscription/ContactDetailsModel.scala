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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.ContactDetails
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionTimeOutException

case class ContactDetailsModel(
    fullName: String,
    emailAddress: String,
    telephone: String,
    fax: Option[String],
    useAddressFromRegistrationDetails: Option[Boolean] = None,
    street: Option[String],
    city: Option[String],
    postcode: Option[String],
    countryCode: Option[String]
) {
  def contactDetails: ContactDetails = ContactDetails(
    fullName,
    emailAddress,
    telephone,
    fax,
    street.getOrElse(""),
    city.getOrElse(""),
    postcode,
    countryCode.getOrElse("")
  )

}

object ContactDetailsModel {
  implicit val jsonFormat: OFormat[ContactDetailsModel] =
    Json.format[ContactDetailsModel]
  def apply(fullName: String,
            emailAddress: String,
            telephone: String,
            fax: Option[String]): ContactDetailsModel = {

    new ContactDetailsModel(fullName,
                            emailAddress,
                            telephone,
                            fax,
                            None,
                            None,
                            None,
                            None,
                            None)
  }

  def apply(
      contactDetailsModel: ContactDetailsModel,
      contactPersonViewModel: ContactPersonViewModel): ContactDetailsModel = {
    contactDetailsModel.copy(
      fullName = contactPersonViewModel.fullName,
      emailAddress = contactPersonViewModel.emailAddress.getOrElse(
        throw new IllegalArgumentException("Email is required")),
      telephone = contactPersonViewModel.telephone,
      fax = contactPersonViewModel.fax
    )
  }

}

case class  ContactDetailsViewModel(
    fullName: String,
    emailAddress: Option[String],
    telephone: String,
    fax: Option[String],
    useAddressFromRegistrationDetails: Boolean = true,
    street: Option[String],
    city: Option[String],
    postcode: Option[String],
    countryCode: Option[String]
)

object ContactDetailsViewModel {
  implicit val jsonFormat: OFormat[ContactDetailsViewModel] =
    Json.format[ContactDetailsViewModel]
}

case class ContactPersonViewModel(fullName: String,
                                  emailAddress: Option[String],
                                  telephone: String,
                                  fax: Option[String])

object ContactPersonViewModel {

  implicit val jsonFormat: OFormat[ContactPersonViewModel] =
    Json.format[ContactPersonViewModel]

  def toContactDetailsModel(
      model: ContactPersonViewModel): ContactDetailsModel = ContactDetailsModel(
    model.fullName,
    model.emailAddress.getOrElse(
      throw SessionTimeOutException("Email is required")),
    model.telephone,
    model.fax
  )

  def fromContactDetailsModel(
      contactDetails: ContactDetailsModel): ContactPersonViewModel = {
    ContactPersonViewModel(
      fullName = contactDetails.fullName,
      emailAddress = Option(contactDetails.emailAddress),
      telephone = contactDetails.telephone,
      fax = contactDetails.fax
    )
  }
}
