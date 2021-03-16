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

package common.pages.matching

import common.pages.WebPage
import common.support.Env

trait ConfirmPage extends WebPage {

  val BusinessAddressXPath = "//dd[@class='cya-answer']"
  val fullDetailsXpath = "//dd[@class='cya-answer']"
  val fieldLevelErrorYesNoWrongAddress = "//*[@id='yes-no-wrong-address-fieldset']//span[@class='error-message']"

  override val url: String = Env.frontendHost + "/customs/register-for-cds/matching/confirm"
  override val title = "These are the details we have about your organisation"

}

object ConfirmPage extends ConfirmPage
