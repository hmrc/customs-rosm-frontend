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
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{ContactDetailsAddressSubscriptionFlowPageGetEori, ContactDetailsAddressSubscriptionFlowPageMigrate}
import uk.gov.hmrc.customs.rosmfrontend.domain.{EtmpOrganisationType, LoggedInUserWithEnrolments}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.AddressDetailsForm.addressDetailsCreateForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.countries.Countries
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.contact_address
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ContactDetailsAddressController @Inject()(
                                                 override val currentApp: Application,
                                                 override val authConnector: AuthConnector,
                                                 subscriptionBusinessService: SubscriptionBusinessService,
                                                 requestSessionData: RequestSessionData,
                                                 sessionCache: SessionCache,
                                                 subscriptionFlowManager: SubscriptionFlowManager,
                                                 subscriptionDetailsService: SubscriptionDetailsService,
                                                 countries: Countries,
                                                 mcc: MessagesControllerComponents,
                                                 contactDetailsAddressView: contact_address,
                                               )(implicit ec: ExecutionContext)
  extends CdsController(mcc) {


  private def populateForm(journey: Journey.Value)(isInReviewMode: Boolean)(implicit request: Request[AnyContent]) = {
    for {
      addressDetails <- subscriptionBusinessService.cachedContactDetailsModel.map{
        _.map(AddressViewModel(_))
      }
    } yield {
      val form =  if (isInReviewMode) {
                    addressDetails.fold(addressDetailsCreateForm())(a => addressDetailsCreateForm().fill(a))
                   } else {
                    addressDetailsCreateForm()
                  }
      val orgType = requestSessionData.userSelectedOrganisationType.map(EtmpOrganisationType(_))
      Ok(contactDetailsAddressView(form, countries.all, orgType, isInReviewMode, journey))
    }
  }

  def createForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request =>
      _: LoggedInUserWithEnrolments =>
        journey match {
          case Journey.Migrate => populateForm(journey)(false)
          case Journey.GetYourEORI => populateForm(journey)(false)
        }
    }

  def reviewForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request =>
      _: LoggedInUserWithEnrolments =>
        journey match {
          case Journey.Migrate => populateForm(journey)(true)
          case Journey.GetYourEORI => populateForm(journey)(true)
        }
    }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request =>
      _: LoggedInUserWithEnrolments =>
        sessionCache.email flatMap { email =>
          addressDetailsCreateForm().bindFromRequest.fold(
            formWithErrors => {
              val orgType = requestSessionData.userSelectedOrganisationType.map(EtmpOrganisationType(_))
               Future.successful( BadRequest(
                  contactDetailsAddressView(formWithErrors, countries.all,orgType, isInReviewMode, journey)
                ))
            },
            formData => {
              journey match {
                case Journey.Migrate =>
                  updateContactDetails(formData, isInReviewMode, journey)
                case _ =>
                  updateContactDetails(formData, isInReviewMode, journey)
              }
            }
          )
        }
    }


  private def updateContactDetails(
                                   formData: AddressViewModel,
                                   inReviewMode: Boolean,
                                   journey: Journey.Value
                                 )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    for {
      mayBeContactDetails <- sessionCache.subscriptionDetails.map(_.contactDetails.map{
        val address = formData.sanitise()
        _.copy(street = Option(address.street),
          city = Option(address.city),
          postcode = address.postcode,
          countryCode = Option(address.countryCode))
       }
      )
    _ <-  subscriptionDetailsService.cacheContactDetails(mayBeContactDetails.getOrElse(throw new IllegalArgumentException("Contact Details Required")))
    } yield {
          (inReviewMode, journey) match {
            case (true, _) =>
              Redirect(DetermineReviewPageController.determineRoute(journey))
            case (_, Journey.GetYourEORI) =>
              Redirect(
                subscriptionFlowManager
                  .stepInformation(ContactDetailsAddressSubscriptionFlowPageGetEori)
                  .nextPage
                  .url
              )
            case (_, _) =>
              Redirect(
                subscriptionFlowManager
                  .stepInformation(ContactDetailsAddressSubscriptionFlowPageMigrate)
                  .nextPage
                  .url
              )
          }
    }

}
