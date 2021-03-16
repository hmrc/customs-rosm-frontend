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

package unit.forms.models.subscription

import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Address
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.AddressViewModel
import util.UnitSpec

class AddressViewModelSpec extends UnitSpec {

  val addressLine1 = "some building"
  val addressLine2 = "some street"
  val addressLine3 = "some area"
  val addressLine4 = "some town"
  val postCode = "PC55 5AA"
  val countryCode = "EN"

  val actualAddress =
    Address(addressLine1, Some(addressLine2), Some(addressLine3), Some(addressLine4), Some(postCode), countryCode)
  val expectedAddress = AddressViewModel(addressLine1 + " " + addressLine2, addressLine3, Some(postCode), countryCode)

  "AddressViewModel" should {

    "concatenate a 6 line address into a 4 line address" in {
      AddressViewModel(actualAddress) shouldEqual expectedAddress
    }

    "limit line 2 field to 35 chars" in {
      val longAddress = Address(
        addressLine1,
        Some(addressLine2),
        Some("Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch"),
        Some(addressLine4),
        Some(postCode),
        countryCode
      )
      AddressViewModel(longAddress).city shouldEqual "Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch".take(
        35
      )
      AddressViewModel(longAddress).city.length shouldEqual 35
    }
  }
}
