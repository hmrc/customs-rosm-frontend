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
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.SignInWithDifferentDetailsController
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.domain.{Eori, GroupId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.EoriNumberViewModel
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.SubscriptionForm.eoriNumberForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.models.exceptions.MissingExistingEori
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{
  EnrolmentStoreProxyService,
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration._
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatIsYourEoriController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  enrolmentStoreProxyService: EnrolmentStoreProxyService,
  mcc: MessagesControllerComponents,
  whatIsYourEoriView: what_is_your_eori,
  requestSessionData: RequestSessionData
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] =
    displayPage(journey, false)

  def reviewForm(journey: Journey.Value): Action[AnyContent] =
    displayPage(journey, true)

  private def displayPage(journey: Journey.Value, isInReviewMode: Boolean): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      val groupId = loggedInUser.groupId.getOrElse(throw MissingExistingEori())

      val existingEori = enrolmentStoreProxyService.groupIdEnrolments(GroupId(groupId)).map { enrolments =>
        existingEoriForUserOrGroup(loggedInUser, enrolments)
      }
      existingEori.flatMap {
        case Some(eori) => useExistingEori(eori, journey)
        case None =>
          subscriptionBusinessService.cachedEoriNumber.map(eori => populateView(eori, isInReviewMode, journey))
      }
    }

  private def useExistingEori(eori: Eori, journey: Journey.Value)(implicit headerCarrier: HeaderCarrier) =
    subscriptionDetailsHolderService.cacheExistingEoriNumber(eori.id).map { _ =>
      Redirect(uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.UseThisEoriController.display(journey))
    }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      eoriNumberForm.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(
            BadRequest(
              whatIsYourEoriView(formWithErrors, isInReviewMode, UserLocation.isRow(requestSessionData), journey)
            )
          )
        },
        formData => {
          submitNewDetails(formData, isInReviewMode, journey)
        }
      )
    }

  private def populateView(eoriNumber: Option[String], isInReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Result = {
    val form = eoriNumber.map(EoriNumberViewModel).fold(eoriNumberForm)(eoriNumberForm.fill)
    Ok(whatIsYourEoriView(form, isInReviewMode, UserLocation.isRow(requestSessionData), journey))
  }

  private def submitNewDetails(formData: EoriNumberViewModel, isInReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] = {
    val eori = Eori(formData.eoriNumber)
    enrolmentStoreProxyService.isEoriEnrolledWithAnotherGG(eori).flatMap {
      case true => {
        Future.successful(Redirect(SignInWithDifferentDetailsController.eoriUsed(journey)))
      }
      case false => {
        subscriptionDetailsHolderService.cacheEoriNumber(eori.id).map { _ =>
          if (isInReviewMode) Redirect(DetermineReviewPageController.determineRoute(journey))
          else Redirect(subscriptionFlowManager.stepInformation(EoriNumberSubscriptionFlowPage).nextPage.url)
        }
      }
    }
  }
}
