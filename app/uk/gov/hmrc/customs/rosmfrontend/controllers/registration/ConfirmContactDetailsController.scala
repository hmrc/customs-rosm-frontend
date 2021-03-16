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
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.registration._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.countries.Countries
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegistrationConfirmService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription._
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.confirm_contact_details
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.{subscription_status_outcome_processing, subscription_status_outcome_rejected}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfirmContactDetailsController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  registrationConfirmService: RegistrationConfirmService,
  requestSessionData: RequestSessionData,
  sessionCache: SessionCache,
  orgTypeLookup: OrgTypeLookup,
  subscriptionFlowManager: SubscriptionFlowManager,
  taxEnrolmentsService: TaxEnrolmentsService,
  countries: Countries,
  mcc: MessagesControllerComponents,
  confirmContactDetailsView: confirm_contact_details,
  subscriptionStatusOutcomeProcessingView: subscription_status_outcome_processing,
  subscriptionStatusOutcomeRejected: subscription_status_outcome_rejected
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit loggedInUser =>
      sessionCache.registrationDetails flatMap {
        case individual: RegistrationDetailsIndividual =>
          if (!individual.address.isValidAddress(countries.all)) {
            CdsLogger.warn("Not a valid Address for Country")
            checkAddressDetails(journey, YesNoWrongAddress(Some("wrong-address")))
          } else {
            Future.successful(
              Ok(
                confirmContactDetailsView(
                  individual.name,
                  concatenateAddress(individual),
                  individual.customsId,
                  None,
                  YesNoWrongAddress.createForm(),
                  journey
                )
              )
            )
          }

        case org: RegistrationDetailsOrganisation =>
          if (!org.address.isValidAddress(countries.all)) {
            CdsLogger.warn("Not a valid Address for Country")
            checkAddressDetails(journey, YesNoWrongAddress(Some("wrong-address")))
          } else {
            orgTypeLookup.etmpOrgType map {
              case Some(ot) =>
                Ok(
                  confirmContactDetailsView(
                    org.name,
                    concatenateAddress(org),
                    org.customsId,
                    Some(ot),
                    YesNoWrongAddress.createForm(),
                    journey
                  )
                )
              case None =>
                CdsLogger.warn("[ConfirmContactDetailsController.form] organisation type None")
                sessionCache.remove
                Redirect(OrganisationTypeController.form(journey))
            }
          }
        case _ =>
          CdsLogger.warn("[ConfirmContactDetailsController.form] registrationDetails not found")
          sessionCache.remove
          Future.successful(Redirect(OrganisationTypeController.form(journey)))
      }
    }

  def submit(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit loggedInUser =>
      YesNoWrongAddress
        .createForm()
        .bindFromRequest()
        .fold(
          formWithErrors => {
            sessionCache.registrationDetails flatMap {
              case individual: RegistrationDetailsIndividual =>
                Future.successful(
                  BadRequest(
                    confirmContactDetailsView(
                      individual.name,
                      concatenateAddress(individual),
                      individual.customsId,
                      None,
                      formWithErrors,
                      journey
                    )
                  )
                )
              case org: RegistrationDetailsOrganisation =>
                orgTypeLookup.etmpOrgType map {
                  case Some(ot) =>
                    BadRequest(
                      confirmContactDetailsView(
                        org.name,
                        concatenateAddress(org),
                        org.customsId,
                        Some(ot),
                        formWithErrors,
                        journey
                      )
                    )
                  case None =>
                    CdsLogger.warn("[ConfirmContactDetailsController.submit] organisation type None")
                    sessionCache.remove
                    Redirect(OrganisationTypeController.form(journey))
                }
              case _ =>
                CdsLogger.warn("[ConfirmContactDetailsController.submit] registrationDetails not found")
                sessionCache.remove
                Future.successful(Redirect(OrganisationTypeController.form(journey)))
            }
          },
          areDetailsCorrectAnswer => {
            checkAddressDetails(journey, areDetailsCorrectAnswer)
          }
        )
    }

  private def checkAddressDetails(
    journey: Journey.Value,
    areDetailsCorrectAnswer: YesNoWrongAddress
  )(implicit request: Request[AnyContent], loggedInUser: LoggedInUserWithEnrolments): Future[Result] =
    sessionCache.subscriptionDetails flatMap (
      subDetails =>
        subDetails.addressDetails match {
          case Some(a) =>
            sessionCache
              .saveSubscriptionDetails(subDetails.copy(addressDetails = Some(a)))
              .flatMap { _ =>
                determineRoute(registrationConfirmService, areDetailsCorrectAnswer.areDetailsCorrect, journey)
              }

          case None =>
            sessionCache.registrationDetails flatMap (
              d =>
                sessionCache
                  .saveSubscriptionDetails(subDetails.copy(addressDetails = Some(concatenateAddress(d))))
                  .flatMap { _ =>
                    determineRoute(registrationConfirmService, areDetailsCorrectAnswer.areDetailsCorrect, journey)
                  }
            )
        }
    )

  def processing: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- sessionCache.registrationDetails.map(_.name)
        processedDate <- sessionCache.subscriptionStatusOutcome.map(_.processedDate)
      } yield Ok(subscriptionStatusOutcomeProcessingView(Some(name), processedDate))
  }

  def rejected: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- sessionCache.registrationDetails.map(_.name)
        processedDate <- sessionCache.subscriptionStatusOutcome.map(_.processedDate)
      } yield Ok(subscriptionStatusOutcomeRejected(Some(name), processedDate))
  }

  private def determineRoute(
    registrationConfirmService: RegistrationConfirmService,
    detailsCorrect: YesNoWrong,
    journey: Journey.Value
  )(implicit request: Request[AnyContent], loggedInUser: LoggedInUserWithEnrolments) =
    detailsCorrect match {
      case Yes =>
        registrationConfirmService.currentSubscriptionStatus flatMap {
          case NewSubscription | SubscriptionRejected =>
            onNewSubscription(journey)
          case SubscriptionProcessing =>
            Future.successful(Redirect(ConfirmContactDetailsController.processing()))
          case SubscriptionExists =>
            handleExistingSubscription(journey: Journey.Value)
          case status =>
            throw new IllegalStateException(s"Invalid subscription status : $status")
        }
      case No =>
        registrationConfirmService
          .clearRegistrationData(loggedInUser)
          .map(
            _ =>
              Redirect(
                uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.OrganisationTypeController
                  .form(journey)
            )
          )
      case WrongAddress =>
        Future.successful(
          Redirect(
            uk.gov.hmrc.customs.rosmfrontend.controllers.routes.AddressController
              .createForm(journey)
          )
        )
      case _ =>
        throw new IllegalStateException(
          ("YesNoWrongAddressForm field somehow had a value that wasn't yes, no, wrong address, or empty ")
        )
    }

  private def onNewSubscription(journey: Journey.Value)(implicit request: Request[AnyContent]): Future[Result] = {
    lazy val noSelectedOrganisationType =
      requestSessionData.userSelectedOrganisationType.isEmpty
    sessionCache.registrationDetails flatMap {
      case _: RegistrationDetailsIndividual if noSelectedOrganisationType =>
        Future.successful(
          Redirect(
            uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.ConfirmIndividualTypeController
              .form(journey)
          )
        )

      case _ =>
        subscriptionFlowManager.startSubscriptionFlow(journey).map {
          case (page, newSession) => Redirect(page.url).withSession(newSession)
        }
    }
  }

  private def handleExistingSubscription(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    sessionCache.registrationDetails.flatMap(
      rd =>
        taxEnrolmentsService.doesEnrolmentExist(rd.safeId).map {
          case true =>
            Redirect(SignInWithDifferentDetailsController.form(journey))
          case false =>
            Redirect(
              SubscriptionRecoveryController
                .complete(journey)
            )
      }
    )

  private def concatenateAddress(registrationDetails: RegistrationDetails): AddressViewModel =
    AddressViewModel(registrationDetails.address)
}
