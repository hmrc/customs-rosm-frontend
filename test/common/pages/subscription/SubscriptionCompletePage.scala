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

trait ApplicationUnsuccessfulPage extends WebPage {
  override val url = Env.frontendHost + "/customs/subscribe-for-cds/unsuccessful/16%20May%202018"

  override val title = "The application for register-with-eori-and-id-FAIL has been unsuccessful"

}

object ApplicationPendingPage extends WebPage {
  override val url = Env.frontendHost + "/customs/subscribe-for-cds/pending/"

  override val title = "We are processing the registration for"

}

object ApplicationUnsuccessfulPage extends ApplicationUnsuccessfulPage
