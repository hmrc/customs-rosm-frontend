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
import uk.gov.hmrc.customs.rosmfrontend.domain.YesNo
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.yesNoCustomAnswerForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.match_nino_subscription_yes_no
import util.ViewSpec

class HaveNinoSubscriptionYesNoSpec extends ViewSpec {

  implicit val request = withFakeCSRF(FakeRequest())

  private val standardForm: Form[YesNo] = yesNoCustomAnswerForm("Tell us if you have a National Insurance number", "have-nino")
  private val noOptionSelectedForm = yesNoCustomAnswerForm("Tell us if you have a National Insurance number", "have-nino").bind(Map.empty[String, String])
  private val incorrectNinoForm = yesNoCustomAnswerForm("Tell us if you have a National Insurance number", "have-nino").bind(Map("have-nino" -> "true"))

  private val view = app.injector.instanceOf[match_nino_subscription_yes_no]

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
        .getElementsByClass("form-hint")
        .text mustBe "You will have a National Insurance number if you have worked in the UK."
    }
  }

  "No option selected Subscription Have Nino Page" should {
    "have page level error with correct message" in {
      docWithNoOptionSelected.body.getElementById("form-error-heading").text mustBe "There is a problem."
    }
  }

  lazy val doc: Document = Jsoup.parse(contentAsString(view(standardForm, Journey.Migrate)))
  lazy val docWithNoOptionSelected: Document = Jsoup.parse(contentAsString(view(noOptionSelectedForm, Journey.Migrate)))
  lazy val docWithIncorrectNino: Document = Jsoup.parse(contentAsString(view(incorrectNinoForm, Journey.Migrate)))
}
