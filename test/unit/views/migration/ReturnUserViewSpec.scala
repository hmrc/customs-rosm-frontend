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
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.return_user
import util.ViewSpec

class ReturnUserViewSpec extends ViewSpec {

  private val view = app.injector.instanceOf[return_user]
  implicit val request = withFakeCSRF(FakeRequest())

  lazy val doc: Document = Jsoup.parse(contentAsString(view(Journey.Migrate)))

  "The 'Checking the status of your application' Page" should {

    "display correct title" in {
      doc.title must startWith("Checking the status of your application")
    }

    "have the correct class on the h1" in {
      doc.body.getElementsByTag("h1").hasClass("heading-large") mustBe true
    }

    "have the correct intro paragraph" in {
      doc.body
        .getElementById("intro")
        .text() mustBe "Before we can show you the status of your application, we need to verify some of the information you applied with."
    }

    "have the correct h2 text" in {
      doc.body.getElementsByTag("h2").text() mustBe "Information you might need to tell us:"
    }

    "have the correct bullet points" in {
      doc.body
        .getElementsByTag("ul")
        .text() mustBe "EORI number issued in the UK Unique Taxpayer Reference (UTR) National Insurance number Privacy policy Terms and conditions Accessibility Statement"
    }
  }
}
