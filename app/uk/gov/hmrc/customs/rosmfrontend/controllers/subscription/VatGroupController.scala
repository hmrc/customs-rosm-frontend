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

package uk.gov.hmrc.customs.rosmfrontend.controllers.subscription

import play.api.Application
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.EmailController
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.vat_group

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class VatGroupController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  vatGroupView: vat_group
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = Action { implicit request =>
    Ok(vatGroupView(vatGroupYesNoAnswerForm(), journey))
  }

  def submit(journey: Journey.Value): Action[AnyContent] = Action { implicit request =>
    vatGroupYesNoAnswerForm()
      .bindFromRequest()
      .fold(
        formWithErrors => BadRequest(vatGroupView(formWithErrors, journey)),
        yesNoAnswer => {
          if (yesNoAnswer.isNo) {
            Redirect(EmailController.form(Journey.GetYourEORI))
          } else {
            Redirect(routes.VatGroupsCannotRegisterUsingThisServiceController.form(journey))
          }
        }
      )
  }
}
