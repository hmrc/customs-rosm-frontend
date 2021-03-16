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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.{CdsController, FeatureFlags}
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegisterWithoutIdWithSubscriptionService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.check_your_details_register

import scala.concurrent.ExecutionContext

@Singleton
class CheckYourDetailsRegisterController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  sessionCache: SessionCache,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  checkYourDetailsRegisterView: check_your_details_register,
  registerWithoutIdWithSubscription: RegisterWithoutIdWithSubscriptionService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with FeatureFlags {

  def reviewDetails(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        registration <- sessionCache.registrationDetails
        subscription <- sessionCache.subscriptionDetails
      } yield {
        val consent = subscription.personalDataDisclosureConsent.getOrElse(
          throw new IllegalStateException("Consent to disclose personal data is missing")
        )
        val isUserIdentifiedByRegService = registration.safeId.id.nonEmpty
        Ok(
          checkYourDetailsRegisterView(
            requestSessionData.userSelectedOrganisationType,
            requestSessionData.isPartnership,
            registration,
            subscription,
            consent,
            journey,
            isUserIdentifiedByRegService,
            rowHaveUtrEnabled
          )
        )
      }
  }

  def submitDetails(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      registerWithoutIdWithSubscription
        .rowRegisterWithoutIdWithSubscription(loggedInUser, journey, loggedInUser.internalId)
  }
}
