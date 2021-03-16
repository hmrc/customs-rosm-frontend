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

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.NinoSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.rowIndividualsNinoForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.match_nino_subscription
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HaveNinoSubscriptionController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  matchNinoSubscriptionView: match_nino_subscription,
  subscriptionDetailsHolderService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(Ok(matchNinoSubscriptionView(rowIndividualsNinoForm, journey)))
  }

  def submit(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      rowIndividualsNinoForm.bindFromRequest.fold(
        formWithErrors => Future.successful(BadRequest(matchNinoSubscriptionView(formWithErrors, journey))),
        formData => destinationsByAnswer(formData, journey)
      )
  }

  private def destinationsByAnswer(
    form: NinoMatchModel,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    form.haveNino match {
      case Some(true) =>
        subscriptionDetailsHolderService
          .cacheCustomsId(Nino(form.nino.getOrElse(noNinoException)))
          .map(_ => redirectToNextPage(journey))
      case Some(false) => subscriptionDetailsHolderService.clearCachedCustomsId map (_ => redirectToNextPage(journey))
      case _           => throw new IllegalStateException("No Data from the form")
    }

  private def redirectToNextPage(
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Result =
    Redirect(subscriptionFlowManager.stepInformation(NinoSubscriptionFlowPage).nextPage.url)

  private lazy val noNinoException = throw new IllegalStateException("User selected 'Yes' for Nino but no Nino found")
}
