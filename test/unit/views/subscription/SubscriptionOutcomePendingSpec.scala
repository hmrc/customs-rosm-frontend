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
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.subscription_outcome_pending
import util.ViewSpec

class SubscriptionOutcomePendingSpec extends ViewSpec {

  implicit val request = withFakeCSRF(FakeRequest())

  private val view = app.injector.instanceOf[subscription_outcome_pending]

  val orgName = "Test Organisation Name"
  val eoriNumber = "EORI123"
  val processedDate = "01 Jan 2019"

  "'Subscription Pending' Page" should {

    "display correct heading" in {
      doc.body.getElementsByTag("h1").text() must startWith(s"We are processing the registration for $orgName")
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
    "have the correct eori number" in {
      doc.body.getElementById("eori-number").text mustBe s"EORI number: $eoriNumber"
    }
    "have the correct 'what happens next' text" in {
      doc.body
        .getElementById("what-happens-next")
        .text mustBe "What happens next We are processing your registration to CDS. This can take up to 5 working days. You will need to sign back in to see the result of your registration."
    }
  }

  lazy val doc: Document = Jsoup.parse(contentAsString(view(eoriNumber, processedDate, orgName)))
}
