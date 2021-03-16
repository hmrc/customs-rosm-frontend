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

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc.{Action, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.{CdsController, FeatureFlags}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.organisationNameForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.what_is_your_org_name
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatIsYourOrgNameController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  whatIsYourOrgNameView: what_is_your_org_name,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with FeatureFlags {

  private def populateView(
    name: Option[String],
    isInReviewMode: Boolean,
    organisationType: String,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    val form = name.map(n => NameMatchModel(n)).fold(organisationNameForm)(organisationNameForm.fill)
    Future.successful(Ok(whatIsYourOrgNameView(isInReviewMode, form, organisationType, journey)))
  }

  def showForm(isInReviewMode: Boolean = false, organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionDetailsService.cachedNameDetails.flatMap(
        details => populateView(details.map(_.name), isInReviewMode, organisationType, journey)
      )
    }

  def submit(isInReviewMode: Boolean = false, organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      organisationNameForm.bindFromRequest.fold(
        formWithErrors =>
          Future.successful(
            BadRequest(whatIsYourOrgNameView(isInReviewMode, formWithErrors, organisationType, journey))
        ),
        formData => submitOrgNameDetails(isInReviewMode, formData, organisationType, journey)
      )
    }

  private def submitOrgNameDetails(
    isInReviewMode: Boolean,
    formData: NameMatchModel,
    organisationType: String,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    subscriptionDetailsService.cacheNameDetails(NameOrganisationMatchModel(formData.name)) map { _ =>
      if (isInReviewMode) {
        Redirect(DetermineReviewPageController.determineRoute(journey))
      } else {
        if (UserLocation.isRow(requestSessionData)) {
          if (rowHaveUtrEnabled) {
            Redirect(DoYouHaveAUtrNumberController.form(organisationType, journey, false))
          } else {
            Redirect(SixLineAddressController.showForm(false, organisationType, journey))
          }
        } else {
          Redirect(DoYouHaveAUtrNumberController.form(organisationType, journey, false))
        }
      }
    }
}
