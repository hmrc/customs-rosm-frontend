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

package unit

import uk.gov.hmrc.customs.rosmfrontend.util.XSSSanitiser
import util.UnitSpec
class XSSSanitiserSpec extends UnitSpec {

  "XSS Sanitiser " should {

    "strip out <script> tags" in {
      XSSSanitiser.sanitise("<script style='wacky'>banana hello 123") shouldBe "banana hello 123"
    }

    "strip out <script> and </script> tags" in {
      XSSSanitiser.sanitise("<script>banana hello 123") shouldBe "banana hello 123"
    }

    "strip out </script> tags" in {
      XSSSanitiser.sanitise("banana hello 123</script>") shouldBe "banana hello 123"
    }

    "strip out vbscript" in {
      XSSSanitiser.sanitise("vbscript:banana hello 123") shouldBe "banana hello 123"
    }

    "strip out javascript" in {
      XSSSanitiser.sanitise("javascript:banana hello 123") shouldBe "banana hello 123"
    }

    "strip out expression" in {
      XSSSanitiser.sanitise("expression(banana hello 123)") shouldBe ""
    }

    "strip out onload" in {
      XSSSanitiser.sanitise("onload=banana hello 123") shouldBe "banana hello 123"
    }

    "strip out eval" in {
      XSSSanitiser.sanitise("eval(banana hello 123)") shouldBe ""
    }

    "strip out src" in {
      XSSSanitiser.sanitise("src='banana hello 123'") shouldBe ""
    }

  }
}
