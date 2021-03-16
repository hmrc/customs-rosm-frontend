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

package uk.gov.hmrc.customs.rosmfrontend.controllers.registration

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.domain.YesNo
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.vat_registered_uk

import scala.concurrent.ExecutionContext
@Singleton
class VatRegisteredUkController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  vatRegisteredUkView: vat_registered_uk,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(): Action[AnyContent] = Action { implicit request =>
    Ok(vatRegisteredUkView(vatRegisteredUkYesNoAnswerForm()))
  }

  def submit(): Action[AnyContent] = Action { implicit request =>
    vatRegisteredUkYesNoAnswerForm()
      .bindFromRequest()
      .fold(
        formWithErrors => BadRequest(vatRegisteredUkView(formWithErrors)),
        vatRegisteredUkYesNoAnswerForm => Redirect(destinationsByAnswer(vatRegisteredUkYesNoAnswerForm))
      )
  }

  def destinationsByAnswer(yesNoAnswer: YesNo)(implicit request: Request[AnyContent]): String = yesNoAnswer match {
    case theAnswer if theAnswer.isYes => "https://www.tax.service.gov.uk/shortforms/form/EORIVAT?details=&vat=yes"
    case _                            => "https://www.tax.service.gov.uk/shortforms/form/EORINonVATImport?details=&vat=no"
  }
}
