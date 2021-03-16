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

package unit.views.registration

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.subscription_outcome
import util.ViewSpec

class SubscriptionCompleteSpec extends ViewSpec {

  implicit val request = withFakeCSRF(FakeRequest())

  val eori = "GB123445562"
  val orgName = "Test Organisation Name"
  val issuedDate = "01 Jan 2019"

  private val view = app.injector.instanceOf[subscription_outcome]

  "'Subscription Rejected' Page with name" should {

    "display correct heading" in {
      doc.body.getElementsByTag("h1").text() must startWith(s"The EORI number for $orgName is $eori")
    }
    "have the correct class on the h1" in {
      doc.body.getElementsByTag("h1").hasClass("heading-xlarge") mustBe true
    }
    "have the correct processing date and text" in {
      doc.body.getElementById("issued-date").text mustBe s"issued by HMRC on $issuedDate"
    }
  }

  lazy val doc: Document = Jsoup.parse(contentAsString(view(eori, orgName, issuedDate)))
}
