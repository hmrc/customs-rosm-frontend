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
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.NameDetailsSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.nameOrganisationForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.nameOrg
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NameOrgController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  sessionCache: SessionCache,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  nameOrgView: nameOrg,
  subscriptionDetailsService: SubscriptionDetailsService,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        subscriptionBusinessService.cachedNameOrganisationViewModel flatMap { maybeCachedNameViewModel =>
          populateOkView(maybeCachedNameViewModel, isInReviewMode = false, journey)
        }
      }
    }

  def reviewForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedNameViewModel flatMap { nameOrgMatchModel =>
        populateOkView(Some(nameOrgMatchModel), isInReviewMode = true, journey)
      }
    }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      nameOrganisationForm.bindFromRequest.fold(formWithErrors => {
        sessionCache.registrationDetails map { registrationDetails =>
          BadRequest(nameOrgView(formWithErrors, registrationDetails, isInReviewMode, journey, appConfig))
        }
      }, formData => storeNameDetails(formData, isInReviewMode, journey))
    }

  private def populateOkView(
    maybeNameViewModel: Option[NameOrganisationMatchModel],
    isInReviewMode: Boolean,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    lazy val form =
      maybeNameViewModel.fold(nameOrganisationForm)(nameOrganisationForm.fill)

    sessionCache.registrationDetails map { registrationDetails =>
      Ok(nameOrgView(form, registrationDetails, isInReviewMode, journey, appConfig))
    }
  }

  private def storeNameDetails(formData: NameOrganisationMatchModel, inReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] = {
    for {
      _ <- subscriptionDetailsService.cacheNameDetails(formData)
      nameIdDetails <- subscriptionDetailsService.cachedNameIdDetails
    } yield {
      (nameIdDetails, inReviewMode) match {
        case (Some(details), true) =>
          subscriptionDetailsService
            .cacheNameIdDetails(NameIdOrganisationMatchModel(formData.name, details.id))
            .map { _ =>
              Redirect(DetermineReviewPageController.determineRoute(journey))
            }
        case (_, _) =>
          Future.successful(
            Redirect(
              subscriptionFlowManager
                .stepInformation(NameDetailsSubscriptionFlowPage)
                .nextPage
                .url
            )
          )
      }
    }
  }.flatMap(identity)
}
