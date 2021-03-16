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

object SubscriptionAmendCompanyDetailsPage extends WebPage {
  override val title = "Your details"

  override val url: String = Env.frontendHost + "/customs/register-for-cds/company-details"

  val useShortNameFieldLevelErrorXpath = "//span[@class='error-message']"

  val shortNameLabelXpath = "//*label[@for='short-name']"
  val shortNameFieldLevelErrorXpath: String = fieldLevelErrorXpath("short-name")

  val sicLabelXpath = "//*label[@for='sic']"
  val sicFieldLevelErrorXpath: String = fieldLevelErrorXpath("sic")

  val eoriNumberLabelXpath = "//*label[@for='eori-number']"
  val eoriNumberFieldLevelErrorXpath: String = fieldLevelErrorXpath("eori-number")

}
