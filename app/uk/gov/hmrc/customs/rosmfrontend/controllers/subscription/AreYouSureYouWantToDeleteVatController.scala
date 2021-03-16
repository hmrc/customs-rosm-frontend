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
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.{
  VatDetailsEuConfirmController,
  VatRegisteredEuController
}
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.removeVatYesNoAnswer
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionVatEUDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.are_you_sure_remove_vat

import scala.concurrent.{ExecutionContext, Future}
@Singleton
class AreYouSureYouWantToDeleteVatController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionVatEUDetailsService: SubscriptionVatEUDetailsService,
  mcc: MessagesControllerComponents,
  areYouSureRemoveVatView: are_you_sure_remove_vat,
  subscriptionFlowManager: SubscriptionFlowManager
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(index: Int, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionVatEUDetailsService.vatEuDetails(index) map {
        case Some(vatDetails) =>
          Ok(areYouSureRemoveVatView(removeVatYesNoAnswer, journey, vatDetails, isInReviewMode = false))
        case _ => Redirect(VatDetailsEuConfirmController.createForm(journey))
      }
    }

  def reviewForm(index: Int, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionVatEUDetailsService.vatEuDetails(index) map {
        case Some(vatDetails) =>
          Ok(areYouSureRemoveVatView(removeVatYesNoAnswer, journey, vatDetails, isInReviewMode = true))
        case _ => Redirect(VatDetailsEuConfirmController.reviewForm(journey))
      }
    }

  def submit(index: Int, journey: Journey.Value, isInReviewMode: Boolean): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionVatEUDetailsService.vatEuDetails(index) flatMap {
        case Some(vatDetails) =>
          removeVatYesNoAnswer
            .bindFromRequest()
            .fold(
              formWithErrors =>
                Future
                  .successful(BadRequest(areYouSureRemoveVatView(formWithErrors, journey, vatDetails, isInReviewMode))),
              yesNoAnswer =>
                if (yesNoAnswer.isYes)
                  subscriptionVatEUDetailsService
                    .removeSingleEuVatDetails(vatDetails) flatMap (_ => redirectToVatConfirm(journey, isInReviewMode))
                else redirectToVatConfirm(journey, isInReviewMode)
            )
        case _ => vatNotFound
      }
    }

  private def redirectToVatConfirm(journey: Journey.Value, isInReviewMode: Boolean)(
    implicit request: Request[AnyContent]
  ): Future[Result] =
    subscriptionVatEUDetailsService.cachedEUVatDetails map {
      case Seq() =>
        if (isInReviewMode) {
          Redirect(VatRegisteredEuController.reviewForm(journey))
        } else {
          Redirect(VatRegisteredEuController.createForm(journey))
        }
      case _ =>
        if (isInReviewMode) {
          Redirect(VatDetailsEuConfirmController.reviewForm(journey))
        } else {
          Redirect(VatDetailsEuConfirmController.createForm(journey))
        }
    }

  private def vatNotFound =
    throw new IllegalStateException("Vat details for remove not found")
}
