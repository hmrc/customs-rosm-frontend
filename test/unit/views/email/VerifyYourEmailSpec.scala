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

package unit.views.email

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.email.verify_your_email
import util.ViewSpec

class VerifyYourEmailSpec extends ViewSpec {
  val isInReviewMode = false
  val previousPageUrl = "/"
  implicit val request = withFakeCSRF(FakeRequest())

  val view = app.injector.instanceOf[verify_your_email]

  "What Is Your Email Address page" should {
    "display correct title" in {
      doc.title must startWith("Verify your email address")
    }
    "have the correct h1 text" in {
      doc.body.getElementsByTag("h1").text() mustBe "Verify your email address"
    }
    "have the correct class on the h1" in {
      doc.body.getElementsByTag("h1").hasClass("heading-large") mustBe true
    }

    "have an change your email address 'text' and change email link" in {
      doc.body.getElementById("p2").text() mustBe "You can change your email address if it is not correct."
      doc.body
        .getElementById("p2")
        .select("a[href]")
        .attr("href") mustBe "/customs/subscribe-for-cds/matching/what-is-your-email"
    }
    "have an link send it again" in {
      doc.body
        .getElementById("p3")
        .select("a[href]")
        .attr("href") mustBe "/customs/subscribe-for-cds/matching/check-your-email"
    }
  }

  lazy val doc: Document = {
    val email = "test@example.com"
    val result = view(Some(email), Journey.Migrate)
    Jsoup.parse(contentAsString(result))
  }

}
