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
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.{
  VatDetailsEuController,
  VatRegisteredEuController
}
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails.EuVatDetailsLimit
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.VatEUConfirmSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.domain.{LoggedInUserWithEnrolments, YesNo}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionVatEUDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.vat_details_eu_confirm
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
@Singleton
class VatDetailsEuConfirmController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  vatEUDetailsService: SubscriptionVatEUDetailsService,
  mcc: MessagesControllerComponents,
  vatDetailsEuConfirmView: vat_details_eu_confirm,
  subscriptionFlowManager: SubscriptionFlowManager
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      vatEUDetailsService.cachedEUVatDetails map {
        case Seq() => Redirect(VatRegisteredEuController.createForm(journey))
        case details if details.size < EuVatDetailsLimit =>
          Ok(
            vatDetailsEuConfirmView(
              euVatLimitNotReachedYesNoAnswerForm,
              isInReviewMode = false,
              details,
              journey,
              vatLimitNotReached = true
            )
          )
        case details =>
          Ok(
            vatDetailsEuConfirmView(
              euVatLimitNotReachedYesNoAnswerForm,
              isInReviewMode = false,
              details,
              journey,
              vatLimitNotReached = false
            )
          )
      }
    }

  def reviewForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      vatEUDetailsService.cachedEUVatDetails map {
        case Seq() => Redirect(VatRegisteredEuController.reviewForm(journey))
        case details if details.size < EuVatDetailsLimit =>
          Ok(
            vatDetailsEuConfirmView(
              euVatLimitNotReachedYesNoAnswerForm,
              isInReviewMode = true,
              details,
              journey,
              vatLimitNotReached = true
            )
          )
        case details =>
          Ok(
            vatDetailsEuConfirmView(
              euVatLimitNotReachedYesNoAnswerForm,
              isInReviewMode = true,
              details,
              journey,
              vatLimitNotReached = false
            )
          )
      }
    }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      vatEUDetailsService.cachedEUVatDetails flatMap (
        details =>
          if (details.size < EuVatDetailsLimit)
            underVatLimitSubmit(journey, isInReviewMode)
          else overVatLimitSubmit(journey, isInReviewMode)
      )
    }

  private def underVatLimitSubmit(journey: Journey.Value, isInReviewMode: Boolean)(
    implicit request: Request[AnyContent]
  ): Future[Result] =
    euVatLimitNotReachedYesNoAnswerForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          vatEUDetailsService.cachedEUVatDetails map { details =>
            BadRequest(
              vatDetailsEuConfirmView(
                formWithErrors,
                isInReviewMode = isInReviewMode,
                details,
                journey,
                vatLimitNotReached = true
              )
            )
        },
        yesNoAnswer => Future.successful(redirect(yesNoAnswer, isInReviewMode, journey))
      )

  private def redirect(yesNoAnswer: YesNo, isInReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    rc: Request[AnyContent]
  ): Result =
    (yesNoAnswer.isYes, isInReviewMode) match {
      case (true, false) => Redirect(VatDetailsEuController.createForm(journey))
      case (true, true)  => Redirect(VatDetailsEuController.reviewForm(journey))
      case (false, true) =>
        Redirect(DetermineReviewPageController.determineRoute(journey).url)
      case (false, false) =>
        Redirect(
          subscriptionFlowManager
            .stepInformation(VatEUConfirmSubscriptionFlowPage)
            .nextPage
            .url
        )
    }

  private def overVatLimitSubmit(journey: Journey.Value, isInReviewMode: Boolean)(
    implicit request: Request[AnyContent]
  ): Future[Result] =
    euVatLimitReachedYesNoAnswerForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          vatEUDetailsService.cachedEUVatDetails map { details =>
            BadRequest(
              vatDetailsEuConfirmView(formWithErrors, isInReviewMode, details, journey, vatLimitNotReached = false)
            )
        },
        _ =>
          if (isInReviewMode)
            Future.successful(Redirect(DetermineReviewPageController.determineRoute(journey).url))
          else
            Future.successful(
              Redirect(
                subscriptionFlowManager
                  .stepInformation(VatEUConfirmSubscriptionFlowPage)
                  .nextPage
                  .url
              )
          )
      )
}
