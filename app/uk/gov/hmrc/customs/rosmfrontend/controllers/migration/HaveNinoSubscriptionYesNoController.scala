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

package uk.gov.hmrc.customs.rosmfrontend.controllers.migration

import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.HaveNinoSubscriptionController
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.AddressController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.yesNoCustomAnswerForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.match_nino_subscription_yes_no
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HaveNinoSubscriptionYesNoController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  matchNinoSubscriptionView: match_nino_subscription_yes_no,
  subscriptionDetailsHolderService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(Ok(matchNinoSubscriptionView(yesNoCustomAnswerForm("cds.matching.nino.row.yes-no.error", "have-nino"), journey)))
  }

  def submit(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      yesNoCustomAnswerForm("cds.matching.nino.row.yes-no.error", "have-nino").bindFromRequest.fold(
        formWithErrors => Future.successful(BadRequest(matchNinoSubscriptionView(formWithErrors, journey))),
        formData => destinationsByAnswer(formData, journey)
      )
  }

  private def destinationsByAnswer(form: YesNo, journey: Journey.Value)
                                  (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    if (form.isYes) {
      Future.successful(Redirect(HaveNinoSubscriptionController.createForm(journey)))
    } else {
      subscriptionDetailsHolderService.clearCachedCustomsId map (_ => Redirect(AddressController.createForm(journey)))
    }

}
