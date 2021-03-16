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
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.migration_start
import util.ViewSpec

class SubscriptionStartSpec extends ViewSpec {
  implicit val request = withFakeCSRF(FakeRequest())

  private val view = app.injector.instanceOf[migration_start]

  lazy val doc = Jsoup.parse(contentAsString(view(Journey.Migrate)))

  "Subscription Start Page" should {

    "display correct title" in {
      doc.title() must startWith("Get access to the Customs Declaration Service (CDS)")
    }

    "have the correct h1 text" in {
      doc.body().getElementsByTag("h1").text() mustBe "Get access to the Customs Declaration Service (CDS)"
    }

    "have the correct class on the h1" in {
      doc.body().getElementsByTag("h1").hasClass("heading-xlarge") mustBe true
    }

    "have a button with the correct href" in {
      doc
        .body()
        .getElementsByClass("button--get-started")
        .attr("href") mustBe "/customs/subscribe-for-cds/are-you-based-in-uk"
    }
  }
}
