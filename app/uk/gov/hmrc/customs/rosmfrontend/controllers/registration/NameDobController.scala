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

package uk.gov.hmrc.customs.rosmfrontend.controllers.registration

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc.{Action, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.enterNameDobForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_namedob
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NameDobController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  matchNameDobView: match_namedob,
  cdsFrontendDataCache: SessionCache
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        val form = enterNameDobForm
        Future.successful(Ok(matchNameDobView(form, organisationType, journey)))
      }
    }

  def submit(organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        enterNameDobForm.bindFromRequest.fold(
          formWithErrors => Future.successful(BadRequest(matchNameDobView(formWithErrors, organisationType, journey))),
          formData => {
            submitNewDetails(formData, organisationType, journey)
          }
        )
      }
    }

  private def submitNewDetails(formData: NameDobMatchModel, organisationType: String, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    cdsFrontendDataCache.saveSubscriptionDetails(SubscriptionDetails(nameDobDetails = Some(formData))).map { _ =>
      Redirect(
        uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.GYEHowCanWeIdentifyYouController
          .form(organisationType, journey)
      )
    }
}
