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
import uk.gov.hmrc.customs.rosmfrontend.domain.NinoMatchModel
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.rowIndividualsNinoForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.match_nino_subscription
import util.ViewSpec

class HaveNinoSubscriptionSpec extends ViewSpec {

  implicit val request = withFakeCSRF(FakeRequest())

  private val standardForm: Form[NinoMatchModel] = rowIndividualsNinoForm
  private val noOptionSelectedForm = rowIndividualsNinoForm.bind(Map.empty[String, String])
  private val incorrectNinoForm = rowIndividualsNinoForm.bind(Map("have-nino" -> "true", "nino" -> "012345789!@#$"))

  private val view = app.injector.instanceOf[match_nino_subscription]

  "Fresh Subscription Have Nino Page" should {
    "display correct heading" in {
      doc.body.getElementsByTag("h1").text must startWith("Do you have a National Insurance number issued in the UK?")
    }

    "display correct title" in {
      doc.title must startWith("Do you have a National Insurance number issued in the UK?")
    }

    "have 'yes' radio button" in {
      doc.body.getElementById("have-nino-yes").attr("value") mustBe "true"
    }

    "have 'no' radio button" in {
      doc.body.getElementById("have-nino-no").attr("value") mustBe "false"
    }

    "have description with proper content" in {
      doc.body
        .getElementById("description")
        .text mustBe "You will have a National Insurance number if you have worked in the UK."
    }

    "Have correct hint for nino field" in {
      doc.body.getElementById("nino-hint").text must include(
        "It's on your National Insurance card, benefit letter, payslip or P60."
      )
      doc.body.getElementById("nino-hint").text must include("For example, 'QQ123456C'")
    }

    "Have correct label for nino field" in {
      doc.body.getElementsByAttributeValue("for", "nino").text must include("National Insurance number")
    }
  }

  "No option selected Subscription Have Nino Page" should {
    "have page level error with correct message" in {
      docWithNoOptionSelected.body.getElementById("form-error-heading").text mustBe "There is a problem."
      docWithNoOptionSelected.body
        .getElementsByAttributeValue("href", "#have-nino")
        .text mustBe "Tell us if you have a National Insurance number"
    }
  }

  "Subscription Have Nino Page with incorrect Nino format" should {
    "have page level error with correct message" in {
      docWithIncorrectNino.body.getElementById("form-error-heading").text mustBe "There is a problem."
      docWithIncorrectNino.body
        .getElementsByAttributeValue("href", "#nino")
        .text mustBe "The National Insurance number must be 9 characters"
    }
    "inform field level that number must be 9 characters when input is too long" in {
      docWithIncorrectNino.body.getElementsByClass("error-message").text must include(
        "The National Insurance number must be 9 characters"
      )
    }
  }

  lazy val doc: Document = Jsoup.parse(contentAsString(view(standardForm, Journey.Migrate)))
  lazy val docWithNoOptionSelected: Document = Jsoup.parse(contentAsString(view(noOptionSelectedForm, Journey.Migrate)))
  lazy val docWithIncorrectNino: Document = Jsoup.parse(contentAsString(view(incorrectNinoForm, Journey.Migrate)))
}
