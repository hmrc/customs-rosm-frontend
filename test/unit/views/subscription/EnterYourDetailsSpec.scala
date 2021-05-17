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
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.domain.NameDobMatchModel
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.enter_your_details
import util.ViewSpec

class EnterYourDetailsSpec extends ViewSpec {
  val form: Form[NameDobMatchModel] = enterNameDobForm
  val isInReviewMode = false
  val previousPageUrl = "/"
  implicit val request = withFakeCSRF(FakeRequest())

  private val view = app.injector.instanceOf[enter_your_details]
  private val appConfig = app.injector.instanceOf[AppConfig]

  "Subscription Enter Your Details Page" should {
    "display correct title" in {
      doc.title() must startWith("Enter your details")
    }
    "have the correct h1 text" in {
      doc.body().getElementsByTag("h1").text() mustBe "Enter your details"
    }
    "have the correct class on the h1" in {
      doc.body().getElementsByTag("h1").hasClass("heading-large") mustBe true
    }
    "have a correct label for First name for UK" in {
      doc.body().getElementById("first-name-outer").getElementsByClass("form-label-bold").text() mustBe "First name"
    }
    "have a correct label for given name for Row" in {
      docRestOfWorld
        .body()
        .getElementById("first-name-outer")
        .getElementsByClass("form-label-bold")
        .text() mustBe "Given name"
    }
    "have correct attributes for input for first name" in {
      doc.body().getElementById("first-name").attr("type") mustBe "text"
      doc.body().getElementById("first-name").attr("spellcheck") mustBe "false"
      doc.body().getElementById("first-name").attr("autocomplete") mustBe "first-name"
    }

    "have a correct label for Last name for UK" in {
      doc.body().getElementById("last-name-outer").getElementsByClass("form-label-bold").text() mustBe "Last name"
    }
    "have a correct label for family name for Row" in {
      docRestOfWorld
        .body()
        .getElementById("last-name-outer")
        .getElementsByClass("form-label-bold")
        .text() mustBe "Family name"
    }
    "have correct attributes for input for last name" in {
      doc.body().getElementById("last-name").attr("type") mustBe "text"
      doc.body().getElementById("last-name").attr("spellcheck") mustBe "false"
      doc.body().getElementById("last-name").attr("autocomplete") mustBe "last-name"
    }
    "have an input of type 'text' for day of birth" in {
      doc.body().getElementById("date-of-birth.day").attr("type") mustBe "text"
    }
    "have an input of type 'text' for month of birth" in {
      doc.body().getElementById("date-of-birth.month").attr("type") mustBe "text"
    }
    "have an input of type 'text' for year of birth" in {
      doc.body().getElementById("date-of-birth.year").attr("type") mustBe "text"
    }
  }

  lazy val doc: Document = {
    val result = view(form, isInReviewMode, Journey.Migrate, Some("uk"), appConfig)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docRestOfWorld: Document = {
    val result = view(form, isInReviewMode, Journey.Migrate, selectedUserLocationWithIslands = Some("third-country"), appConfig)
    Jsoup.parse(contentAsString(result))
  }
}
