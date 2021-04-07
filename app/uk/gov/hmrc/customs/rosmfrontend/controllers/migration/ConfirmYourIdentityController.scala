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
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes._
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.confirmIdentityYesNoAnswer
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfirmYourIdentityController @Inject()(
    override val currentApp: Application,
    override val authConnector: AuthConnector,
    mcc: MessagesControllerComponents,
    confirmYourIdentity: confirm_your_identity,
    sessionCache: SessionCache
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        {
          val form = confirmIdentityYesNoAnswer
          Future.successful(
            Ok(confirmYourIdentity(form, isInReviewMode = false, journey)))
        }
    }

  def submit(isInReviewMode: Boolean,
             journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        {
          confirmIdentityYesNoAnswer.bindFromRequest.fold(
            formWithErrors =>
              Future.successful(
                BadRequest(
                  confirmYourIdentity(formWithErrors, isInReviewMode, journey)
                )
            ),
            formData =>
              sessionCache.saveHasNino(formData.isYes).map { _ =>
                if (isInReviewMode)
                  Redirect(WhatIsYourIdentifierController.reviewForm(journey))
                else
                  Redirect(WhatIsYourIdentifierController.form(journey))
            }
          )
        }
    }

  def reviewForm(journey: Journey.Value): Action[AnyContent] = {
    ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        {
          sessionCache.hasNino.map { hasNino =>
            val form =
              confirmIdentityYesNoAnswer.fill(YesNo(hasNino.getOrElse(false)))
            Ok(confirmYourIdentity(form, isInReviewMode = true, journey))
          }
        }
    }
  }

}
