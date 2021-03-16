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
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.subscription_outcome_fail
import util.ViewSpec

class SubscriptionOutcomeFailSpec extends ViewSpec {

  implicit val request = withFakeCSRF(FakeRequest())

  private val view = app.injector.instanceOf[subscription_outcome_fail]

  val orgName = "Test Organisation Name"
  val processedDate = "01 Jan 2019"

  "'Subscription Fail' Page" should {

    "display correct heading" in {
      doc.body.getElementsByTag("h1").text() must startWith(s"The application for $orgName has been unsuccessful")
    }
    "have the correct class on the h1" in {
      doc.body.getElementsByTag("h1").hasClass("heading-xlarge") mustBe true
    }
    "have the correct class on the h2" in {
      doc.body.getElementsByTag("h2").hasClass("heading-medium") mustBe true
    }
    "have the correct processing date and text" in {
      doc.body.getElementById("active-from").text mustBe s"Application received by HMRC on $processedDate"
    }
  }

  lazy val doc: Document = Jsoup.parse(contentAsString(view(processedDate, orgName)))
}
