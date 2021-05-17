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

package uk.gov.hmrc.customs.rosmfrontend.controllers.migration

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.NameDobDetailsSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.enterNameDobForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NameDobSoleTraderController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  requestSessionData: RequestSessionData,
  cdsFrontendDataCache: SessionCache,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  enterYourDetails: enter_your_details,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      {
        subscriptionBusinessService.cachedSubscriptionNameDobViewModel flatMap { maybeCachedNameDobViewModel =>
          val selectedOrganisationType = requestSessionData.userSelectedOrganisationType.map(_.id)
          populateOkView(
            maybeCachedNameDobViewModel,
            selectedOrganisationType.getOrElse(""),
            isInReviewMode = false,
            journey
          )
        }
      }
  }

  def reviewForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedSubscriptionNameDobViewModel flatMap { cdm =>
        val selectedOrganisationType = requestSessionData.userSelectedOrganisationType.map(_.id)
        populateOkView(Some(cdm), selectedOrganisationType.getOrElse(""), isInReviewMode = true, journey)
      }
  }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      enterNameDobForm.bindFromRequest.fold(
        formWithErrors => {
          cdsFrontendDataCache.registrationDetails map { _ =>
            BadRequest(
              enterYourDetails(
                formWithErrors,
                isInReviewMode,
                journey,
                requestSessionData.selectedUserLocationWithIslands,
                appConfig
              )
            )
          }
        },
        formData => {
          storeNameDobDetails(formData, isInReviewMode, journey)
        }
      )
    }

  private def populateOkView(
    nameDobViewModel: Option[NameDobMatchModel],
    organisationType: String,
    isInReviewMode: Boolean,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    lazy val form = nameDobViewModel.fold(enterNameDobForm) {
      enterNameDobForm.fill
    }

    cdsFrontendDataCache.registrationDetails map { _ =>
      Ok(enterYourDetails(form, isInReviewMode, journey, requestSessionData.selectedUserLocationWithIslands, appConfig))
    }
  }

  private def storeNameDobDetails(formData: NameDobMatchModel, inReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    subscriptionDetailsHolderService.cacheNameDobDetails(formData).map { _ =>
      if (inReviewMode) {
        Redirect(
          uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController.determineRoute(journey)
        )
      } else {
        Redirect(subscriptionFlowManager.stepInformation(NameDobDetailsSubscriptionFlowPage).nextPage.url)
      }
    }
}
