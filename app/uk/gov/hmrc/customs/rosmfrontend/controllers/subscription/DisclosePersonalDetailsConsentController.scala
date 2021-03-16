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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.EoriConsentSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.domain.{LoggedInUserWithEnrolments, YesNo}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.disclose_personal_details_consent

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DisclosePersonalDetailsConsentController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionDetailsService: SubscriptionDetailsService,
  subscriptionBusinessService: SubscriptionBusinessService,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  disclosePersonalDetailsConsentView: disclose_personal_details_consent,
  subscriptionFlowManager: SubscriptionFlowManager
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(
        Ok(
          disclosePersonalDetailsConsentView(
            isInReviewMode = false,
            disclosePersonalDetailsYesNoAnswerForm,
            isIndividualFlow,
            requestSessionData.isPartnership,
            journey
          )
        )
      )
  }

  def reviewForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        isConsentDisclosed <- subscriptionBusinessService.getCachedPersonalDataDisclosureConsent
        yesNo: YesNo = YesNo(isConsentDisclosed)
      } yield {
        Ok(
          disclosePersonalDetailsConsentView(
            isInReviewMode = true,
            disclosePersonalDetailsYesNoAnswerForm.fill(yesNo),
            isIndividualFlow,
            requestSessionData.isPartnership,
            journey
          )
        )
      }
  }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      disclosePersonalDetailsYesNoAnswerForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful(
              BadRequest(
                disclosePersonalDetailsConsentView(
                  isInReviewMode,
                  formWithErrors,
                  isIndividualFlow,
                  requestSessionData.isPartnership,
                  journey
                )
              )
          ),
          yesNoAnswer => {
            subscriptionDetailsService.cacheConsentToDisclosePersonalDetails(yesNoAnswer).flatMap { _ =>
              if (isInReviewMode) {
                Future.successful(Redirect(DetermineReviewPageController.determineRoute(journey).url))
              } else {
                Future.successful(
                  Redirect(subscriptionFlowManager.stepInformation(EoriConsentSubscriptionFlowPage).nextPage.url)
                )
              }
            }
          }
        )
    }

  private def isIndividualFlow(implicit rq: Request[AnyContent]) =
    subscriptionFlowManager.currentSubscriptionFlow.isIndividualFlow
}
