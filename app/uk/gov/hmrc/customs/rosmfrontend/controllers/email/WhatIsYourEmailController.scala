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

package uk.gov.hmrc.customs.rosmfrontend.controllers.email

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.{CdsController, FeatureFlags}
import uk.gov.hmrc.customs.rosmfrontend.domain.{InternalId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.email.EmailForm.emailForm
import uk.gov.hmrc.customs.rosmfrontend.forms.models.email.{EmailStatus, EmailViewModel}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.Save4LaterService
import uk.gov.hmrc.customs.rosmfrontend.views.html.email._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatIsYourEmailController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  whatIsYourEmailView: what_is_your_email,
  save4LaterService: Save4LaterService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with FeatureFlags {

  private def populateView(
    email: Option[String],
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    lazy val form = email.map(EmailViewModel).fold(emailForm) {
      emailForm.fill
    }
    Future.successful(Ok(whatIsYourEmailView(emailForm = form, journey = journey)))
  }

  def createForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        populateView(None, journey = journey)
      }
    }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
      emailForm.bindFromRequest.fold(formWithErrors => {
        Future.successful(BadRequest(whatIsYourEmailView(emailForm = formWithErrors, journey = journey)))
      }, formData => {
        submitNewDetails(InternalId(userWithEnrolments.internalId), formData, journey)
      })
    }

  private def submitNewDetails(internalId: InternalId, formData: EmailViewModel, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    save4LaterService
      .saveEmail(internalId, EmailStatus(formData.email))
      .flatMap(_ => Future.successful(Redirect(routes.CheckYourEmailController.createForm(journey))))
}
