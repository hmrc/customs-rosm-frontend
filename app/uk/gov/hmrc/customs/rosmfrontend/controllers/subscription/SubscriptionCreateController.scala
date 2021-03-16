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
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.SubscriptionCreateResponse._
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription._
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.migration_success
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class SubscriptionCreateController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  requestSessionData: RequestSessionData,
  sessionCache: SessionCache,
  subscriptionDetailsService: SubscriptionDetailsService,
  mcc: MessagesControllerComponents,
  migrationSuccessView: migration_success,
  subscriptionStatusOutcomeView: subscription_status_outcome_processing,
  subscriptionCreateRequestNotProcessed: subscription_create_request_not_processed,
  subscriptionCreateSubscriptionInProgressView: subscription_create_subscription_in_progress,
  subscriptionCreateEoriAlreadyAssociatedView: subscription_create_eori_already_associated,
  subscriptionCreateEoriAlreadyExists: subscription_create_eori_already_exists,
  subscriptionStatusOutcomeRejected: subscription_status_outcome_rejected,
  subscriptionOutcomeView: subscription_outcome,
  subscriptionOutcomeGuidanceView: subscription_outcome_xieori_guidance,
  cdsSubscriber: CdsSubscriber
) extends CdsController(mcc) {

  def subscribe(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      val selectedOrganisationType: Option[CdsOrganisationType] =
        requestSessionData.userSelectedOrganisationType
      val internalId = InternalId(loggedInUser.internalId)
      val groupId = GroupId(loggedInUser.groupId)
      cdsSubscriber
        .subscribeWithCachedDetails(selectedOrganisationType, journey)
        .flatMap { subscribeResult =>
          (subscribeResult, journey) match {
            case (_: SubscriptionSuccessful, Journey.GetYourEORI) => {
              subscriptionDetailsService
                .saveKeyIdentifiers(groupId, internalId)
                .map(_ => Redirect(SubscriptionCreateController.end()))
            }
            case (_: SubscriptionPending, _) => {
              subscriptionDetailsService
                .saveKeyIdentifiers(groupId, internalId)
                .map(_ => Redirect(SubscriptionCreateController.pending()))
            }
            case (SubscriptionFailed(EoriAlreadyExists, _), _) =>
              Future.successful(Redirect(SubscriptionCreateController.eoriAlreadyExists()))
            case (SubscriptionFailed(EoriAlreadyAssociated, _), _) =>
              Future.successful(Redirect(SubscriptionCreateController.eoriAlreadyAssociated()))
            case (SubscriptionFailed(SubscriptionInProgress, _), _) =>
              Future.successful(Redirect(SubscriptionCreateController.subscriptionInProgress()))
            case (SubscriptionFailed(RequestNotProcessed, _), _) =>
              Future.successful(Redirect(SubscriptionCreateController.requestNotProcessed()))
            case (_: SubscriptionFailed, _) =>
              Future.successful(Redirect(SubscriptionCreateController.rejected()))
            case _ =>
              throw new IllegalArgumentException(s"Cannot redirect for subscription with journey: $journey")
          }
        } recoverWith {
        case e: Exception =>
          CdsLogger.error("Subscription Error. ", e)
          Future.failed(new RuntimeException("Subscription Error. ", e))
      }
    }

  def xiEoriGuidance: Action[AnyContent] = Action {
    implicit request =>
        Ok(subscriptionOutcomeGuidanceView())
  }

  def end: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        subscriptionCreateOutcome <- sessionCache.subscriptionCreateOutcome
        _ <- sessionCache.remove
        _ <- sessionCache.saveSubscriptionCreateOutcome(subscriptionCreateOutcome)
      } yield
        Ok(
          subscriptionOutcomeView(
            subscriptionCreateOutcome.eori
              .getOrElse("EORI not populated from subscription create response."),
            subscriptionCreateOutcome.fullName,
            subscriptionCreateOutcome.processedDate
          )
        )
  }

  def migrationEnd: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      if (UserLocation.isRow(requestSessionData)) {
        subscriptionDetailsService.cachedCustomsId flatMap {
          case Some(_) => renderPageWithName
          case _       => renderPageWithNameRow
        }
      } else renderPageWithName
  }

  def rejected: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        subscriptionCreateOutcome <- sessionCache.subscriptionCreateOutcome
        _ <- sessionCache.remove
      } yield Ok(subscriptionStatusOutcomeRejected(Some(subscriptionCreateOutcome.fullName), subscriptionCreateOutcome.processedDate))
  }

  def eoriAlreadyExists: Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        subscriptionCreateOutcome <- sessionCache.subscriptionCreateOutcome
        _ <- sessionCache.remove
      } yield Ok(subscriptionCreateEoriAlreadyExists(subscriptionCreateOutcome.fullName, subscriptionCreateOutcome.processedDate))
    }

  def eoriAlreadyAssociated: Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        subscriptionCreateOutcome <- sessionCache.subscriptionCreateOutcome
        _ <- sessionCache.remove
      } yield Ok(subscriptionCreateEoriAlreadyAssociatedView(subscriptionCreateOutcome.fullName, subscriptionCreateOutcome.processedDate))
    }

  def subscriptionInProgress: Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        subscriptionCreateOutcome <- sessionCache.subscriptionCreateOutcome
        _ <- sessionCache.remove
      } yield Ok(subscriptionCreateSubscriptionInProgressView(subscriptionCreateOutcome.fullName, subscriptionCreateOutcome.processedDate))
    }

  def requestNotProcessed: Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        _ <- sessionCache.remove
      } yield Ok(subscriptionCreateRequestNotProcessed())
    }

  def pending: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        subscriptionCreateOutcome <- sessionCache.subscriptionCreateOutcome
        _ <- sessionCache.remove
      } yield Ok(subscriptionStatusOutcomeView(Some(subscriptionCreateOutcome.fullName), subscriptionCreateOutcome.processedDate))
  }

  private def renderPageWithName(implicit hc: HeaderCarrier, request: Request[_]) =
    for {
      name <- sessionCache.registerWithEoriAndIdResponse.map(
        _.responseDetail.flatMap(_.responseData.map(_.trader.fullName))
      )
      subscriptionCreateOutcome <- sessionCache.subscriptionCreateOutcome
      _ <- sessionCache.remove
      _ <- sessionCache.saveSubscriptionCreateOutcome(
        SubscriptionCreateOutcome(subscriptionCreateOutcome.processedDate, name.getOrElse(""), subscriptionCreateOutcome.eori)
      )
    } yield
      Ok(
        migrationSuccessView(
          subscriptionCreateOutcome.eori,
          name.getOrElse(throw new IllegalStateException("Name not populated from Register with Eori and Id")),
          subscriptionCreateOutcome.processedDate
        )
      )

  private def renderPageWithNameRow(implicit hc: HeaderCarrier, request: Request[_]) =
    for {
      subscriptionCreateOutcome <- sessionCache.subscriptionCreateOutcome
      _ <- sessionCache.remove
      _ <- sessionCache.saveSubscriptionCreateOutcome(subscriptionCreateOutcome)
    } yield Ok(migrationSuccessView(subscriptionCreateOutcome.eori, subscriptionCreateOutcome.fullName, subscriptionCreateOutcome.processedDate))
}
