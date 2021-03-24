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

package common.pages.subscription

import common.pages.WebPage
import common.support.Env

trait SubscriptionContactDetailsPage extends WebPage {
  override val title = "Who can we contact?"
  val formId = "contactDetailsForm"

  val headingXPath = "//*[@id='contactDetailsForm']/fieldset/legend/h1"
  val introXPath = "//*[@id='contactDetailsForm']/fieldset/legend/p"

  val fullNameFieldXPath = "//*[@id='full-name']"
  val fullNameFieldLevelErrorXPath = "//*[@id='full-name-outer']//span[@class='error-message']"
  val fullNameLabelXPath = "//*[@id='full-name-outer']/label"

  val emailFieldXPath = "//*[@id='email']"
  val emailAddressFieldLabel = "Email address"

  val emailLabelXPath = "//*[@id='email-outer']/label"

  val telephoneFieldXPath = "//*[@id='telephone']"
  val telephoneFieldLevelErrorXPath = "//*[@id='telephone-outer']//span[@class='error-message']"
  val telephoneLabelXPath = "//*[@id='telephone-outer']/label"

  val faxFieldXPath = "//*[@id='fax']"
  val faxLabelXPath = "//*[@id='fax-outer']/label"
  val faxFieldLevelErrorXPath = "//*[@id='fax-outer']//span[@class='error-message']"

  val streetFieldXPath = "//*[@id='street']"
  val streetFieldLevelErrorXPath = "//*[@id='street-outer']//span[@class='error-message']"

  val cityFieldXPath = "//*[@id='city']"
  val cityFieldLevelErrorXPath = "//*[@id='city-outer']//span[@class='error-message']"

  val countryFieldLevelErrorXPath = "//*[@id='country-outer']//span[@class='error-message']"

  val countryCodeSelectedOptionXPath = "//*[@id='countryCode']/option[@selected]"

  val postcodeFieldXPath = "//*[@id='postcode']"
  val postcodeFieldLevelErrorXPath = "//*[@id='postcode-outer']//span[@class='error-message']"
  val postcodeFieldLabel = "Postcode"

  val registeredAddressQuestionXPath = "//*[@id='use-registered-address-fieldset']/legend/span[1]"
  val registeredAddressParaXPath = "//*[@id='registered-address']"
  val addressParaXPath = "//*[@id='address']"

  val useRegisteredAddressYesRadioButtonXPath = "//*[@id='use-registered-address-yes']"
  val useRegisteredAddressNoRadioButtonXPath = "//*[@id='use-registered-address-no']"

  override val url: String = Env.frontendHost + "/customs/subscribe-for-cds/contact-details"

  val continueButtonXpath = "//*[@class='button']"

  val hintTextTelephonXpath = "//*[@id='telephone-hint']"
  val hintTextFaxXpath = "//*[@id='fax-hint']"
  val stepsXpath = "//*[@id='steps-heading']"

}

object SubscriptionContactDetailsPage extends SubscriptionContactDetailsPage

