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

package uk.gov.hmrc.customs.rosmfrontend.services.subscription

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{ContactDetails, RecipientDetails, SubscriptionDetails}
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CdsSubscriber @Inject()(
  subscriptionService: SubscriptionService,
  sessionCache: SessionCache,
  handleSubscriptionService: HandleSubscriptionService,
  subscriptionDetailsService: SubscriptionDetailsService,
  requestSessionData: RequestSessionData
) {

  def subscribeWithCachedDetails(
    cdsOrganisationType: Option[CdsOrganisationType],
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[SubscriptionResult] = {
    def migrationEoriUK: Future[SubscriptionResult] =
      for {
        subscriptionDetails <- sessionCache.subscriptionDetails
        email <- sessionCache.email
        registerWithEoriAndIdResponse <- sessionCache.registerWithEoriAndIdResponse
        subscriptionResult <- subscriptionService.existingReg(registerWithEoriAndIdResponse, subscriptionDetails, email)
        _ <- onSubscriptionResultForUKSubscribe(
          subscriptionResult,
          registerWithEoriAndIdResponse,
          subscriptionDetails,
          email
        )
      } yield subscriptionResult

    def subscribeEori: Future[SubscriptionResult] =
      for {
        registrationDetails <- sessionCache.registrationDetails
        (subscriptionResult, maybeSubscriptionDetails) <- fetchOtherDetailsFromCacheAndSubscribe(
          registrationDetails,
          cdsOrganisationType,
          journey
        )
        _ <- onSubscriptionResult(subscriptionResult, registrationDetails, maybeSubscriptionDetails)
      } yield {
        subscriptionResult
      }

    def migrationEoriROW: Future[SubscriptionResult] =
      for {
        registrationDetails <- sessionCache.registrationDetails
        subscriptionDetails <- sessionCache.subscriptionDetails
        email <- sessionCache.email
        subscriptionResult <- subscriptionService.subscribeWithMandatoryOnly(
          registrationDetails,
          subscriptionDetails,
          journey,
          Some(email),
          cdsOrganisationType
        )
        _ <- onSubscriptionResultForRowSubscribe(subscriptionResult, registrationDetails, subscriptionDetails, email)
      } yield {
        subscriptionResult
      }

    val isRowF = Future.successful(UserLocation.isRow(requestSessionData))
    val journeyF = Future.successful(journey)
    val cachedCustomsIdF = subscriptionDetailsService.cachedCustomsId

    val result = for {
      isRow <- isRowF
      journey <- journeyF
      customId <- if (isRow) cachedCustomsIdF else Future.successful(None)
    } yield {
      (journey, isRow, customId) match {
        case (Journey.Migrate, true, Some(identifier)) => migrationEoriUK //Has NINO/UTR as identifier UK journey
        case (Journey.Migrate, true, None)             => migrationEoriROW //ROW
        case (Journey.Migrate, false, _)               => migrationEoriUK //UK Journey
        case _                                         => subscribeEori //Journey Get An EORI
      }
    }
    result.flatMap(identity)
  }

  private def fetchOtherDetailsFromCacheAndSubscribe(
    registrationDetails: RegistrationDetails,
    mayBeCdsOrganisationType: Option[CdsOrganisationType],
    journey: Journey.Value
  )(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[(SubscriptionResult, Option[SubscriptionDetails])] =
    for {
      subscriptionDetailsHolder <- sessionCache.subscriptionDetails
      subscriptionResult <- subscriptionService.subscribe(
        registrationDetails,
        subscriptionDetailsHolder,
        mayBeCdsOrganisationType,
        journey
      )
    } yield (subscriptionResult, Some(subscriptionDetailsHolder))

  private def onSubscriptionResult(
    subscriptionResult: SubscriptionResult,
    regDetails: RegistrationDetails,
    subscriptionDetails: Option[SubscriptionDetails]
  )(implicit hc: HeaderCarrier): Future[Unit] =
    subscriptionResult match {
      case success: SubscriptionSuccessful =>
        CdsLogger.info(s"SubscriptionSuccessful=${Journey.GetYourEORI}")
        val safeId = regDetails.safeId
        val contactDetails: Option[ContactDetails] = subscriptionDetails
          .flatMap(_.contactDetails.map(_.contactDetails))
        val contactName = contactDetails.map(_.fullName)
        val cdsFullName = Some(regDetails.name)
        val email = contactDetails.map(_.emailAddress).getOrElse(throw new IllegalStateException("Email required"))
        val mayBeEori = Some(success.eori)

        completeSubscription(
          Journey.GetYourEORI,
          regDetails.name,
          mayBeEori,
          email,
          safeId,
          contactName,
          cdsFullName,
          success.processingDate,
          success.formBundleId,
          success.emailVerificationTimestamp
        )

      case pending: SubscriptionPending =>
        CdsLogger.info(s"SubscriptionPending=${Journey.GetYourEORI}")
        val safeId = regDetails.safeId
        val contactDetails: Option[ContactDetails] = subscriptionDetails
          .flatMap(_.contactDetails.map(_.contactDetails))
        val contactName = contactDetails.map(_.fullName)
        val cdsFullName = Some(regDetails.name)
        val email = contactDetails.map(_.emailAddress).getOrElse(throw new IllegalStateException("Email required"))
        val mayBeEori = None
        completeSubscription(
          Journey.GetYourEORI,
          regDetails.name,
          mayBeEori,
          email,
          safeId,
          contactName,
          cdsFullName,
          pending.processingDate,
          pending.formBundleId,
          pending.emailVerificationTimestamp
        )

      case failed: SubscriptionFailed =>
        sessionCache.saveSubscriptionCreateOutcome(SubscriptionCreateOutcome(failed.processingDate, regDetails.name))
        Future.successful(())
    }

  private def onSubscriptionResultForRowSubscribe(
    subscriptionResult: SubscriptionResult,
    regDetails: RegistrationDetails,
    subDetails: SubscriptionDetails,
    email: String
  )(implicit hc: HeaderCarrier): Future[Unit] =
    subscriptionResult match {
      case success: SubscriptionSuccessful =>
        CdsLogger.info(s"SubscriptionSuccessful=${Journey.Migrate}")
        val contactName = subDetails.contactDetails.map(_.fullName)
        val cdsFullName = Some(regDetails.name)
        completeSubscription(
          Journey.Migrate,
          subDetails.name,
          Some(success.eori),
          email,
          regDetails.safeId,
          contactName,
          cdsFullName,
          success.processingDate,
          success.formBundleId,
          success.emailVerificationTimestamp
        )

      case pending: SubscriptionPending =>
        CdsLogger.info(s"SubscriptionPending=${Journey.Migrate}")
        val contactName = subDetails.contactDetails.map(_.fullName)
        val cdsFullName = Some(regDetails.name)
        completeSubscription(
          Journey.Migrate,
          subDetails.name,
          subDetails.eoriNumber.map(Eori),
          email,
          regDetails.safeId,
          contactName,
          cdsFullName,
          pending.processingDate,
          pending.formBundleId,
          pending.emailVerificationTimestamp
        )
      case failed: SubscriptionFailed =>
        sessionCache.saveSubscriptionCreateOutcome(SubscriptionCreateOutcome(failed.processingDate, regDetails.name))
        Future.successful(())
    }

  private def onSubscriptionResultForUKSubscribe(
    subscriptionResult: SubscriptionResult,
    regDetails: RegisterWithEoriAndIdResponse,
    subDetails: SubscriptionDetails,
    email: String
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    val safeId = regDetails.responseDetail
      .flatMap(_.responseData.map(x => SafeId(x.SAFEID)))
      .getOrElse(throw new IllegalArgumentException("SAFEID Missing"))
    val contactName = regDetails.responseDetail.flatMap(_.responseData.flatMap(_.contactDetail.map(_.contactName)))
    val cdsFullName = regDetails.responseDetail.flatMap(_.responseData.map(_.trader.fullName))

    subscriptionResult match {
      case success: SubscriptionSuccessful =>
        CdsLogger.info(s"SubscriptionSuccessful=${Journey.Migrate}")
        completeSubscription(
          Journey.Migrate,
          subDetails.name,
          Some(success.eori),
          email,
          safeId,
          contactName,
          cdsFullName,
          success.processingDate,
          success.formBundleId,
          success.emailVerificationTimestamp
        )
      case pending: SubscriptionPending =>
        CdsLogger.info(s"SubscriptionPending=${Journey.Migrate}")
        completeSubscription(
          Journey.Migrate,
          subDetails.name,
          subDetails.eoriNumber.map(Eori),
          email,
          safeId,
          contactName,
          cdsFullName,
          pending.processingDate,
          pending.formBundleId,
          pending.emailVerificationTimestamp
        )

      case failed: SubscriptionFailed =>
        sessionCache.saveSubscriptionCreateOutcome(SubscriptionCreateOutcome(failed.processingDate, cdsFullName.getOrElse(subDetails.name)))
        Future.successful(())
    }
  }

  private def completeSubscription(
    journey: Journey.Value,
    name: String,
    mayBeEORI: Option[Eori],
    email: String,
    safeId: SafeId,
    contactName: Option[String],
    cdsFullName: Option[String],
    processingDate: String,
    formBundleId: String,
    emailVerificationTimestamp: Option[DateTime]
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    sessionCache.saveSubscriptionCreateOutcome(SubscriptionCreateOutcome(processingDate, cdsFullName.getOrElse(name), mayBeEORI.map(_.id)))
    val recipientDetails =
      RecipientDetails(journey, email, contactName.getOrElse(""), cdsFullName, Some(processingDate))
    handleSubscriptionService.handleSubscription(
      formBundleId,
      recipientDetails,
      TaxPayerId(safeId.id),
      mayBeEORI,
      emailVerificationTimestamp,
      safeId
    )
  }
}
