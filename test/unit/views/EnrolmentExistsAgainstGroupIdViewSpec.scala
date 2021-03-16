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

package unit.views

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.enrolment_exists_against_group_id
import util.ViewSpec

class EnrolmentExistsAgainstGroupIdViewSpec extends ViewSpec {

  private val view = app.injector.instanceOf[enrolment_exists_against_group_id]
  implicit val request = withFakeCSRF(FakeRequest())

  "Enrolment exists against group id page" should {
    "display correct title" in {
      gyeDoc.title() must startWith("You cannot use this service")
    }

    "display correct heading" in {
      gyeDoc.body().getElementsByTag("h1").text() mustBe "You cannot use this service"
    }

    "have the correct class on the h1" in {
      gyeDoc.body().getElementsByTag("h1").hasClass("heading-large") mustBe true
    }

    "display the correct text for Gye" in {
      gyeDoc
        .body()
        .getElementById("info")
        .text mustBe "The Government Gateway ID you used is part of a team that already has an EORI linked to it."
    }

    "display the correct text for Migrate" in {
      migrateDoc
        .body()
        .getElementById("info")
        .text mustBe "The Government Gateway ID you used to sign in is part of a team that already has access to CDS."
    }

    "display the correct header and steps for Gye" in {
      gyeDoc.body().getElementById("steps-header").text mustBe "If you need to give yourself access to CDS:"
      gyeDoc
        .body()
        .getElementById("step1")
        .text mustBe "Sign in to your Business tax account (opens in a new window or tab)."
      gyeDoc.body().getElementById("step2").text mustBe "Select 'manage account'."
      gyeDoc.body().getElementById("step3").text mustBe "Select 'give a team member access to a tax, duty or scheme'."
      gyeDoc.body().getElementById("step4").text mustBe "Select 'manage taxes and schemes' next to your name."
      gyeDoc.body().getElementById("step5").text mustBe "Select 'Customs Declaration Service (CDS)'."
      gyeDoc.body().getElementById("step6").text mustBe "Save and exit."
    }

    "display the correct headee for Migrate" in {
      migrateDoc.body().getElementById("steps-header").text mustBe "To give yourself access to CDS:"
    }
  }

  private lazy val gyeDoc: Document = Jsoup.parse(contentAsString(view(Journey.GetYourEORI)))
  private lazy val migrateDoc: Document = Jsoup.parse(contentAsString(view(Journey.Migrate)))
}
