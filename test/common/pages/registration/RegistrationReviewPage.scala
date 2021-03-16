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

package common.pages.registration

import common.pages.WebPage
import common.support.Env

class RegistrationReviewPage extends WebPage {

  val BusinessNameXPath = "//dd[@id='review-tbl__business-name']"
  val BusinessNameReviewLinkXPath = "//a[@id='review-tbl__business-name_change']"

  val BusinessNameAndAddressLabelXPath = "//th[@id='review-tbl__name-and-address_heading']"

  val UKVatIdentificationNumberXpath = "//dd[@id='review-tbl__gb-vat_number']"
  val UKVatIdentificationNumberXpathLabel = "//td[@id='review-tbl__gb-vat_number_heading']"

  val UKVatIdentificationPostcodeXpath = "//dd[@id='review-tbl__gb-vat_postcode']"

  val UKVatIdentificationDateXpath = "//dd[@id='review-tbl__gb-vat_date']"

  val UKVatIdentificationNumbersReviewLinkXpath = "//a[@id='review-tbl__gb-vat_change']"

  val EUVatDetailsXpath = "//dd[@id='review-tbl__eu-vat']"

  val EUVatIdentificationNumbersReviewLinkXpath = "//a[@id='review-tbl__eu-vat_change']"

  val UkVatDetailsChangeLinkId = "review-tbl__gb-vat_change"

  val ContactDetailsXPath = "//dd[@id='review-tbl__contact']"

  val ContactDetailsReviewLinkXPath = "//a[@id='review-tbl__contact_change']"

  val CorrespondenceAddressXpath = "//td[@id='review-tbl__correspondence']"

  val ThirdCountryIdNumbersXPath = "//td[@id='review-tbl__third-country-id']"

  val ThirdCountryIdNumbersReviewLinkXPath = "//a[@id='review-tbl__third-country-id_change']"

  val EUDisclosureConsentXPath = "//tr[@id='review-tbl__disclosure']/td[1]"

  val ShortNameXPath = "//dd[@id='review-tbl__short-name']"

  val ShortNameReviewLinkXPath = "//a[@id='review-tbl__short-name_change']"

  val DateOfEstablishmentXPath = "//div[@id='review-tbl__doe']/dd[1]"

  val DateOfEstablishmentLabelXPath = "//div[@id='review-tbl__doe']/dt"

  val IndividualDateOfBirthXPath = "//dd[@id='review-tbl__date-of-birth']"
  val IndividualDateOfBirthReviewLinkXPath = "//a[@id='review-tbl__date-of-birth_change']"

  val IndividualDateOfBirthLabelXPath = "//dd[@id='review-tbl__date-of-birth_heading']"

  val PrincipalEconomicActivityXPath = "//dd[@id='review-tbl__activity']"

  val AddressXPath = "//dd[@id='review-tbl__address']"
  val AddressReviewLinkXPath = "//a[@id='review-tbl__address_change']"

  val SixLineAddressXPath = "//dd[@id='review-tbl__six_line_address']"
  val SixLineAddressXPathLabel = "//td[@id='review-tbl__six_line_address_heading']"
  val SixLineAddressReviewLinkXPath = "//a[@id='review-tbl__six_line_address_change']"

  val AddressHeadingXPath = "//[@id='review-tbl__address_heading']"

  val BusinessNameLabelXPath = "//[@id='review-tbl__business-name_heading']"

  val UtrLabelXPath = "//[@id='review-tbl__utr_heading']"

  val FullNameXPath = "//dd[@id='review-tbl__full-name']"
  val FullNameReviewLinkXPath = "//a[@id='review-tbl__full-name_change']"

  def changeAnswerText(heading: String): String = s"Change $heading"

  override val url: String = Env.frontendHost + "/customs/register-for-cds/review-details"

  override val title = "Check your answers"
}

object RegistrationReviewPage extends RegistrationReviewPage
