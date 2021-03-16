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

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.ConfirmIndividualTypePage
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.SubscriptionForm.confirmIndividualTypeForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.confirm_individual_type

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfirmIndividualTypeController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  requestSessionData: RequestSessionData,
  subscriptionFlowManager: SubscriptionFlowManager,
  confirmIndividualTypeView: confirm_individual_type,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(
        Ok(confirmIndividualTypeView(confirmIndividualTypeForm, journey))
          .withSession(requestSessionData.sessionWithoutOrganisationType)
      )
    }

  def submit(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        confirmIndividualTypeForm.bindFromRequest.fold(
          invalidForm => Future.successful(BadRequest(confirmIndividualTypeView(invalidForm, journey))),
          selectedIndividualType =>
            subscriptionFlowManager
              .startSubscriptionFlow(Some(ConfirmIndividualTypePage), selectedIndividualType, journey) map {
              case (page, newSession) =>
                val sessionWithOrganisationType =
                  requestSessionData.sessionWithOrganisationTypeAdded(newSession, selectedIndividualType)
                Redirect(page.url).withSession(sessionWithOrganisationType)
          }
        )
      }
    }
}
