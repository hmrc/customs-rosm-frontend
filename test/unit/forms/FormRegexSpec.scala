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

package unit.forms

import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.SubscriptionForm
import util.UnitSpec

class FormRegexSpec extends UnitSpec {

  "UTR Regex" should {

    "match valid utr format" in {
      val utrRegex = MatchingForms.utrRegex
      val utr = "1234567891"
      utr.matches(utrRegex.regex) shouldBe true
    }

    "match  utr format with spaces" in {
      val utrRegex = MatchingForms.utrRegex
      val utr = "12 34 56 78 91"
      utr.matches(utrRegex.regex) shouldBe true
    }

    "match  utr format start with k" in {
      val utrRegex = MatchingForms.utrRegex
      val utr = "k12 34 56 78 91"
      utr.matches(utrRegex.regex) shouldBe true
    }

    "match  utr format ends with K" in {
      val utrRegex = MatchingForms.utrRegex
      val utr = "12 34 56 78 91 K"
      utr.matches(utrRegex.regex) shouldBe true
    }

    "match 13 digits utr format ends with K" in {
      val utrRegex = MatchingForms.utrRegex
      val utr = "12 34 56 78 90 12 3 K"
      utr.matches(utrRegex.regex) shouldBe true
    }
  }

  "NINO Regex" should {

    "match valid NINO format" in {
      val ninoRegex = MatchingForms.ninoRegex
      val nino = "AB123456C"
      nino.matches(ninoRegex.regex) shouldBe true
    }


    "match  NINO format with spaces" in {
      val ninoRegex = MatchingForms.ninoRegex
        val nino = "AB 12 34 56 C"
      nino.matches(ninoRegex.regex) shouldBe true
    }
  }


  "EORI Regex" should {

    "match valid EORI format" in {
      val eoriRegex = SubscriptionForm.eoriRegex
      val eori = "gb12345678901113"
      eori.matches(eoriRegex.regex) shouldBe true
    }

    "match  EORI format with spaces" in {
      val eoriRegex = SubscriptionForm.eoriRegex
      val eori = "GB 12 34 56 78 90 11 13"
      eori.matches(eoriRegex.regex) shouldBe true
    }
  }

  "SIC code Regex" should {
    "match valid SIC format" in {
      val sicCodeRegex = SubscriptionForm.sicCodeRegex
      val sicCode = "12340"
      sicCode.matches(sicCodeRegex.regex) shouldBe true
    }

    "match  SIC format with spaces" in {
      val sicCodeRegex = SubscriptionForm.sicCodeRegex
      val sicCode = "12 34 0"
      sicCode.matches(sicCodeRegex.regex) shouldBe true
    }
  }
}
