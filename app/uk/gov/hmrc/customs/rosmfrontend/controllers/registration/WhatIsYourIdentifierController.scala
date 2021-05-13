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
import play.api.i18n.Messages
import play.api.mvc.{Action, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.SecuritySignOutController
import uk.gov.hmrc.customs.rosmfrontend.domain.{CustomsId, _}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Individual
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.{
  ninoIdentityForm,
  utrIdentityForm
}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.{
  what_is_your_nino,
  what_is_your_utr
}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatIsYourIdentifierController @Inject()(
    override val currentApp: Application,
    override val authConnector: AuthConnector,
    matchingService: MatchingService,
    mcc: MessagesControllerComponents,
    ninoIdentityView: what_is_your_nino,
    utrIdentityView: what_is_your_utr,
    sessionCache: SessionCache
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(organisationType: String,
           journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        {
          sessionCache.hasNino.map {
            case Some(true) =>
              val form = ninoIdentityForm
              Ok(
                ninoIdentityView(form,
                                 isInReviewMode = false,
                                 journey,
                                 organisationType))
            case Some(false) =>
              val form = utrIdentityForm
              Ok(
                utrIdentityView(form,
                                isInReviewMode = false,
                                journey,
                                organisationType))
            case _ => Redirect(SecuritySignOutController.displayPage(journey))
          }
        }
    }

  def submit(organisationType: String,
             journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => loggedInUser: LoggedInUserWithEnrolments =>
        {
          val internalId = InternalId(loggedInUser.internalId)
          sessionCache.hasNino.flatMap {
            case Some(true) => {
              ninoIdentityForm.bindFromRequest.fold(
                formWithErrors =>
                  Future.successful(
                    BadRequest(
                      ninoIdentityView(formWithErrors,
                                       isInReviewMode = false,
                                       journey,
                                       organisationType)
                    )),
                nino => matchOnId(nino.normalize(), internalId, organisationType, journey)
              )
            }
            case Some(false) =>
              utrIdentityForm.bindFromRequest.fold(
                formWithErrors =>
                  Future.successful(
                    BadRequest(
                      utrIdentityView(formWithErrors,
                                      isInReviewMode = false,
                                      journey,
                                      organisationType)
                    )),
                utr => matchOnId(utr.normalize(), internalId, organisationType, journey)
              )
            case _ =>
              Future.successful(
                Redirect(SecuritySignOutController.displayPage(journey)))
          }
        }
    }

  private def matchOnId(customsId: CustomsId,
                        internalId: InternalId,
                        organisationType: String,
                        journey: Journey.Value)(
      implicit request: Request[AnyContent],
      hc: HeaderCarrier): Future[Result] =
    customsId match {
      case nino @ Nino(_) =>
        retrieveNameDobFromCache()
          .flatMap(ind =>
            matchingService.matchIndividualWithId(nino, ind, internalId))
          .map { isMatched =>
            handleNinoResponse(isMatched, nino, organisationType, journey)
          }
      case utr @ Utr(_) =>
        retrieveNameDobFromCache()
          .flatMap(ind =>
            matchingService.matchIndividualWithId(utr, ind, internalId))
          .map { isMatched =>
            handleUtrResponse(isMatched, utr, organisationType, journey)
          }
      case _ =>
        Future.successful(
          Redirect(SecuritySignOutController.displayPage(journey)))
    }

  private def handleNinoResponse(isMatched: Boolean,
                                 nino: Nino,
                                 organisationType: String,
                                 journey: Journey.Value)(
      implicit request: Request[AnyContent],
      hc: HeaderCarrier): Result = {
    isMatched match {
      case false => {
        val errorForm = ninoIdentityForm
          .withGlobalError(Messages("cds.matching-error.individual-not-found"))
          .fill(nino)
        BadRequest(
          ninoIdentityView(errorForm,
                           isInReviewMode = false,
                           journey,
                           organisationType))
      }
      case true => {
        Redirect(ConfirmContactDetailsController.form(journey))
      }
    }
  }

  private def handleUtrResponse(isMatched: Boolean,
                                utr: Utr,
                                organisationType: String,
                                journey: Journey.Value)(
      implicit request: Request[AnyContent],
      hc: HeaderCarrier) = {
    isMatched match {
      case false => {
        val errorForm = utrIdentityForm
          .withGlobalError(Messages("cds.matching-error.individual-not-found"))
          .fill(utr)
        BadRequest(
          utrIdentityView(errorForm,
                          isInReviewMode = false,
                          journey,
                          organisationType))
      }
      case true => {
        Redirect(ConfirmContactDetailsController.form(journey))
      }
    }
  }

  private def retrieveNameDobFromCache()(
      implicit hc: HeaderCarrier): Future[Individual] =
    for {
      mayBeNameDobDetails <- sessionCache.subscriptionDetails.map(
        _.nameDobDetails)
    } yield
      mayBeNameDobDetails
        .map { nameDobDetails =>
          Individual.withLocalDate(
            firstName = nameDobDetails.firstName,
            middleName = None,
            lastName = nameDobDetails.lastName,
            dateOfBirth = nameDobDetails.dateOfBirth
          )
        }
        .getOrElse(
          throw new IllegalStateException("Individual details not in cache"))

}
