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
import uk.gov.hmrc.customs.rosmfrontend.domain.{CdsOrganisationType, YesNo}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.yesNoCustomAnswerForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.match_utr_subscription_yes_no
import util.ViewSpec

class HaveUtrSubscriptionYesNoSpec extends ViewSpec {

  implicit val request = withFakeCSRF(FakeRequest())

  private val invalidUtr = "0123456789"
  private val standardForm: Form[YesNo] = yesNoCustomAnswerForm("Tell us if you have a Self Assessment Unique Taxpayer Reference (UTR) issued in the UK?", "have-utr")
  private val noOptionSelectedForm = yesNoCustomAnswerForm("Tell us if you have a Self Assessment Unique Taxpayer Reference (UTR) issued in the UK?", "have-utr").bind(Map.empty[String, String])
  private val incorrectUtrForm = yesNoCustomAnswerForm("Tell us if you have a Self Assessment Unique Taxpayer Reference (UTR) issued in the UK?", "have-utr").bind(Map("utr" -> invalidUtr))

  private val view = app.injector.instanceOf[match_utr_subscription_yes_no]

  "Fresh Subscription Have Utr Yes No Page for Company" should {
    "display correct heading" in {
      companyDoc.body
        .getElementsByTag("h1")
        .first()
        .text mustBe "Does your organisation have a Corporation Tax Unique Taxpayer Reference (UTR)?"
    }

    "display correct title" in {
      companyDoc.title must startWith(
        "Does your organisation have a Corporation Tax Unique Taxpayer Reference (UTR)?"
      )
    }
  }

  "Fresh Subscription Have Utr Page for Individual" should {
    "display correct heading" in {
      individualDoc.body
        .getElementsByTag("h1")
        .first()
        .text mustBe "Do you have a Self Assessment Unique Taxpayer Reference issued in the UK?"
    }

    "display correct title" in {
      individualDoc.title must startWith(
        "Do you have a Self Assessment Unique Taxpayer Reference issued in the UK?"
      )
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
