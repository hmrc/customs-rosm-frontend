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

import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.email.routes.CheckYourEmailController
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{ContactDetailsAddressSubscriptionFlowPageMigrate, ContactDetailsSubscriptionFlowPageGetEori, ContactDetailsSubscriptionFlowPageMigrate}
import uk.gov.hmrc.customs.rosmfrontend.domain.{EtmpOrganisationType, LoggedInUserWithEnrolments, NA}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.ContactPersonViewModel.{fromContactDetailsModel, toContactDetailsModel}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.{AddressViewModel, ContactDetailsModel, ContactPersonViewModel}
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.ContactDetailsForm._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.contact_details
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ContactDetailsController @Inject()(
    override val currentApp: Application,
    override val authConnector: AuthConnector,
    subscriptionBusinessService: SubscriptionBusinessService,
    requestSessionData: RequestSessionData,
    sessionCache: SessionCache,
    subscriptionFlowManager: SubscriptionFlowManager,
    subscriptionDetailsService: SubscriptionDetailsService,
    registrationDetailsService: RegistrationDetailsService,
    mcc: MessagesControllerComponents,
    contactDetailsView: contact_details,
    regDetailsCreator: RegistrationDetailsCreator,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        val orgType = requestSessionData.userSelectedOrganisationType.map(
          EtmpOrganisationType(_))

        journey match {
          case Journey.Migrate =>
            val f = for {
              cachedCustomsId <- orgType match {
                case Some(NA) => subscriptionDetailsService.cachedCustomsId
                case _        => Future.successful(None)
              }
              cachedNameIdDetails <- orgType match {
                case Some(NA) => Future.successful(None)
                case _        => subscriptionDetailsService.cachedNameIdDetails
              }
            } yield {
              (cachedCustomsId, cachedNameIdDetails) match {
                case (None, None) => populateFormGYE(journey)(false)
                case _ =>
                  Future.successful(
                    Redirect(
                      subscriptionFlowManager
                        .stepInformation(
                          ContactDetailsAddressSubscriptionFlowPageMigrate)
                        .nextPage
                        .url
                    )
                  )
              }
            }
            f.flatMap(identity)
          case Journey.GetYourEORI => populateFormGYE(journey)(false)
        }
    }

  def reviewForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        journey match {
          case Journey.Migrate => populateFormGYE(journey)(true)
          case _               => populateFormGYE(journey)(true)
        }
    }

  private def populateFormGYE(journey: Journey.Value)(isInReviewMode: Boolean)(
      implicit request: Request[AnyContent]) = {
    for {
      email <- sessionCache.mayBeEmail
      contactDetails <- subscriptionBusinessService.cachedContactDetailsModel
    } yield {
      if (email.isDefined) {
        populateOkView(contactDetails.map(fromContactDetailsModel),
                       email,
                       isInReviewMode = isInReviewMode,
                       journey)
      } else {
        Future.successful(
          Redirect(CheckYourEmailController.emailConfirmed(journey)))
      }
    }
  }.flatMap(identity)

  def submit(isInReviewMode: Boolean,
             journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        sessionCache.email flatMap { email =>
          contactPersonDetailForm().bindFromRequest.fold(
            formWithErrors => {
              createContactDetails(journey).map { contactDetails =>
                BadRequest(
                  contactDetailsView(formWithErrors,
                    Some(email),
                    isInReviewMode,
                    journey,
                    appConfig)
                )
              }
            },
            formData => {
              journey match {
                case Journey.Migrate =>
                  storeContactDetails(formData, email, isInReviewMode, journey)
                case _ =>
                  storeContactDetails(formData, email, isInReviewMode, journey)
              }
            }
          )
        }
    }

  private def createContactDetails(
      journey: Journey.Value
  )(implicit request: Request[AnyContent]): Future[AddressViewModel] =
    sessionCache.subscriptionDetails flatMap { sd =>
      sd.contactDetails match {
        case Some(contactDetails) =>
          Future.successful(
            AddressViewModel(
              contactDetails.street.getOrElse(""),
              contactDetails.city.getOrElse(""),
              contactDetails.postcode,
              contactDetails.countryCode.getOrElse("")
            )
          )
        case _ =>
          journey match {
            case Journey.GetYourEORI =>
              sessionCache.registrationDetails.map(rd =>
                AddressViewModel(rd.address))
            case _ =>
              subscriptionDetailsService.cachedAddressDetails.map {
                case Some(addressViewModel) => addressViewModel
                case _ =>
                  throw new IllegalStateException(
                    "No addressViewModel details found in cache")
              }
          }
      }
    }

  private def populateOkView(
      contactDetailsModel: Option[ContactPersonViewModel],
      email: Option[String],
      isInReviewMode: Boolean,
      journey: Journey.Value
  )(implicit hc: HeaderCarrier,
    request: Request[AnyContent]): Future[Result] = {

    val form = if (isInReviewMode) {
      contactDetailsModel.fold(contactPersonDetailForm())(c =>
        contactPersonDetailForm().fill(c))
    } else {
      contactPersonDetailForm()
    }

    Future.successful(
      Ok(contactDetailsView(form, email, isInReviewMode, journey, appConfig)))

  }

  private def storeContactDetailsMigrate(
      formData: ContactPersonViewModel,
      email: String,
      isInReviewMode: Boolean,
      journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    for {
      cachedAddressDetails <- subscriptionDetailsService.cachedAddressDetails
      _ <- registrationDetailsService.cacheAddress(
        regDetailsCreator
          .registrationAddressFromAddressViewModel(cachedAddressDetails.get)
      )
    } yield {
      storeContactDetails(formData, email, isInReviewMode, journey)
    }
  }.flatMap(identity)

  private def storeContactDetails(
      formData: ContactPersonViewModel,
      email: String,
      inReviewMode: Boolean,
      journey: Journey.Value
  )(implicit hc: HeaderCarrier,
    request: Request[AnyContent]): Future[Result] = {

    for {
      mayBeContactDetails <- sessionCache.subscriptionDetails.map(
        _.contactDetails)
      contactDetails = mayBeContactDetails
        .map(
          ContactDetailsModel(_, formData.copy(emailAddress = Option(email))))
        .getOrElse(
          toContactDetailsModel(formData.copy(emailAddress = Option(email))))
      _ <- subscriptionDetailsService
        .cacheContactDetails(
          contactDetails
        )
    } yield {
      (inReviewMode, journey) match {
        case (true, _) =>
          Redirect(DetermineReviewPageController.determineRoute(journey))
        case (_, Journey.GetYourEORI) =>
          Redirect(
            subscriptionFlowManager
              .stepInformation(ContactDetailsSubscriptionFlowPageGetEori)
              .nextPage
              .url
          )
        case (_, _) =>
          Redirect(
            subscriptionFlowManager
              .stepInformation(ContactDetailsSubscriptionFlowPageMigrate)
              .nextPage
              .url
          )
      }
    }
  }
}
