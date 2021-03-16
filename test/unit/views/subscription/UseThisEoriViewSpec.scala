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

import play.api.test.FakeRequest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.use_this_eori
import util.ViewSpec

class UseThisEoriViewSpec extends ViewSpec {

  implicit val request = withFakeCSRF(FakeRequest())

  private val view = app.injector.instanceOf[use_this_eori]
  private val eori = "GB123456789123"
  private val form = MatchingForms.useThisEoriYesNoAnswer()
  private val page = view(eori, form, Journey.Migrate)
  private val doc: Document = Jsoup.parse(contentAsString(page))

  "Use this eori view" should {

    "has a title" in {

      doc.body().getElementsByClass("heading-xlarge").text() mustBe "This is the EORI number linked to your Government Gateway"
    }

    "has EORI table with EORI" in {

      doc.body().getElementById("eori-number").text() mustBe "EORI number GB123456789123"
    }

    "has question" in {

      doc.body().getElementsByClass("heading-medium").first().text() mustBe "Is this the EORI number you want to use to Get access to CDS?"
    }

    "has 'yes' radio button" in {
      doc.body().getElementById("yes-no-answer-true").attr("checked") mustBe empty
    }

    "has 'no' radio button" in {
      doc.body().getElementById("yes-no-answer-false").attr("checked") mustBe empty
    }

    "has Continue button" in {
      doc.body().getElementsByClass("button").attr("value") mustBe "Continue"
    }
  }
}
