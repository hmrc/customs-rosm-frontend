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
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.EoriNumberViewModel
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.SubscriptionForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.what_is_your_eori
import util.ViewSpec

class WhatIsYourEoriSpec extends ViewSpec {
  val form: Form[EoriNumberViewModel] = SubscriptionForm.eoriNumberForm
  val formWithInvalidError: Form[EoriNumberViewModel] =
    SubscriptionForm.eoriNumberForm.bind(Map("eori-number" -> "invalidinvalid"))
  val formWithInvalidGbEoriError: Form[EoriNumberViewModel] =
    SubscriptionForm.eoriNumberForm.bind(Map("eori-number" -> "GBthatIsNotValid"))
  val formWithTooShortError: Form[EoriNumberViewModel] =
    SubscriptionForm.eoriNumberForm.bind(Map("eori-number" -> "GB"))
  val formWithTooLongError: Form[EoriNumberViewModel] =
    SubscriptionForm.eoriNumberForm.bind(Map("eori-number" -> "this eori is too long"))
  val formWithEmptyFieldError: Form[EoriNumberViewModel] =
    SubscriptionForm.eoriNumberForm.bind(Map("eori-number" -> ""))
  val isInReviewMode = false
  val isRestOfWorldJourney = false
  val previousPageUrl = "/"
  implicit val request = withFakeCSRF(FakeRequest())

  private val view = app.injector.instanceOf[what_is_your_eori]

  "What Is Your EORI page" should {
    "display correct title" in {
      doc.title must startWith("What is your EORI number?")
    }
    "have the correct heading text" in {
      doc.body.getElementsByClass("heading-large").text() mustBe "What is your EORI number?"
    }
    "have the correct text in the label" in {
      doc.body
        .getElementById("eori-number-hint")
        .text() mustBe "The number starts with GB and is then followed by 12 digits, For example, GB345834921000."
    }
    "have an input of type 'text'" in {
      doc.body.getElementById("eori-number").attr("type") mustBe "text"
    }


    "display a field level error message when the Eori is invalid and starts with GB" in {
      docWithInvalidGbEoriError.body
        .getElementById("eori-number-outer")
        .getElementsByClass("error-message")
        .text mustBe "Enter an EORI number in the right format"
    }


    "display a field level error message when the Eori is too long" in {
      docWithTooLongError.body
        .getElementById("eori-number-outer")
        .getElementsByClass("error-message")
        .text mustBe "Enter an EORI number in the right format"
    }
    "display a field level error message when the Eori field is empty" in {
      docWithEmptyFieldError.body
        .getElementById("eori-number-outer")
        .getElementsByClass("error-message")
        .text mustBe "Enter your EORI number"
    }
  }

  lazy val doc: Document = {
    val result = view(form, isInReviewMode, isRestOfWorldJourney = false, Journey.Migrate)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithInvalidError: Document = {
    val result = view(formWithInvalidError, isInReviewMode, isRestOfWorldJourney, Journey.Migrate)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithInvalidGbEoriError: Document = {
    val result = view(formWithInvalidGbEoriError, isInReviewMode, isRestOfWorldJourney, Journey.Migrate)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithTooShortError: Document = {
    val result = view(formWithTooShortError, isInReviewMode, isRestOfWorldJourney, Journey.Migrate)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithTooLongError: Document = {
    val result = view(formWithTooLongError, isInReviewMode, isRestOfWorldJourney, Journey.Migrate)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithEmptyFieldError: Document = {
    val result = view(formWithEmptyFieldError, isInReviewMode, isRestOfWorldJourney, Journey.Migrate)
    Jsoup.parse(contentAsString(result))
  }
}
