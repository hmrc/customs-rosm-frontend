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
import play.api.i18n.Messages
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.{
  ConfirmContactDetailsController,
  SixLineAddressController
}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Individual
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.rowIndividualsNinoForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_nino_row_individual
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DoYouHaveNinoController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  matchingService: MatchingService,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  matchNinoRowIndividualView: match_nino_row_individual,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def displayForm(journey: Journey.Value, isInReviewMode: Boolean = false): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        Future.successful(Ok(matchNinoRowIndividualView(rowIndividualsNinoForm, journey)))
      }
    }

  def submit(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      {
        rowIndividualsNinoForm.bindFromRequest.fold(
          formWithErrors => Future.successful(BadRequest(matchNinoRowIndividualView(formWithErrors, journey))),
          formData =>
            formData.haveNino match {
              case Some(true) =>
                matchIndividual(Nino(formData.nino.get), journey, formData, InternalId(loggedInUser.internalId))
              case Some(false) => {
                subscriptionDetailsService.updateSubscriptionDetails
                noNinoRedirect(journey)
              }
              case _ => throw new IllegalArgumentException("Have NINO should be Some(true) or Some(false) but was None")
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

  private def matchIndividual(id: CustomsId, journey: Journey.Value, formData: NinoMatchModel, internalId: InternalId)(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier
  ): Future[Result] =
    subscriptionDetailsService.cachedNameDobDetails flatMap {
      case Some(details) =>
        matchingService
          .matchIndividualWithId(
            id,
            Individual.withLocalDate(details.firstName, details.middleName, details.lastName, details.dateOfBirth),
            internalId
          )
          .map {
            case true  => Redirect(ConfirmContactDetailsController.form(journey))
            case false => matchNotFoundBadRequest(formData, journey)
          }
      case None => Future.successful(matchNotFoundBadRequest(formData, journey))
    }

  private def matchNotFoundBadRequest(formData: NinoMatchModel, journey: Journey.Value)(
    implicit request: Request[AnyContent]
  ): Result = {
    val errorMsg = Messages("cds.matching-error.individual-not-found")
    val errorForm = rowIndividualsNinoForm.withGlobalError(errorMsg).fill(formData)
    BadRequest(matchNinoRowIndividualView(errorForm, journey))
  }
}
