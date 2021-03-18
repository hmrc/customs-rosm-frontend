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
import play.api.data.Form
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.{VatDetails, VatDetailsForm}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.vat_details
import util.ViewSpec

class VatDetailsUkSpec extends ViewSpec {
  val form: Form[VatDetails] = VatDetailsForm.vatDetailsForm
  val formWithError: Form[VatDetails] =
    VatDetailsForm.vatDetailsForm.bind(Map("postcode" -> "", "vat-number" -> "", "vat-effective-date" -> ""))

  val formWithNoVatError: Form[VatDetails] =
    VatDetailsForm.vatDetailsForm.bind(
      Map("postcode" -> "", "vat-number" -> "GB 12 34 56 78 9", "vat-effective-date" -> "")
    )

  private val view = app.injector.instanceOf[vat_details]
  implicit val request = withFakeCSRF(FakeRequest())

  lazy val doc: Document = Jsoup.parse(contentAsString(view(form, false, Journey.GetYourEORI)))
  lazy val docWithErrors: Document = Jsoup.parse(contentAsString(view(formWithError, false, Journey.GetYourEORI)))
  lazy val docWithNoVatErrors: Document =
    Jsoup.parse(contentAsString(view(formWithNoVatError, false, Journey.GetYourEORI)))

  "The 'VAT Details UK?' Page" should {

    "display correct title" in {
      doc.title must startWith("What are your UK VAT details?")
    }

    "have the correct class on the h1" in {
      doc.body.getElementsByTag("h1").hasClass("heading-large") mustBe true
    }

    "have correct labels" in {
      doc
        .body()
        .getElementById("postcode-outer")
        .getElementsByClass("form-label-bold")
        .text() mustBe "The postcode of your VAT registration address"

      doc
        .body()
        .getElementById("vat-number-outer")
        .getElementsByClass("form-label-bold")
        .text() mustBe "VAT registration number This is 9 numbers, sometimes with ‘GB’ at the start, for example 123456789 or GB123456789."

      doc
        .body()
        .getElementById("vat-effective-date-label-text")
        .text() mustBe "Effective VAT date"
    }

    "have a no VAT error on page level error list" in {
      docWithNoVatErrors.body
        .getElementsByClass("error-summary-list")
        .text mustBe "Enter a valid postcode of your VAT registration address Enter your effective VAT date, for example '31 3 1980'"
    }

    "have a page level error list" in {
      docWithErrors.body
        .getElementsByClass("error-summary-list")
        .text mustBe "Enter a valid postcode of your VAT registration address Enter your VAT registration number Enter your effective VAT date, for example '31 3 1980'"
    }

    "have a field level error" in {
      docWithErrors.body
        .getElementsByClass("error-message")
        .text mustBe "Enter a valid postcode of your VAT registration address Enter your VAT registration number Enter your effective VAT date, for example '31 3 1980'"
    }
  }
}
