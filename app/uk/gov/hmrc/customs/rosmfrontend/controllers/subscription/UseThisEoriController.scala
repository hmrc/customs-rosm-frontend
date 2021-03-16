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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.domain.{Eori, LoggedInUserWithEnrolments}
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.EoriNumberSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.useThisEoriYesNoAnswer
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.models.exceptions.MissingExistingEori
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{EnrolmentStoreProxyService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.{use_this_eori, use_this_eori_different_gg}
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.SignInWithDifferentDetailsController
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UseThisEoriController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionFlowManager: SubscriptionFlowManager,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  enrolmentStoreProxyService: EnrolmentStoreProxyService,
  mcc: MessagesControllerComponents,
  useThisEoriView: use_this_eori,
  useThisEoriDifferentGG: use_this_eori_different_gg
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def display(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionDetailsHolderService.cachedExistingEoriNumber.map { eori =>
        Ok(useThisEoriView(eori.getOrElse(throw MissingExistingEori()), useThisEoriYesNoAnswer, journey))
      }
  }

  def submit(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionDetailsHolderService.cachedExistingEoriNumber.flatMap { mayBeEori =>
        useThisEoriYesNoAnswer()
          .bindFromRequest()
          .fold(
            formWithErrors =>
              Future.successful(
                BadRequest(useThisEoriView(mayBeEori.getOrElse(throw MissingExistingEori()), formWithErrors, journey))
            ),
            validForm =>
              if (validForm.isYes) {
                val eori = mayBeEori.map(Eori).getOrElse(throw MissingExistingEori())
                enrolmentStoreProxyService.isEoriEnrolledWithAnotherGG(eori).flatMap {
                  case true => {
                    Future.successful(Redirect(SignInWithDifferentDetailsController.eoriUsedSignout(journey)))
                  }
                  case false => {
                    subscriptionDetailsHolderService.cacheEoriNumber(eori.id).map { _ =>
                      Redirect(subscriptionFlowManager.stepInformation(EoriNumberSubscriptionFlowPage).nextPage.url)
                    }
                  }
                }
              } else
                Future.successful(
                  Redirect(
                    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.UseThisEoriController
                      .useDifferentGg(journey)
                  )
              )
          )

      }
    }

  def useDifferentGg(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionDetailsHolderService.cachedExistingEoriNumber.map { eori =>
        Ok(useThisEoriDifferentGG(eori.getOrElse(throw MissingExistingEori()), journey))
      }
    }

}
