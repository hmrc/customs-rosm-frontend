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
import uk.gov.hmrc.customs.rosmfrontend.views.html.enrolment_pending_against_group_id
import util.ViewSpec

class EnrolmentPendingAgainstGroupIdViewSpec extends ViewSpec {

  private val view = app.injector.instanceOf[enrolment_pending_against_group_id]
  implicit val request = withFakeCSRF(FakeRequest())

  "Enrolment Pending against group id page" should {
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
        .text mustBe "The Government Gateway ID you used to sign in is part of a team that has already applied for an EORI number. This application is being processed."
    }

    "display the correct text for Migrate" in {
      migrateDoc
        .body()
        .getElementById("info")
        .text mustBe "The Government Gateway ID you used to sign in is part of a team that has already applied Get access to CDS. This application is being processed."
    }
  }

  private lazy val gyeDoc: Document = Jsoup.parse(contentAsString(view(Journey.GetYourEORI)))
  private lazy val migrateDoc: Document = Jsoup.parse(contentAsString(view(Journey.Migrate)))

}
