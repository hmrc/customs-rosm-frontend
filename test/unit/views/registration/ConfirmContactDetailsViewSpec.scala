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

package unit.views.registration

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.data.Form
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.registration.YesNoWrongAddress
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.confirm_contact_details
import util.ViewSpec

class ConfirmContactDetailsViewSpec extends ViewSpec {

  private val name = "Org Name"
  private val address = AddressViewModel("street", "city", Some("SW1A 2BQ"), "GB")
  private val customsIdUtr = Some(Utr("1111111111"))
  private val customsIdNino = Some(Nino("ZZ123456Z"))
  private val corporateBody = Some(CorporateBody)
  private val partnership = Some(Partnership)
  private val form: Form[YesNoWrongAddress] = YesNoWrongAddress.createForm()
  private implicit val request = withFakeCSRF(FakeRequest())

  private val view = app.injector.instanceOf[confirm_contact_details]

  "Confirm Contact Details" should {
    "display correct title" in {
      CorporateBodyDoc.title() must startWith("These are the details we have about your organisation")
      SoleTraderOrIndividualWithNinoDoc.title() must startWith("These are the details we have about you")
      PartnershipBodyDoc.title() must startWith("These are the details we have about your partnership")
    }
    "have the correct h1 text" in {
      CorporateBodyDoc
        .body()
        .getElementsByTag("h1")
        .text() mustBe "These are the details we have about your organisation"
      SoleTraderOrIndividualWithNinoDoc
        .body()
        .getElementsByTag("h1")
        .text() mustBe "These are the details we have about you"
      PartnershipBodyDoc
        .body()
        .getElementsByTag("h1")
        .text() mustBe "These are the details we have about your partnership"
    }
    "have the correct class on the h1" in {
      CorporateBodyDoc.body().getElementsByTag("h1").hasClass("heading-large") mustBe true
    }
    "have the right labels in the definition list" in {
      CorporateBodyDoc.body().getElementById("idNumber").text() mustBe "Corporation Tax UTR number"
      CorporateBodyDoc.body().getElementById("name").text() mustBe "Registered company name"
      CorporateBodyDoc.body().getElementById("address").text() mustBe "Registered address"

      SoleTraderOrIndividualWithNinoDoc.body().getElementById("idNumber").text() mustBe "National Insurance number"
      SoleTraderOrIndividualWithUtrDoc.body().getElementById("idNumber").text() mustBe "Self Assessment UTR number"
      SoleTraderOrIndividualWithNinoDoc.body().getElementById("name").text() mustBe "Name"
      SoleTraderOrIndividualWithNinoDoc.body().getElementById("address").text() mustBe "Address"

      PartnershipBodyDoc.body().getElementById("idNumber").text() mustBe "Partnership Self Assessment UTR number"
      PartnershipBodyDoc.body().getElementById("name").text() mustBe "Registered partnership name"
      PartnershipBodyDoc.body().getElementById("address").text() mustBe "Registered address"

    }
    "have the right legend" in {
      CorporateBodyDoc
        .body()
        .getElementsByTag("legend")
        .text() mustBe "Are these the organisation details you want to use to get an EORI number?"
      SoleTraderOrIndividualWithNinoDoc
        .body()
        .getElementsByTag("legend")
        .text() mustBe "Are these the details you want to use to get an EORI number?"
      PartnershipBodyDoc
        .body()
        .getElementsByTag("legend")
        .text() mustBe "Are these the partnership details you want to use to get an EORI number?"
    }
    "have an input of type 'radio' for Yes option" in {
      CorporateBodyDoc.body().getElementById("yes-no-wrong-address-yes").attr("type") mustBe "radio"
    }
    "have the right text on the Yes option" in {
      CorporateBodyDoc
        .body()
        .getElementsByAttributeValue("for", "yes-no-wrong-address-yes")
        .text() mustBe "Yes, these are the details I want to use"
      SoleTraderOrIndividualWithNinoDoc
        .body()
        .getElementsByAttributeValue("for", "yes-no-wrong-address-yes")
        .text() mustBe "Yes, these are the details I want to use"
      PartnershipBodyDoc
        .body()
        .getElementsByAttributeValue("for", "yes-no-wrong-address-yes")
        .text() mustBe "Yes, these are the details I want to use"
    }
    "have an input of type 'radio' for Wrong Address option" in {
      CorporateBodyDoc.body().getElementById("yes-no-wrong-address-wrong-address").attr("type") mustBe "radio"
    }
    "have the right text on the Wrong Address option" in {
      CorporateBodyDoc
        .body()
        .getElementsByAttributeValue("for", "yes-no-wrong-address-wrong-address")
        .text() mustBe "No, I need to change my organisation address"
      SoleTraderOrIndividualWithNinoDoc
        .body()
        .getElementsByAttributeValue("for", "yes-no-wrong-address-wrong-address")
        .text() mustBe "No, I need to change my organisation address"
      PartnershipBodyDoc
        .body()
        .getElementsByAttributeValue("for", "yes-no-wrong-address-wrong-address")
        .text() mustBe "No, I need to change my partnership address"
    }
    "have an input of type 'radio' for No option" in {
      CorporateBodyDoc.body().getElementById("yes-no-wrong-address-no").attr("type") mustBe "radio"
    }
    "have the right text on the No option" in {
      CorporateBodyDoc
        .body()
        .getElementsByAttributeValue("for", "yes-no-wrong-address-no")
        .text() mustBe "No, I need to enter all my organisation details again"
      SoleTraderOrIndividualWithNinoDoc
        .body()
        .getElementsByAttributeValue("for", "yes-no-wrong-address-no")
        .text() mustBe "No, I need to enter all my organisation details again"
      PartnershipBodyDoc
        .body()
        .getElementsByAttributeValue("for", "yes-no-wrong-address-no")
        .text() mustBe "No, I need to enter all my partnership details again"
    }
  }

  private lazy val CorporateBodyDoc: Document = {
    val result = view(name, address, customsIdUtr, corporateBody, form, Journey.GetYourEORI)
    Jsoup.parse(contentAsString(result))
  }

  private lazy val SoleTraderOrIndividualWithNinoDoc: Document = {
    val result = view(name, address, customsIdNino, None, form, Journey.GetYourEORI)
    Jsoup.parse(contentAsString(result))
  }

  private lazy val SoleTraderOrIndividualWithUtrDoc: Document = {
    val result = view(name, address, customsIdUtr, None, form, Journey.GetYourEORI)
    Jsoup.parse(contentAsString(result))
  }

  private lazy val PartnershipBodyDoc: Document = {
    val result = view(name, address, customsIdUtr, partnership, form, Journey.GetYourEORI)
    Jsoup.parse(contentAsString(result))
  }
}
