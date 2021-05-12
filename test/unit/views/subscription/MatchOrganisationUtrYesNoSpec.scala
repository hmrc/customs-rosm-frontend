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

package unit.views.subscription

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.data.Form
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.domain.YesNo
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_organisation_utr_yes_no
import util.ViewSpec

class MatchOrganisationUtrYesNoSpec extends ViewSpec {
  val form: Form[YesNo] = yesNoAnswerForm
  val formWithNoSelectionError: Form[YesNo] = yesNoAnswerForm.bind(Map.empty[String, String])
  val formWithNoUtrEnteredError: Form[YesNo] = yesNoAnswerForm.bind(Map("utr" -> ""))
  val isInReviewMode = false
  val previousPageUrl = "/"
  val nonSoleTraderType = "charity-public-body-not-for-profit"
  val soleTraderType = "sole-trader"
  implicit val request = withFakeCSRF(FakeRequest())

  private val view = app.injector.instanceOf[match_organisation_utr_yes_no]

  "Match UTR Yes No page in the non sole trader case" should {
    "display correct title" in {
      doc.title must startWith("Does your organisation have a Corporation Tax Unique Taxpayer Reference (UTR) number?")
    }
    "have the correct h1 text" in {
      doc.body
        .getElementsByTag("h1")
        .first()
        .text() mustBe "Does your organisation have a Corporation Tax Unique Taxpayer Reference (UTR) number?"
    }
    "have the correct class on the h1" in {
      doc.body.getElementsByTag("h1") contains("<span class=\"heading-large\">What is your Corporation Tax Unique Taxpayer Reference?</span>")
    }
    "have an input of type 'radio' for Yes I have a UTR" in {
      doc.body.getElementById("have-utr-yes").attr("type") mustBe "radio"
    }
    "have an input of type 'radio' for No I don't have a UTR" in {
      doc.body.getElementById("have-utr-no").attr("type") mustBe "radio"
    }
    "display correct intro paragraph" in {
      doc.body
        .getElementsByClass("form-hint")
        .text() mustBe "Your organisation will have a Corporation Tax UTR number if you pay corporation tax. It is on tax returns and other letters from HMRC."
    }
  }

  "Match UTR Yes No page in the sole trader case" should {
    "have the correct h1 text" in {
      docAsSoleTraderIndividual.body
        .getElementsByTag("h1")
        .first()
        .text mustBe "Do you have a Self Assessment Unique Taxpayer Reference issued in the UK?"
    }
    "not show the link for corporation tax UTR number, for sole traders" in {
      docAsSoleTraderIndividual.body.getElementsByTag("summary").text mustBe ""
    }

    "not have any content for sole trader" in {
      docAsSoleTraderIndividual.body.getElementById("details-content-1") mustBe null
    }
  }

  "Match UTR Yes No page without selecting any radio button in the non sole trader case" should {
    "display a page level error message" in {
      docWithNoSelectionError.body
        .getElementsByClass("error-summary-list")
        .text mustBe "Please select one of the options"
    }
    "display the correct problem message at the top of the page" in {
      docWithNoSelectionError.body
        .getElementById("errors")
        .text mustBe "There is a problem. Please select one of the options"
    }
  }

  "Match Organisation UTR page without selecting any radio button in the sole trader case" should {
    "display a page level error message" in {
      docWithNoSelectionErrorAsSoleTrader.body
        .getElementsByClass("error-summary-list")
        .text mustBe "Please select one of the options"
    }
    "display the correct problem message at the top of the page" in {
      docWithNoSelectionErrorAsSoleTrader.body
        .getElementById("errors")
        .text mustBe "There is a problem. Please select one of the options"
    }
  }

  lazy val doc: Document = getDoc(form)

  private def getDoc(form: Form[YesNo]) = {
    val result = view(form, nonSoleTraderType, "", Journey.GetYourEORI)
    val doc = Jsoup.parse(contentAsString(result))
    doc
  }

  lazy val docWithNoSelectionError: Document = {
    val result = view(formWithNoSelectionError, nonSoleTraderType, "", Journey.GetYourEORI)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithNoUtrEnteredError: Document = {
    val result = view(formWithNoUtrEnteredError, nonSoleTraderType, "", Journey.GetYourEORI)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docAsSoleTraderIndividual: Document = {
    val result = view(form, soleTraderType, "", Journey.GetYourEORI)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithNoSelectionErrorAsSoleTrader: Document = {
    val result = view(formWithNoSelectionError, soleTraderType, "", Journey.GetYourEORI)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithNoUtrEnteredErrorAsSoleTrader: Document = {
    val result = view(formWithNoUtrEnteredError, soleTraderType, "", Journey.GetYourEORI)
    Jsoup.parse(contentAsString(result))
  }

}
