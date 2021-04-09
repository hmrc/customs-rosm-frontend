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

///*
// * Copyright 2021 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package unit.views.subscription
//
//import org.jsoup.Jsoup
//import org.jsoup.nodes.Document
//import play.api.data.Form
//import play.api.test.FakeRequest
//import play.api.test.Helpers.contentAsString
//import uk.gov.hmrc.customs.rosmfrontend.domain.NinoOrUtr
//import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
//import uk.gov.hmrc.customs.rosmfrontend.models.Journey
//import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.how_can_we_identify_you
//import util.ViewSpec
//
//class HowCanWeIdentifyYouSpec extends ViewSpec {
//  val form: Form[NinoOrUtr] = ninoOrUtrForm
//  val formWithNothingSelected: Form[NinoOrUtr] = ninoOrUtrForm.bind(Map("ninoOrUtrRadio" -> ""))
//  val formWithNinoSelected: Form[NinoOrUtr] = ninoOrUtrForm.bind(Map("ninoOrUtrRadio" -> "nino", "nino" -> ""))
//  val formWithUtrSelected: Form[NinoOrUtr] = ninoOrUtrForm.bind(Map("ninoOrUtrRadio" -> "utr", "utr" -> ""))
//
//  val isInReviewMode = false
//  val previousPageUrl = "/"
//  implicit val request = withFakeCSRF(FakeRequest())
//
//  private val view = app.injector.instanceOf[how_can_we_identify_you]
//
//  "Subscription Enter Your Nino Page" should {
//
//    "display correct heading" in {
//      doc.body().getElementsByTag("h1").text() mustBe "What information can we use to confirm your identity?"
//    }
//
//    "include the heading in the title" in {
//      doc.title() must startWith(doc.body().getElementsByTag("h1").text())
//    }
//
//    "have the correct class on the h1" in {
//      doc.body().getElementsByTag("h1").hasClass("heading-large") mustBe true
//    }
//
//    "have nino displayed but not selected" in {
//      doc.body().getElementById("nino-radio").attr("checked") mustBe empty
//    }
//
//    "have utr displayed but not selected" in {
//      doc.body().getElementById("utr-radio").attr("checked") mustBe empty
//    }
//
//    "have an input of type 'radio' for nino" in {
//      doc.body().getElementById("nino-radio").attr("type") mustBe "radio"
//    }
//
//    "have an input of type 'radio' for utr" in {
//      doc.body().getElementById("utr-radio").attr("type") mustBe "radio"
//    }
//
//    "display an page level error if no radio button is selected" in {
//      docWithRadioButtonsError
//        .body()
//        .getElementsByClass("error-summary-list")
//        .text() mustBe "Tell us how we can identify you"
//    }
//
//    "display an field level error if no radio button is selected" in {
//      docWithRadioButtonsError
//        .body()
//        .getElementsByClass("error-message")
//        .text() mustBe "Tell us how we can identify you"
//    }
//
//    "display an page level error if nino radio button is selected but no nino entered" in {
//      docWithNoNinoError
//        .body()
//        .getElementsByClass("error-summary-list")
//        .text() mustBe "Enter your National Insurance number"
//    }
//
//    "display an field level error if nino radio button is selected but no nino entered" in {
//      docWithNoNinoError.body().getElementsByClass("error-message").text() mustBe "Enter your National Insurance number"
//    }
//
//    "display an page level error if utr radio button is selected but no nino entered" in {
//      docWithNoUtrError.body().getElementsByClass("error-summary-list").text() mustBe "Enter your Unique Taxpayer Reference"
//    }
//
//    "display an field level error if utr radio button is selected but no utr entered" in {
//      docWithNoUtrError.body().getElementsByClass("error-message").text() mustBe "Enter your Unique Taxpayer Reference"
//    }
//  }
//
//  lazy val doc: Document = Jsoup.parse(contentAsString(view(form, isInReviewMode, Journey.Migrate)))
//  lazy val docWithRadioButtonsError: Document =
//    Jsoup.parse(contentAsString(view(formWithNothingSelected, isInReviewMode, Journey.Migrate)))
//  lazy val docWithNoNinoError: Document =
//    Jsoup.parse(contentAsString(view(formWithNinoSelected, isInReviewMode, Journey.Migrate)))
//  lazy val docWithNoUtrError: Document =
//    Jsoup.parse(contentAsString(view(formWithUtrSelected, isInReviewMode, Journey.Migrate)))
//}
