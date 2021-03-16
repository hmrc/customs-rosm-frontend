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

package uk.gov.hmrc.customs.rosmfrontend.controllers

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.affinityGroup
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YouCannotUseServiceController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  youCantUseService: you_cant_use_service,
  unauthorisedView: unauthorized,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def page(journey: Journey.Value): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway))
      .retrieve(affinityGroup) { ag =>
        Future.successful(Unauthorized(youCantUseService(ag)))
      } recover withAuthRecovery(request)
  }

  def unauthorisedPage(): Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Unauthorized(unauthorisedView()))
  }
}
