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
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.{eori_used, eori_used_signout, sign_in_with_different_details}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SignInWithDifferentDetailsController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  sessionCache: SessionCache,
  signInWithDifferentDetailsView: sign_in_with_different_details,
  eoriUsedSignoutView: eori_used_signout,
  eoriUsedView: eori_used,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      {
        val name = journey match {
          case Journey.GetYourEORI => sessionCache.registrationDetails.map(_.name)
          case Journey.Migrate     => sessionCache.subscriptionDetails.map(_.name)
          case _                   => throw new IllegalArgumentException("No a valid journey")
        }

        name map { n =>
          val optionalName = Option(n) filter (_.nonEmpty)
          Ok(signInWithDifferentDetailsView(optionalName))
        }
      }
  }

  def eoriUsedSignout(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      sessionCache.subscriptionDetails.map(_.existingEoriNumber).map(eori => Ok(eoriUsedSignoutView(journey, eori)))
  }

  def eoriUsed(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      {
        Future.successful(Ok(eoriUsedView(journey, None)))
      }
  }
}
