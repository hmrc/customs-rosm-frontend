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

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.SicCodeViewModel
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.SubscriptionForm.sicCodeform
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.sic_code
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SicCodeController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  orgTypeLookup: OrgTypeLookup,
  mcc: MessagesControllerComponents,
  sicCodeView: sic_code,
  requestSessionData: RequestSessionData
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def populateView(sicCode: Option[String], isInReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] = {
    lazy val form = sicCode.map(SicCodeViewModel).fold(sicCodeform)(sicCodeform.fill)
    orgTypeLookup.etmpOrgType map { _ =>
      Ok(
        sicCodeView(
          form,
          isInReviewMode,
          requestSessionData.userSelectedOrganisationType,
          journey,
          requestSessionData.selectedUserLocation
        )
      )
    }
  }

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.cachedSicCode.flatMap(populateView(_, isInReviewMode = false, journey))
  }

  def reviewForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedSicCode.flatMap(
        sic => populateView(Some(sic), isInReviewMode = true, journey)
      )
  }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      sicCodeform.bindFromRequest.fold(
        formWithErrors => {
          orgTypeLookup.etmpOrgType map { orgType =>
            BadRequest(
              sicCodeView(
                formWithErrors,
                isInReviewMode,
                requestSessionData.userSelectedOrganisationType,
                journey,
                requestSessionData.selectedUserLocation
              )
            )
          }
        },
        formData => {
          submitNewDetails(formData.normalize(), isInReviewMode, journey)
        }
      )
    }

  private def stepInformation(
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): SubscriptionFlowInfo =
    subscriptionFlowManager.stepInformation(SicCodeSubscriptionFlowPage)

  private def submitNewDetails(formData: SicCodeViewModel, isInReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    subscriptionDetailsHolderService
      .cacheSicCode(formData.sicCode)
      .map(
        _ =>
          if (isInReviewMode) {
            Redirect(
              uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController.determineRoute(journey)
            )
          } else {
            Redirect(stepInformation(journey).nextPage.url)
        }
      )
}
