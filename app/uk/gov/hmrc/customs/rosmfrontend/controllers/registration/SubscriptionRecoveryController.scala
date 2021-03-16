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

import org.joda.time.{DateTime, LocalDate}
import play.api.Application
import play.api.mvc.{Action, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.connector.SubscriptionDisplayConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.SubscriptionDisplayResponse
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{RecipientDetails, SubscriptionDetails}
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.RandomUUIDGenerator
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{
  HandleSubscriptionService,
  SubscriptionDetailsService,
  TaxEnrolmentsService,
  UpdateVerifiedEmailService
}
import uk.gov.hmrc.customs.rosmfrontend.views.html.error_template
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@Singleton
class SubscriptionRecoveryController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  handleSubscriptionService: HandleSubscriptionService,
  taxEnrolmentService: TaxEnrolmentsService,
  sessionCache: SessionCache,
  subscriptionDisplayConnector: SubscriptionDisplayConnector,
  mcc: MessagesControllerComponents,
  errorTemplateView: error_template,
  uuidGenerator: RandomUUIDGenerator,
  requestSessionData: RequestSessionData,
  subscriptionDetailsService: SubscriptionDetailsService,
  updateVerifiedEmailService: UpdateVerifiedEmailService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def complete(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      {
        val isRowF = Future.successful(UserLocation.isRow(requestSessionData))
        val journeyF = Future.successful(journey)
        val cachedCustomsIdF = subscriptionDetailsService.cachedCustomsId
        val result = for {
          isRow <- isRowF
          journey <- journeyF
          customId <- if (isRow) cachedCustomsIdF else Future.successful(None)
        } yield {
          (journey, isRow, customId) match {
            case (Journey.Migrate, true, Some(identifier)) => subscribeForCDS // UK journey
            case (Journey.Migrate, true, None)             => subscribeForCDSROW //subscribeForCDSROW //ROW
            case (Journey.Migrate, false, _)               => subscribeForCDS //UK Journey
            case _                                         => subscribeGetAnEori //Journey Get An EORI
          }
        }
        result.flatMap(identity)
      }
  }

  private def subscribeGetAnEori(implicit ec: ExecutionContext, request: Request[AnyContent]): Future[Result] = {
    val result = for {
      registrationDetails <- sessionCache.registrationDetails
      safeId = registrationDetails.safeId.id
      queryParameters = ("taxPayerID" -> safeId) :: buildQueryParams
      subscriptionDisplayResult <- subscriptionDisplayConnector.subscriptionDisplay(queryParameters)
      subscriptionStatusOutcome <- sessionCache.subscriptionStatusOutcome
    } yield {
      subscriptionDisplayResult match {
        case Right(subscriptionDisplayResponse) =>
          val eori = subscriptionDisplayResponse.responseDetail.EORINo
            .getOrElse(throw new IllegalStateException("no eori found in the response"))
          sessionCache.saveEori(Eori(eori)).flatMap { _ =>
            val mayBeEmail = subscriptionDisplayResponse.responseDetail.contactInformation
              .flatMap(c => c.emailAddress.filter(EmailAddress.isValid(_) && c.emailVerificationTimestamp.isDefined))
            mayBeEmail.map { email =>
              onSubscriptionDisplaySuccess(
                subscriptionStatusOutcome.processedDate,
                email,
                safeId,
                Eori(eori),
                subscriptionDisplayResponse,
                subscriptionDisplayResponse.responseDetail.dateOfEstablishment,
                Journey.GetYourEORI
              )(Redirect(SubscriptionCreateController.end()))
            }.getOrElse {
              CdsLogger.info("Email Missing")
              Future.successful(Redirect(EoriExistsController.eoriExist(Journey.GetYourEORI)))
            }

          }
        case Left(_) =>
          Future.successful(ServiceUnavailable(errorTemplateView()))
      }
    }
    result.flatMap(identity)
  }

  private def subscribeForCDS(
    implicit ec: ExecutionContext,
    request: Request[AnyContent],
    hc: HeaderCarrier
  ): Future[Result] = {
    val result = for {
      subscriptionDetails <- sessionCache.subscriptionDetails
      eori = subscriptionDetails.eoriNumber.getOrElse(throw new IllegalStateException("no eori found in the cache"))
      registerWithEoriAndIdResponse <- sessionCache.registerWithEoriAndIdResponse
      safeId = registerWithEoriAndIdResponse.responseDetail
        .flatMap(_.responseData.map(_.SAFEID))
        .getOrElse(throw new IllegalStateException("no SAFEID found in the response"))
      queryParameters = ("EORI" -> eori) :: buildQueryParams
      subscriptionDisplayResult <- subscriptionDisplayConnector.subscriptionDisplay(queryParameters)
      subscriptionStatusOutcome <- sessionCache.subscriptionStatusOutcome
      email <- sessionCache.email
    } yield {
      subscriptionDisplayResult match {
        case Right(subscriptionDisplayResponse) =>
          onSubscriptionDisplaySuccess(
            subscriptionStatusOutcome.processedDate,
            email,
            safeId,
            Eori(eori),
            subscriptionDisplayResponse,
            getDateOfBirthOrDateOfEstablishment(subscriptionDisplayResponse, subscriptionDetails),
            Journey.Migrate
          )(Redirect(SubscriptionCreateController.migrationEnd()))
        case Left(_) =>
          Future.successful(ServiceUnavailable(errorTemplateView()))
      }
    }
    result.flatMap(identity)
  }

  private def subscribeForCDSROW(implicit ec: ExecutionContext, request: Request[AnyContent]): Future[Result] = {
    val result = for {
      subscriptionDetails <- sessionCache.subscriptionDetails
      registrationDetails <- sessionCache.registrationDetails
      eori = subscriptionDetails.eoriNumber.getOrElse(throw new IllegalStateException("no eori found in the cache"))
      safeId = registrationDetails.safeId.id
      queryParameters = ("EORI" -> eori) :: buildQueryParams
      subscriptionDisplayResult <- subscriptionDisplayConnector.subscriptionDisplay(queryParameters)
      subscriptionStatusOutcome <- sessionCache.subscriptionStatusOutcome
      email <- sessionCache.email
    } yield {
      subscriptionDisplayResult match {
        case Right(subscriptionDisplayResponse) =>
          onSubscriptionDisplaySuccess(
            subscriptionStatusOutcome.processedDate,
            email,
            safeId,
            Eori(eori),
            subscriptionDisplayResponse,
            getDateOfBirthOrDateOfEstablishment(subscriptionDisplayResponse, subscriptionDetails),
            Journey.Migrate
          )(Redirect(SubscriptionCreateController.migrationEnd()))
        case Left(_) =>
          Future.successful(ServiceUnavailable(errorTemplateView()))
      }
    }
    result.flatMap(identity)
  }

  private def buildQueryParams: List[(String, String)] =
    List("regime" -> "CDS", "acknowledgementReference" -> uuidGenerator.generateUUIDAsString)

  private case class SubscriptionInformation(
    processedDate: String,
    email: String,
    emailVerificationTimestamp: Option[DateTime],
    formBundleId: String,
    recipientFullName: String,
    name: String,
    eori: Eori,
    safeId: SafeId,
    dateOfEstablishment: Option[LocalDate]
  )

  private def onSubscriptionDisplaySuccess(
    processedDate: String,
    email: String,
    safeId: String,
    eori: Eori,
    subscriptionDisplayResponse: SubscriptionDisplayResponse,
    dateOfEstablishment: Option[LocalDate],
    journey: Journey.Value
  )(redirect: => Result)(implicit headerCarrier: HeaderCarrier): Future[Result] = {
    val formBundleId =
      subscriptionDisplayResponse.responseCommon.returnParameters
        .flatMap(_.find(_.paramName.equals("ETMPFORMBUNDLENUMBER")).map(_.paramValue))
        .getOrElse(throw new IllegalStateException("NO ETMPFORMBUNDLENUMBER specified"))
    val formBundleIdEnriched = {
      //Multiple enrolment with same formBundleId not possible
      if (journey == Journey.Migrate) s"$formBundleId${Random.nextInt(1000)}cds" else formBundleId
    }
    val recipientFullName =
      subscriptionDisplayResponse.responseDetail.contactInformation.flatMap(_.personOfContact).getOrElse("Customer")
    val name = subscriptionDisplayResponse.responseDetail.CDSFullName
    val emailVerificationTimestamp =
      subscriptionDisplayResponse.responseDetail.contactInformation.flatMap(_.emailVerificationTimestamp)
    val subscriptionInformation = SubscriptionInformation(
      processedDate,
      email,
      emailVerificationTimestamp,
      formBundleIdEnriched,
      recipientFullName,
      name,
      eori,
      SafeId(safeId),
      dateOfEstablishment
    )
    completeEnrolment(journey, subscriptionInformation)(redirect)
  }

  private def completeEnrolment(journey: Journey.Value, subscriptionInformation: SubscriptionInformation)(
    redirect: => Result
  )(implicit hc: HeaderCarrier): Future[Result] =
    for {
      // Update Recovered Subscription Information
      _ <- updateSubscription(subscriptionInformation)
      // Update Email
      _ <- updateEmail(journey, subscriptionInformation)
      // Subscribe Call for enrolment
      _ <- subscribe(journey, subscriptionInformation)
      // Issuer Call for enrolment
      res <- issue(journey, subscriptionInformation)
    } yield {
      res match {
        case NO_CONTENT => redirect
        case _          => throw new IllegalArgumentException("Tax Enrolment issuer call failed")
      }
    }

  private def updateSubscription(subscriptionInformation: SubscriptionInformation)(implicit hc: HeaderCarrier) =
    sessionCache.saveSubscriptionCreateOutcome(
      SubscriptionCreateOutcome(
        subscriptionInformation.processedDate,
        subscriptionInformation.name,
        Some(subscriptionInformation.eori.id)
      )
    )

  private def subscribe(journey: Journey.Value, subscriptionInformation: SubscriptionInformation)(
    implicit hc: HeaderCarrier
  ): Future[Unit] =
    handleSubscriptionService
      .handleSubscription(
        subscriptionInformation.formBundleId,
        RecipientDetails(
          journey,
          subscriptionInformation.email,
          subscriptionInformation.recipientFullName,
          Some(subscriptionInformation.name),
          Some(subscriptionInformation.processedDate)
        ),
        TaxPayerId(subscriptionInformation.safeId.id),
        Some(subscriptionInformation.eori),
        subscriptionInformation.emailVerificationTimestamp,
        subscriptionInformation.safeId
      )

  private def updateEmail(journey: Journey.Value, subscriptionInformation: SubscriptionInformation)(
    implicit hc: HeaderCarrier
  ): Future[Option[Boolean]] =
    if (journey == Journey.Migrate) {
      updateVerifiedEmailService
        .updateVerifiedEmail(newEmail = subscriptionInformation.email, eori = subscriptionInformation.eori.id)
        .map {
          case Some(true) => Some(true)
          case _          => throw new IllegalArgumentException("UpdateEmail failed")
        }
    } else {
      Future.successful(None)
    }

  private def issue(journey: Journey.Value, subscriptionInformation: SubscriptionInformation)(
    implicit hc: HeaderCarrier
  ): Future[Int] =
    if (journey == Journey.Migrate) {
      taxEnrolmentService.issuerCall(
        subscriptionInformation.formBundleId,
        subscriptionInformation.eori,
        subscriptionInformation.dateOfEstablishment
      )
    } else {
      Future.successful(NO_CONTENT)
    }

  private def getDateOfBirthOrDateOfEstablishment(
    response: SubscriptionDisplayResponse,
    subscriptionDetails: SubscriptionDetails
  )(implicit request: Request[AnyContent], headerCarrier: HeaderCarrier): Option[LocalDate] = {
    val isIndividualOrSoleTrader = requestSessionData.isIndividualOrSoleTrader
    val dateOfEstablishment = response.responseDetail.dateOfEstablishment // Date we hold
    val dateOfEstablishmentCaptured = subscriptionDetails.dateEstablished // Captured date
    val dateOfBirthCaptured = subscriptionDetails.dateOfBirth orElse subscriptionDetails.nameDobDetails.map(
      _.dateOfBirth
    ) // Captured date

    (isIndividualOrSoleTrader, dateOfEstablishment, dateOfEstablishmentCaptured, dateOfBirthCaptured) match {
      case (_, Some(date), _, _)     => Some(date)
      case (false, _, Some(date), _) => Some(date)
      case (true, _, _, Some(date))  => Some(date)
      case _                         => throw new IllegalStateException("No DOB/DOE")
    }
  }

}
