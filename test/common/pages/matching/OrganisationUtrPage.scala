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

trait OrganisationUtrPage extends WebPage {

  val fieldLevelErrorUtr = "//*[@id='utr-outer']//span[@class='error-message']"
  val labelForUtrXpath = "//*[@id='utr-outer']/label"

  override val url: String = Env.frontendHost + "/customs/register-for-cds/matching/utr"

  override val title = "Does your organisation have a Corporation Tax Unique Taxpayer Reference (UTR) number?"
}

object OrganisationUtrPage extends OrganisationUtrPage

object SubscriptionRowIndividualsUtr extends OrganisationUtrPage {
  override val url = Env.frontendHost + "/customs/subscribe-for-cds/row-utr"
  override val title = "Do you have a Self Assessment Unique Taxpayer Reference (UTR) number issued in the UK?"
  val formId = "SubscriptionUtrForm"
}

object SubscriptionRowCompanyUtr extends OrganisationUtrPage {
  override val url = Env.frontendHost + "/customs/subscribe-for-cds/row-utr"
  override val title = "Does your organisation have a Unique Taxpayer Reference (UTR) number issued in the UK?"

  val formId = "SubscriptionUtrForm"
}
