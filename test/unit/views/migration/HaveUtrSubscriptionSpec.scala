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

package unit.views.migration

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.data.Form
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.domain.{CdsOrganisationType, UtrMatchModel}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.utrForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.match_utr_subscription
import util.ViewSpec

class HaveUtrSubscriptionSpec extends ViewSpec {

  implicit val request = withFakeCSRF(FakeRequest())

  private val invalidUtr = "0123456789"
  private val standardForm: Form[UtrMatchModel] = utrForm
  private val noOptionSelectedForm = utrForm.bind(Map.empty[String, String])
  private val incorrectUtrForm = utrForm.bind(Map("have-utr" -> "true", "utr" -> invalidUtr))

  private val view = app.injector.instanceOf[match_utr_subscription]

  "Fresh Subscription Have Utr Page for Company" should {
    "display correct heading" in {
      companyDoc.body
        .getElementsByTag("h1")
        .text mustBe "Does your organisation have a Unique Taxpayer Reference (UTR) number issued in the UK?"
    }

    "display correct title" in {
      companyDoc.title must startWith(
        "Does your organisation have a Unique Taxpayer Reference (UTR) number issued in the UK?"
      )
    }

    "have correct intro" in {
      companyDoc.body
        .getElementById("intro")
        .text mustBe "You will have a UTR number if your organisation pays corporation tax in the UK."
    }
  }

  "Fresh Subscription Have Utr Page for Individual" should {
    "display correct heading" in {
      individualDoc.body
        .getElementsByTag("h1")
        .text mustBe "Do you have a Self Assessment Unique Taxpayer Reference (UTR) number issued in the UK?"
    }

    "display correct title" in {
      individualDoc.title must startWith(
        "Do you have a Self Assessment Unique Taxpayer Reference (UTR) number issued in the UK?"
      )
    }
    "have correct intro" in {
      individualDoc.body
        .getElementById("intro")
        .text mustBe "You will have a self assessment UTR number if you registered for Self Assessment in the UK."
    }
  }

  "Subscription Have Utr Page" should {
    "radio button yes with correct label" in {
      companyDoc.body.getElementById("have-utr-yes").attr("value") mustBe "true"
      companyDoc.body.getElementsByAttributeValue("for", "have-utr-yes").text must include("Yes")
    }

    "radio button no with correct label" in {
      companyDoc.body.getElementById("have-utr-no").attr("value") mustBe "false"
      companyDoc.body.getElementsByAttributeValue("for", "have-utr-no").text must include("No")
    }

    "text input with correct label" in {
      companyDoc.body.getElementById("utr").attr("type") mustBe "text"
      companyDoc.body.getElementsByAttributeValue("for", "utr").text must include("Corporation Tax UTR number")
    }
  }

  "Form with no option selected" should {
    "display proper message page level" in {
      notSelectedIndividualDoc.body.getElementById("form-error-heading").text mustBe "There is a problem."
      notSelectedCompanyDoc.body.getElementById("form-error-heading").text mustBe "There is a problem."

      notSelectedIndividualDoc.body
        .getElementsByAttributeValue("href", "#have-utr")
        .text mustBe "Tell us if you have a UTR number"
      notSelectedCompanyDoc.body
        .getElementsByAttributeValue("href", "#have-utr")
        .text mustBe "Tell us if you have a UTR number"
    }
  }

  "Form with incorrect UTR format" should {
    "display item level error message" in {
      incorrectUtrDoc.body.getElementsByClass("error-message").text mustBe "Enter a valid Unique Taxpayer Reference number"
    }
  }

  lazy val companyDoc: Document =
    Jsoup.parse(contentAsString(view(standardForm, CdsOrganisationType.CompanyId, Journey.Migrate)))
  lazy val notSelectedCompanyDoc: Document =
    Jsoup.parse(contentAsString(view(noOptionSelectedForm, CdsOrganisationType.CompanyId, Journey.Migrate)))
  lazy val individualDoc: Document =
    Jsoup.parse(contentAsString(view(standardForm, CdsOrganisationType.SoleTraderId, Journey.Migrate)))
  lazy val notSelectedIndividualDoc: Document =
    Jsoup.parse(contentAsString(view(noOptionSelectedForm, CdsOrganisationType.SoleTraderId, Journey.Migrate)))
  lazy val incorrectUtrDoc: Document =
    Jsoup.parse(contentAsString(view(incorrectUtrForm, CdsOrganisationType.SoleTraderId, Journey.Migrate)))
}
