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

package common.pages.subscription

import common.pages.WebPage
import common.support.Env

trait VatDetailsEuConfirmPage extends WebPage {

  override val url: String = Env.frontendHost + "/customs/register-for-cds/vat-details-eu-confirm"
  override val title = "You have added VAT details for 1 EU member country"
}

object VatDetailsEuConfirmPage extends VatDetailsEuConfirmPage {
  def apply(numberOfEuCountries: String): VatDetailsEuConfirmPage = new VatDetailsEuConfirmPage {
    override val title =
      if (numberOfEuCountries.toInt <= 1)
        s"You have added VAT details for $numberOfEuCountries EU member country"
      else
        s"You have added VAT details for $numberOfEuCountries EU member countries"
  }
}
