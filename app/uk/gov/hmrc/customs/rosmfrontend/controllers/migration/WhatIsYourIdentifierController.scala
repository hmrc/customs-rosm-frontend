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

import play.api.Application
import play.api.mvc.{Action, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.{DetermineReviewPageController, SecuritySignOutController}
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.WhatIsYourIdentifierControllerFlowPage
import uk.gov.hmrc.customs.rosmfrontend.domain.{CustomsId, _}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.{ninoIdentityForm, utrIdentityForm}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.{what_is_your_nino, what_is_your_utr}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatIsYourIdentifierController @Inject()(
    override val currentApp: Application,
    override val authConnector: AuthConnector,
    mcc: MessagesControllerComponents,
    ninoIdentityView: what_is_your_nino,
    utrIdentityView: what_is_your_utr,
    sessionCache: SessionCache,
    subscriptionFlowManager: SubscriptionFlowManager,
    subscriptionBusinessService: SubscriptionBusinessService,
    subscriptionDetailsHolderService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        {
          handleForm(false, journey)
        }
    }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => loggedInUser: LoggedInUserWithEnrolments =>
        {
          sessionCache.hasNino.flatMap {
            case Some(true) => {
              ninoIdentityForm.bindFromRequest.fold(
                formWithErrors =>
                  Future.successful(
                    BadRequest(
                      ninoIdentityView(formWithErrors,
                                       isInReviewMode = false,
                                       journey)
                    )),
                nino => storeId(nino, isInReviewMode, journey)
              )
            }
            case Some(false) =>
              utrIdentityForm.bindFromRequest.fold(
                formWithErrors =>
                  Future.successful(
                    BadRequest(
                      utrIdentityView(formWithErrors,
                                      isInReviewMode = false,
                                      journey)
                    )),
                utr => storeId(utr, isInReviewMode, journey)
              )
            case _ =>
              Future.successful(
                Redirect(SecuritySignOutController.displayPage(journey)))
          }
        }
    }

  private def storeId(customsId: CustomsId, inReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    subscriptionDetailsHolderService
      .cacheCustomsId(customsId)
      .map(
        _ =>
          if (inReviewMode) {
            Redirect(DetermineReviewPageController.determineRoute(journey))
          } else {
            Redirect(subscriptionFlowManager.stepInformation(WhatIsYourIdentifierControllerFlowPage).nextPage.url)
          }
      )

  def reviewForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
      {
        handleForm(true, journey)
      }
    }


  private def handleForm(isInReviewMode: Boolean, journey: Journey.Value)(implicit request: Request[AnyContent]) = {

     for {
     mayBeHasNino <-  sessionCache.hasNino
     mayBeCustomId <- subscriptionBusinessService.getCachedCustomsId
     } yield {

       (mayBeHasNino, isInReviewMode, mayBeCustomId) match {

         case (Some(true), false, _) =>
           val form = ninoIdentityForm
           Ok(
             ninoIdentityView(form,
               isInReviewMode,
               journey))
         case (Some(false), false, _ ) =>
           val form = utrIdentityForm
           Ok(
             utrIdentityView(form,
               isInReviewMode,
               journey))

         case (Some(true), true, Some(Nino(nino))) =>
           val form = ninoIdentityForm.fill(Nino(nino))
           Ok(
             ninoIdentityView(form,
               isInReviewMode,
               journey))
         case (Some(false), true, Some(Utr(utr)) ) =>
           val form = utrIdentityForm.fill(Utr(utr))
           Ok(
             utrIdentityView(form,
               isInReviewMode,
               journey))

         case (Some(true), true, _) =>
           val form = ninoIdentityForm
           Ok(
             ninoIdentityView(form,
               isInReviewMode,
               journey))
         case (Some(false), true, _ ) =>
           val form = utrIdentityForm
           Ok(
             utrIdentityView(form,
               isInReviewMode,
               journey))
         case _ => Redirect(SecuritySignOutController.displayPage(journey))
       }
     }
  }
}
