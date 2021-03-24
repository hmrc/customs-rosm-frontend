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
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{ContactDetailsAddressSubscriptionFlowPageGetEori, ContactDetailsAddressSubscriptionFlowPageMigrate, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori, ContactDetailsIsThisRightAddressSubscriptionFlowPageMigrate, ContactDetailsSubscriptionFlowPageMigrate}
import uk.gov.hmrc.customs.rosmfrontend.domain.{LoggedInUserWithEnrolments, YesNo}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.isThisRightContactAddressYesNoAnswer
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.{AddressViewModel, ContactDetailsModel}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.contact_is_right_address
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class ContactDetailsIsRightAddressController @Inject()(
    override val currentApp: Application,
    override val authConnector: AuthConnector,
    subscriptionBusinessService: SubscriptionBusinessService,
    sessionCache: SessionCache,
    subscriptionFlowManager: SubscriptionFlowManager,
    subscriptionDetailsService: SubscriptionDetailsService,
    mcc: MessagesControllerComponents,
    isRightAddressView: contact_is_right_address
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def populateForm(journey: Journey.Value)(
      isInReviewMode: Boolean)(implicit request: Request[AnyContent]) = {
    for {
      contactDetails <- subscriptionBusinessService.cachedContactDetailsModel
      addressPopulated <- updateIsThisRightAddress(journey, isInReviewMode, contactDetails)
    } yield {
      val form = {
        if (isInReviewMode) {
          contactDetails
            .flatMap(_.useAddressFromRegistrationDetails.map(x => x))
            .fold(
              isThisRightContactAddressYesNoAnswer()
            )(f => isThisRightContactAddressYesNoAnswer().fill(YesNo(f)))
        } else {
          isThisRightContactAddressYesNoAnswer()
        }
      }
      Ok(isRightAddressView(form, addressPopulated, isInReviewMode, journey))
    }
  }

  private def updateIsThisRightAddress(
      journey: Journey.Value,isInReviewMode: Boolean, contactDetails : Option[ContactDetailsModel]
  )(implicit request: Request[AnyContent]): Future[AddressViewModel] =
    if (isInReviewMode) {
      Future.fromTry {
        Try{
          contactDetails.map(AddressViewModel(_)).getOrElse(throw new IllegalStateException(
            "No addressViewModel details found in cache"))
        }
      }
    } else {
      journey match {
        case Journey.GetYourEORI =>
          sessionCache.registrationDetails.map(rd => AddressViewModel(rd.address))
        case Journey.Migrate =>
          sessionCache.subscriptionDetails.map(
            _.addressDetails match {
              case Some(addressViewModel) => addressViewModel
              case _ => throw new IllegalStateException(
                "No addressViewModel details found in cache")
            })
      }
    }


  def createForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        journey match {
          case Journey.Migrate =>
            populateForm(journey)(false)
          case Journey.GetYourEORI =>
            populateForm(journey)(false)
        }
    }

  def reviewForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        journey match {
          case Journey.Migrate =>
            populateForm(journey)(true)
          case Journey.GetYourEORI =>
            populateForm(journey)(true)
        }
    }

  def submit(isInReviewMode: Boolean,
             journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        isThisRightContactAddressYesNoAnswer().bindFromRequest.fold(
          formWithErrors => {
            getAddressFromCachedContactDetails(journey).map { contactDetails =>
              BadRequest(
                isRightAddressView(formWithErrors,
                                   contactDetails,
                                   isInReviewMode,
                                   journey)
              )
            }
          },
          formData => {
            journey match {
              case Journey.Migrate =>
                storeContactDetails(formData, isInReviewMode, journey)
              case Journey.GetYourEORI =>
                storeContactDetails(formData, isInReviewMode, journey)
            }
          }
        )
    }

  private def getAddressFromCachedContactDetails(
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
          getAddress(journey)
      }
    }


  private def getAddress(journey: Journey.Value)(implicit request: Request[AnyContent]) = {
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

  private def updateContactDetails(
                                    useThisAddress: Boolean,
                                   inReviewMode: Boolean,
                                   journey: Journey.Value
                                 )(implicit hc: HeaderCarrier, request: Request[AnyContent])= {
  {
    for {
      mayBeContactDetails <- sessionCache.subscriptionDetails.map(
        _.contactDetails)
      useAddress <- getAddress(journey)
    } yield {
      val contactDetails = mayBeContactDetails
        .map {
          cd =>
          if (useThisAddress) {
            cd.copy(useAddressFromRegistrationDetails = Some(useThisAddress),
              street = Option(useAddress.street),
              city = Option(useAddress.city),
              postcode = useAddress.postcode,
              countryCode = Option(useAddress.countryCode)
            )
          } else {
            cd.copy(useAddressFromRegistrationDetails = Some(useThisAddress))
          }
        }
        .getOrElse(
          throw new IllegalStateException("contactDetails not found in cache"))
      subscriptionDetailsService
        .cacheContactDetails(
          contactDetails,
          isInReviewMode = inReviewMode
        )
    }
   }.flatMap(identity)
  }

  private def storeContactDetails(
      formData: YesNo,
      inReviewMode: Boolean,
      journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    (inReviewMode, journey, formData.isYes) match {
      case (true, Journey.GetYourEORI, false) =>
        updateContactDetails(false, inReviewMode, journey).map { _ =>
        val reviewLink  = subscriptionFlowManager
          .stepInformation(
            ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori)
          .nextPage
          .url
          Redirect(
            s"$reviewLink/review"
          )
        }
      case (true, Journey.Migrate, false) =>
        updateContactDetails(false, inReviewMode, journey).map { _ =>
          val reviewLink  =subscriptionFlowManager
            .stepInformation(ContactDetailsIsThisRightAddressSubscriptionFlowPageMigrate)
            .nextPage
            .url
          Redirect(
            s"$reviewLink/review"
          )
        }
      case (true, _, true) =>
        Future.successful(Redirect(DetermineReviewPageController.determineRoute(journey)))
      case (_, Journey.GetYourEORI, true) =>
        updateContactDetails(true, inReviewMode, journey).map { _ =>
          Redirect(
            subscriptionFlowManager
              .stepInformation(
                ContactDetailsAddressSubscriptionFlowPageGetEori)
              .nextPage
              .url
          )
        }

      case (_, Journey.GetYourEORI, false) =>
        updateContactDetails(false, inReviewMode, journey).map { _ =>
          Redirect(
            subscriptionFlowManager
              .stepInformation(
                ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori)
              .nextPage
              .url
          )
        }
      case (_, Journey.Migrate, true) =>
        updateContactDetails(true, inReviewMode, journey).map { _ =>
          Redirect(
            subscriptionFlowManager
              .stepInformation(ContactDetailsAddressSubscriptionFlowPageMigrate)
              .nextPage
              .url
          )
        }
      case (_, Journey.Migrate, false) =>
        updateContactDetails(false, inReviewMode, journey).map { _ =>
          Redirect(
            subscriptionFlowManager
              .stepInformation(ContactDetailsIsThisRightAddressSubscriptionFlowPageMigrate)
              .nextPage
              .url
          )
        }

    }
  }
}
