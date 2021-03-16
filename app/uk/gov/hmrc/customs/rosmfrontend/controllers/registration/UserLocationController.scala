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
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector}
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.{CdsController, FeatureFlags}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.registration.RegistrationDisplayResponse
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.Save4LaterService
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegistrationDisplayService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{NewSubscription, _}
import uk.gov.hmrc.customs.rosmfrontend.views.html.error_template
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration._
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.{subscription_status_outcome_processing, subscription_status_outcome_rejected}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserLocationController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  requestSessionData: RequestSessionData,
  save4LaterService: Save4LaterService,
  subscriptionStatusService: SubscriptionStatusService,
  taxEnrolmentsService: TaxEnrolmentsService,
  sessionCache: SessionCache,
  registrationDisplayService: RegistrationDisplayService,
  mcc: MessagesControllerComponents,
  userLocationView: user_location,
  subscriptionStatusOutcomeProcessing: subscription_status_outcome_processing,
  subscriptionStatusOutcomeRejected: subscription_status_outcome_rejected,
  errorTemplate: error_template
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with FeatureFlags {

  private def isAffinityOrganisation(affinityGroup: Option[AffinityGroup]): Boolean =
    affinityGroup.contains(AffinityGroup.Organisation)

  private def continue(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    Future.successful(Ok(userLocationView(userLocationForm, journey, isAffinityOrganisation(user.affinityGroup))))

  def form(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user: LoggedInUserWithEnrolments =>
      continue(journey)
    }

  private def forRow(journey: Journey.Value, internalId: InternalId, location: String)(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier
  ) =
    subscriptionStatusBasedOnSafeId(internalId).map {
      case (NewSubscription | SubscriptionRejected, Some(safeId)) => {
        registrationDisplayService
          .requestDetails(safeId)
          .flatMap(cacheAndRedirect(journey, location))
      }
      case (status, _) =>
        subscriptionStatus(status, internalId, journey, Some(location))
    }.flatMap(identity)

  def submit(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      userLocationForm.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(
            BadRequest(userLocationView(formWithErrors, journey, isAffinityOrganisation(loggedInUser.affinityGroup)))
          )
        },
        details =>
          (journey, details.location, loggedInUser.internalId) match {
            case (_, Some(UserLocation.Iom), Some(_)) =>
              Future.successful(Redirect(YouNeedADifferentServiceIomController.form(journey)))
            case (Journey.GetYourEORI, Some(location), Some(id)) if UserLocation.isRow(location) =>
              forRow(journey, InternalId(id), location)
            case _ =>
              Future.successful(
                Redirect(OrganisationTypeController.form(journey))
                  .withSession(
                    requestSessionData
                      .sessionWithUserLocationAdded(sessionInfoBasedOnJourney(journey, details.location))
                  )
              )
        }
      )
    }

  private def sessionInfoBasedOnJourney(journey: Journey.Value, location: Option[String]): String =
    journey match {
      case Journey.GetYourEORI =>
        location match {
          case Some(UserLocation.ThirdCountry) => "third-country"
          case Some(UserLocation.Eu)           => "eu"
          case Some(UserLocation.Iom)          => "iom"
          case Some(UserLocation.Islands)      => "islands"
          case Some(UserLocation.Uk)           => "uk"
          case _                               => throw new IllegalStateException("User Location not set")
        }
      case _ =>
        location.getOrElse(throw new IllegalStateException("User Location not set"))

    }

  private def subscriptionStatusBasedOnSafeId(internalId: InternalId)(implicit hc: HeaderCarrier) =
    for {
      mayBeSafeId <- save4LaterService.fetchSafeId(internalId)
      preSubscriptionStatus <- mayBeSafeId match {
        case Some(safeId) =>
          subscriptionStatusService.getStatus("SAFE", safeId.id)
        case None => Future.successful(NewSubscription)
      }
    } yield (preSubscriptionStatus, mayBeSafeId)

  private def handleExistingSubscription(
    internalId: InternalId
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    save4LaterService
      .fetchSafeId(internalId)
      .flatMap(
        safeId =>
          sessionCache
            .saveRegistrationDetails(RegistrationDetails.rdSafeId(safeId.get))
            .flatMap { _ =>
              taxEnrolmentsService.doesEnrolmentExist(safeId.get).map {
                case true =>
                  Redirect(
                    SignInWithDifferentDetailsController
                      .form(Journey.GetYourEORI)
                  )
                case false =>
                  Redirect(
                    SubscriptionRecoveryController
                      .complete(Journey.GetYourEORI)
                  )
              }
          }
      )

  def subscriptionStatus(
    preSubStatus: PreSubscriptionStatus,
    internalId: InternalId,
    journey: Journey.Value,
    location: Option[String]
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    preSubStatus match {
      case SubscriptionProcessing =>
        Future.successful(Redirect(UserLocationController.processing()))
      case SubscriptionExists => handleExistingSubscription(internalId)
      case NewSubscription | SubscriptionRejected =>
        Future.successful(
          Redirect(OrganisationTypeController.form(journey))
            .withSession(requestSessionData.sessionWithUserLocationAdded(sessionInfoBasedOnJourney(journey, location)))
        )
    }

  def cacheAndRedirect(journey: Journey.Value, location: String)(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier
  ): Either[_, RegistrationDisplayResponse] => Future[Result] = {

    case rResponse @ Right(RegistrationDisplayResponse(_, Some(_))) =>
      registrationDisplayService.cacheDetails(rResponse.value).flatMap { _ =>
        Future.successful(
          Redirect(BusinessDetailsRecoveryController.form(journey)).withSession(
            requestSessionData.sessionWithUserLocationAdded(sessionInfoBasedOnJourney(journey, Some(location)))
          )
        )
      }
    case _ => Future.successful(ServiceUnavailable(errorTemplate()))
  }

  def processing: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      sessionCache.subscriptionStatusOutcome
        .map(_.processedDate)
        .map(processedDate => Ok(subscriptionStatusOutcomeProcessing(None, processedDate)))
  }

  def rejected: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      sessionCache.subscriptionStatusOutcome
        .map(_.processedDate)
        .map(processedDate => Ok(subscriptionStatusOutcomeRejected(None, processedDate)))
  }
}
