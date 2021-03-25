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

import org.joda.time.DateTime
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.SubscriptionCreateController
import uk.gov.hmrc.customs.rosmfrontend.domain.RegisterWithEoriAndIdResponse._
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.forms.FormUtils.dateTimeFormat
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.registration.{MatchingService, RegisterWithEoriAndIdService}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription._
import uk.gov.hmrc.customs.rosmfrontend.views.html.error_template
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription._
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class RegisterWithEoriAndIdController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  requestSessionData: RequestSessionData,
  sessionCache: SessionCache,
  registerWithEoriAndIdService: RegisterWithEoriAndIdService,
  matchingService: MatchingService,
  cdsSubscriber: CdsSubscriber,
  subscriptionStatusService: SubscriptionStatusService,
  subscriptionDetailsService: SubscriptionDetailsService,
  mcc: MessagesControllerComponents,
  subscriptionStatusOutcomeProcessingView: subscription_status_outcome_processing,
  subscriptionStatusOutcomeRejectedView: subscription_status_outcome_rejected,
  errorTemplateView: error_template,
  subscriptionOutcomePendingView: subscription_outcome_pending,
  subscriptionOutcomeFailView: subscription_outcome_fail,
  registerWithEoriAndIdEoriAlreadyLinked: register_with_eori_and_id_eori_already_linked,
  taxEnrolmentsService: TaxEnrolmentsService,
  notifyRcmService: NotifyRcmService
) extends CdsController(mcc) {

  def registerWithEoriAndId(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit loggedInUser =>
      sendRequest().flatMap {
        case true if isRow => handleRowResponse(journey)
        case true          => handleRegisterWithEoriAndIdResponse(journey)
        case false =>
          CdsLogger.error("RegisterWithEoriAndId BadRequest ROW")
          val formattedDate = dateTimeFormat.print(DateTime.now())
          Future.successful(Redirect(RegisterWithEoriAndIdController.fail(formattedDate)))
      }
    }

  private def sendRequest()(
    implicit request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments
  ): Future[Boolean] = {
    for {
      regDetails <- sessionCache.registrationDetails
      cachedCustomsId <- subscriptionDetailsService.cachedCustomsId
    } yield {
      (regDetails, cachedCustomsId, isRow) match {
        case (_: RegistrationDetailsOrganisation, Some(_), true) =>
          registerWithEoriAndIdService.sendOrganisationRequest
        case (_: RegistrationDetailsOrganisation, None, true) =>
          matchingService.sendOrganisationRequestForMatchingService
        case (_: RegistrationDetailsOrganisation, _, false) =>
          registerWithEoriAndIdService.sendOrganisationRequest
        case (_: RegistrationDetailsIndividual, Some(_), true) =>
          registerWithEoriAndIdService.sendIndividualRequest
        case (_: RegistrationDetailsIndividual, None, true) =>
          matchingService.sendIndividualRequestForMatchingService
        case _ => registerWithEoriAndIdService.sendIndividualRequest
      }
    }
  }.flatMap(identity)

  private def handleRowResponse(journey: Journey.Value)(
    implicit request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    hc: HeaderCarrier
  ): Future[Result] = subscriptionDetailsService.cachedCustomsId flatMap {
    case Some(_) => handleRegisterWithEoriAndIdResponse(journey)
    case _       => handleRegisterWithIDResponse(journey)
  }

  private def handleRegisterWithIDResponse(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], loggedInUser: LoggedInUserWithEnrolments) =
    sessionCache.registrationDetails.flatMap { regDetails =>
      onRegistrationPassCheckSubscriptionStatus(journey, "taxPayerID", regDetails.sapNumber.mdgTaxPayerId)
    }

  private def handleRegisterWithEoriAndIdResponse(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], loggedInUser: LoggedInUserWithEnrolments) =
    sessionCache.registerWithEoriAndIdResponse.flatMap { resp =>
      resp.responseDetail.flatMap(_.outcome) match {
        case Some("PASS") =>
          val safeId = resp.responseDetail
            .flatMap(_.responseData.map(x => x.SAFEID))
            .getOrElse(throw new IllegalStateException("SafeId can't be none"))
          onRegistrationPassCheckSubscriptionStatus(journey, idType = "SAFE", id = safeId)
        case Some("DEFERRED") =>
          notifyRcmService.notifyRcm().map { _ =>
            val formattedDate =
              dateTimeFormat.print(resp.responseCommon.processingDate)
            Redirect(RegisterWithEoriAndIdController.pending(formattedDate))
          }
        case Some("FAIL") =>
          val formattedDate =
            dateTimeFormat.print(resp.responseCommon.processingDate)
          Future.successful(Redirect(RegisterWithEoriAndIdController.fail(formattedDate)))
        case None =>
          handleErrorCodes(journey, resp)
        case _ =>
          CdsLogger.error("Unknown RegistrationDetailsOutCome ")
          throw new IllegalStateException("Unknown RegistrationDetailsOutCome")
      }
    }

  def processing: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- cachedName
        processedDate <- sessionCache.subscriptionStatusOutcome.map(_.processedDate)
      } yield Ok(subscriptionStatusOutcomeProcessingView(Some(name), processedDate))
  }

  def rejected: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- cachedName
        processedDate <- sessionCache.subscriptionStatusOutcome.map(_.processedDate)
      } yield Ok(subscriptionStatusOutcomeRejectedView(Some(name), processedDate))
  }

  def pending(date: String): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        eori <- sessionCache.subscriptionDetails.map(
          _.eoriNumber.getOrElse(throw new IllegalStateException("No EORI found in cache"))
        )
        name <- sessionCache.subscriptionDetails.map(_.name)
        _ <- sessionCache.remove
      } yield Ok(subscriptionOutcomePendingView(eori, date, name))
    }

  def fail(date: String): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- sessionCache.subscriptionDetails.map(_.name)
        _ <- sessionCache.remove
      } yield Ok(subscriptionOutcomeFailView(date, name))
    }

  def eoriAlreadyLinked(journey: Journey.Value, isIndividual: Boolean, hasUtr: Boolean): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- sessionCache.subscriptionDetails.map(_.name)
        date <- sessionCache.registerWithEoriAndIdResponse.map(_.responseCommon.processingDate)
      } yield Ok(registerWithEoriAndIdEoriAlreadyLinked(journey, name, date, isIndividual, hasUtr))
    }

  def rejectedPreviously(): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- sessionCache.subscriptionDetails.map(_.name)
        date <- sessionCache.registerWithEoriAndIdResponse.map(
          r => dateTimeFormat.print(r.responseCommon.processingDate)
        )
        _ <- sessionCache.remove
      } yield Ok(subscriptionStatusOutcomeRejectedView(Some(name), date))
    }

  private def handleErrorCodes(journey: Journey.Value, response: RegisterWithEoriAndIdResponse)(
    implicit request: Request[AnyContent]
  ): Future[Result] = {
    val statusText = response.responseCommon.statusText
    val (hasUtr, isIndividual) = response.additionalInformation match {
      case Some(info) =>
        (info.id, info.isIndividual) match {
          case (_: Utr, true) => (true, true)
          case (_, true)      => (false, true)
          case (_, _)         => (false, false)
        }
      case _ => (false, false)
    }

    statusText match {
      case _ if statusText.exists(_.equalsIgnoreCase(EoriAlreadyLinked)) => {
        CdsLogger.warn("Register with Eori and Id EoriAlreadyLinked")
        Future.successful(Redirect(RegisterWithEoriAndIdController.eoriAlreadyLinked(journey, isIndividual, hasUtr)))
      }
      case _ if statusText.exists(_.equalsIgnoreCase(IDLinkedWithEori)) => {
        CdsLogger.warn("Register with Eori and Id IDLinkedWithEori")
        Future.successful(Redirect(RegisterWithEoriAndIdController.eoriAlreadyLinked(journey, isIndividual, hasUtr)))
      }
      case _ if statusText.exists(_.equalsIgnoreCase(RejectedPreviouslyAndRetry)) =>
        Future.successful(Redirect(RegisterWithEoriAndIdController.rejectedPreviously()))
      case _ => {
        CdsLogger.warn(s"Register with Eori and Id Unknown $statusText")
        Future.successful(ServiceUnavailable(errorTemplateView()))
      }
    }
  }

  private def onSuccessfulSubscriptionStatusSubscribe(journey: Journey.Value)(
    implicit request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    hc: HeaderCarrier
  ): Future[Result] = {
    val internalId = InternalId(loggedInUser.internalId)
    val groupId = GroupId(loggedInUser.groupId)
    cdsSubscriber
      .subscribeWithCachedDetails(requestSessionData.userSelectedOrganisationType, journey)
      .flatMap {
        case _: SubscriptionSuccessful => {
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId)
            .map(_ => Redirect(SubscriptionCreateController.migrationEnd()))
        }
        case sp: SubscriptionPending => {
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId)
            .map(_ => Redirect(RegisterWithEoriAndIdController.pending(sp.processingDate)))
        }
        case sf: SubscriptionFailed => {
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId)
            .map(_ => Redirect(RegisterWithEoriAndIdController.fail(sf.processingDate)))
        }
      }
  }

  private def onRegistrationPassCheckSubscriptionStatus(
    journey: Journey.Value,
    idType: String,
    id: String
  )(implicit request: Request[AnyContent], loggedInUser: LoggedInUserWithEnrolments, hc: HeaderCarrier) =
    subscriptionStatusService.getStatus(idType, id).flatMap {
      case NewSubscription | SubscriptionRejected =>
        onSuccessfulSubscriptionStatusSubscribe(journey)
      case SubscriptionProcessing =>
        Future
          .successful(Redirect(RegisterWithEoriAndIdController.processing()))
      case SubscriptionExists => handleExistingSubscription(journey)
    }

  private def handleExistingSubscription(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    Future.successful(Redirect(SubscriptionRecoveryController.complete(journey)))

  private def cachedName(implicit request: Request[AnyContent]) =
    if (isRow) sessionCache.registrationDetails.map(_.name)
    else sessionCache.subscriptionDetails.map(_.name)

  private def isRow(implicit request: Request[AnyContent]) =
    UserLocation.isRow(requestSessionData)
}
