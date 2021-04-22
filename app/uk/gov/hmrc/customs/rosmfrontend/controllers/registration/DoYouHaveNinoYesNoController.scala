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

import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.{DoYouHaveNinoController, SixLineAddressController}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.yesNoCustomAnswerForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_nino_row_individual_yes_no
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DoYouHaveNinoYesNoController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  matchingService: MatchingService,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  matchNinoRowIndividualView: match_nino_row_individual_yes_no,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def displayForm(journey: Journey.Value, isInReviewMode: Boolean = false): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        Future.successful(Ok(matchNinoRowIndividualView(yesNoCustomAnswerForm("cds.matching.nino.row.yes-no.error", "have-nino"), journey)))
      }
    }

  def submit(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      {
        yesNoCustomAnswerForm("cds.matching.nino.row.yes-no.error", "have-nino").bindFromRequest.fold(
          formWithErrors => Future.successful(BadRequest(matchNinoRowIndividualView(formWithErrors, journey))),
          formData =>
            if (formData.isYes) {
              Future.successful(Redirect(DoYouHaveNinoController.displayForm(journey)))
            } else {
              subscriptionDetailsService.updateSubscriptionDetails
              noNinoRedirect(journey)
            }
        )
      }
  }

  private def noNinoRedirect(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    requestSessionData.userSelectedOrganisationType match {
      case Some(cdsOrgType) =>
        Future.successful(Redirect(SixLineAddressController.showForm(false, cdsOrgType.id, journey)))
      case _ => throw new IllegalStateException("No userSelectedOrganisationType details in session.")
    }

}
