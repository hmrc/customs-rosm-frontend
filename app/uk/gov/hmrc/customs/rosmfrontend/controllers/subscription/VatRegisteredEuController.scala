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
  VatDetailsEuConfirmController,
  VatDetailsEuController
}
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{
  SubscriptionPage,
  VatEUConfirmSubscriptionFlowPage,
  VatRegisteredEuSubscriptionFlowPage
}
import uk.gov.hmrc.customs.rosmfrontend.domain.{LoggedInUserWithEnrolments, YesNo}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.VatEUDetailsModel
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService,
  SubscriptionVatEUDetailsService
}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.vat_registered_eu

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatRegisteredEuController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionDetailsService: SubscriptionDetailsService,
  subscriptionVatEUDetailsService: SubscriptionVatEUDetailsService,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  vatRegisteredEuView: vat_registered_eu,
  subscriptionFlowManager: SubscriptionFlowManager
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(
        Ok(
          vatRegisteredEuView(
            isInReviewMode = false,
            vatRegisteredEuYesNoAnswerForm(requestSessionData.isPartnership),
            isIndividualFlow,
            requestSessionData.isPartnership,
            journey
          )
        )
      )
  }

  def reviewForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedVatRegisteredEu map { isVatRegisteredEu =>
        Ok(
          vatRegisteredEuView(
            isInReviewMode = true,
            vatRegisteredEuYesNoAnswerForm(requestSessionData.isPartnership).fill(YesNo(isVatRegisteredEu)),
            isIndividualFlow,
            requestSessionData.isPartnership,
            journey
          )
        )
      }
  }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      vatRegisteredEuYesNoAnswerForm(requestSessionData.isPartnership)
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful(
              BadRequest(
                vatRegisteredEuView(
                  isInReviewMode,
                  formWithErrors,
                  isIndividualFlow,
                  requestSessionData.isPartnership,
                  journey
                )
              )
          ),
          yesNoAnswer => {
            subscriptionDetailsService.cacheVatRegisteredEu(yesNoAnswer).flatMap { _ =>
              redirect(isInReviewMode, yesNoAnswer, journey)
            }
          }
        )
    }

  private def redirect(isInReviewMode: Boolean, yesNoAnswer: YesNo, journey: Journey.Value)(
    implicit rq: Request[AnyContent]
  ): Future[Result] =
    subscriptionVatEUDetailsService.cachedEUVatDetails flatMap { cachedEuVatDetails =>
      (isInReviewMode, yesNoAnswer.isYes) match {
        case (true, true) if cachedEuVatDetails.isEmpty =>
          Future.successful(Redirect(VatDetailsEuController.reviewForm(journey)))
        case (true, true)          => Future.successful(Redirect(VatDetailsEuConfirmController.reviewForm(journey)))
        case (inReviewMode, false) => redirectForNoAnswer(journey, inReviewMode)
        case (false, true)         => redirectForYesAnswer(journey, cachedEuVatDetails, isInReviewMode)
      }
    }

  private def redirectForYesAnswer(
    journey: Journey.Value,
    cachedEuVatDetails: Seq[VatEUDetailsModel],
    isInReviewMode: Boolean
  )(implicit rq: Request[AnyContent]): Future[Result] =
    cachedEuVatDetails.isEmpty match {
      case true => redirectWithFlowManager(VatRegisteredEuSubscriptionFlowPage)
      case _ =>
        isInReviewMode match {
          case false => Future.successful(Redirect(VatDetailsEuConfirmController.createForm(journey)))
          case _     => Future.successful(Redirect(VatDetailsEuConfirmController.reviewForm(journey)))
        }
    }

  private def redirectForNoAnswer(journey: Journey.Value, isInReviewMode: Boolean)(
    implicit rq: Request[AnyContent]
  ): Future[Result] =
    subscriptionVatEUDetailsService.saveOrUpdate(Seq.empty) flatMap { _ =>
      if (isInReviewMode) Future.successful(Redirect(DetermineReviewPageController.determineRoute(journey).url))
      else redirectWithFlowManager(VatEUConfirmSubscriptionFlowPage)
    }

  private def isIndividualFlow(implicit rq: Request[AnyContent]) =
    subscriptionFlowManager.currentSubscriptionFlow.isIndividualFlow

  private def redirectWithFlowManager(subPage: SubscriptionPage)(implicit rq: Request[AnyContent]) =
    Future.successful(Redirect(subscriptionFlowManager.stepInformation(subPage).nextPage.url))
}
