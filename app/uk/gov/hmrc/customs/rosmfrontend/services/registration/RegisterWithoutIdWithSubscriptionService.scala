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

package uk.gov.hmrc.customs.rosmfrontend.services.registration

import javax.inject.{Inject, Singleton}
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionCreateController
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.ResponseCommon
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.ResponseCommon._
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class RegisterWithoutIdWithSubscriptionService @Inject()(
  registerWithoutIdService: RegisterWithoutIdService,
  sessionCache: SessionCache,
  requestSessionData: RequestSessionData,
  orgTypeLookup: OrgTypeLookup,
  subscriptionCreateController: SubscriptionCreateController
) {

  def rowRegisterWithoutIdWithSubscription(
    loggedInUser: LoggedInUserWithEnrolments,
    journey: Journey.Value,
    internalId: Option[String] = None
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    def isRow = UserLocation.isRow(requestSessionData)

    def applicableForRegistration(rd: RegistrationDetails) =
      rd.safeId.id.isEmpty && isRow && journey.equals(Journey.GetYourEORI)

    sessionCache.registrationDetails flatMap {
      case rd if applicableForRegistration(rd) =>
        rowServiceCall(loggedInUser, journey, internalId)
      case _ => createSubscription(journey)(request)
    }
  }

  def createSubscription(journey: Journey.Value)(implicit request: Request[AnyContent]): Future[Result] =
    subscriptionCreateController.subscribe(journey)(request)

  private def rowServiceCall(
    loggedInUser: LoggedInUserWithEnrolments,
    journey: Journey.Value,
    internalId: Option[String]
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]) = {

    def registerWithoutIdWithSubscription(
      orgType: Option[EtmpOrganisationType],
      regDetails: RegistrationDetails,
      subDetails: SubscriptionDetails
    ) =
      orgType match {
        case Some(NA) =>
          rowIndividualRegisterWithSubscription(
            loggedInUser,
            journey,
            regDetails,
            subDetails,
            internalId,
            requestSessionData.userSelectedOrganisationType
          )
        case _ =>
          rowOrganisationRegisterWithSubscription(
            loggedInUser,
            journey,
            regDetails,
            subDetails,
            internalId,
            requestSessionData.userSelectedOrganisationType
          )
      }

    for {
      orgType <- orgTypeLookup.etmpOrgType
      rd <- sessionCache.registrationDetails
      sd <- sessionCache.subscriptionDetails
      call <- registerWithoutIdWithSubscription(orgType, rd, sd)
    } yield call
  }

  private def rowIndividualRegisterWithSubscription(
    loggedInUser: LoggedInUserWithEnrolments,
    journey: Journey.Value,
    registrationDetails: RegistrationDetails,
    subscriptionDetails: SubscriptionDetails,
    internalId: Option[String] = None,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]) =
    subscriptionDetails.nameDobDetails.map(
      details =>
        registerWithoutIdService
          .registerIndividual(
            IndividualNameAndDateOfBirth(details.firstName, details.middleName, details.lastName, details.dateOfBirth),
            registrationDetails.address,
            subscriptionDetails.contactDetails,
            loggedInUser,
            internalId,
            orgType
          )
          .flatMap {
            case RegisterWithoutIDResponse(ResponseCommon(status, _, _, _), _) if status == StatusOK =>
              subscriptionCreateController.subscribe(journey)(request)
            case _ =>
              throw new RuntimeException("Registration of individual FAILED")
        }
    ) match {
      case Some(f) => f
      case None =>
        throw new IllegalArgumentException("Incorrect argument passed for cache Individual Registration")
    }

  private def rowOrganisationRegisterWithSubscription(
    loggedInUser: LoggedInUserWithEnrolments,
    journey: Journey.Value,
    registrationDetails: RegistrationDetails,
    subscriptionDetails: SubscriptionDetails,
    internalId: Option[String] = None,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]) =
    registerWithoutIdService
      .registerOrganisation(
        subscriptionDetails.name,
        registrationDetails.address,
        subscriptionDetails.contactDetails,
        loggedInUser,
        internalId,
        orgType
      )
      .flatMap {
        case RegisterWithoutIDResponse(ResponseCommon(status, _, _, _), _) if status == StatusOK =>
          subscriptionCreateController.subscribe(journey)(request)
        case _ =>
          throw new RuntimeException("Registration of organisation FAILED")
      }
}
