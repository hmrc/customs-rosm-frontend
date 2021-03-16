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
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.use_this_eori_different_gg
import util.ViewSpec

class UseThisEoriViewDifferentGgSpec extends ViewSpec {

  implicit val request = withFakeCSRF(FakeRequest())

  private val view = app.injector.instanceOf[use_this_eori_different_gg]
  private val eori = "GB123456789123"
  private val page = view(eori, Journey.Migrate)
  private val doc: Document = Jsoup.parse(contentAsString(page))

  "Use this eori different gg view" should {

    "has a title" in {

      doc.body().getElementsByClass("heading-xlarge").text() mustBe "You need to sign in with a different Government Gateway"
    }

    "has first paragraph" in {

      doc.body().getElementById("info1").text() mustBe "This Government Gateway is linked to the EORI number GB123456789123."
    }

    "has second paragraph" in {

      doc.body().getElementById("info2").text() mustBe "To Get access to CDS using a different EORI number you will need to sign in using a different Government Gateway."
    }

    "has sign out button" in {

      doc.body().getElementsByClass("button").text() mustBe "Sign out"
    }
  }
}
