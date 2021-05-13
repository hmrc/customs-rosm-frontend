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
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.AddressController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.utrForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.match_utr_subscription
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatIsYourUtrSubscriptionController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  requestSessionData: RequestSessionData,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  matchUtrSubscriptionView: match_utr_subscription,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      requestSessionData.userSelectedOrganisationType match {
        case Some(orgType) => Future.successful(Ok(matchUtrSubscriptionView(utrForm, orgType.id, journey)))
        case None          => noOrgTypeSelected
      }
  }

  def submit(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      requestSessionData.userSelectedOrganisationType match {
        case Some(orgType) =>
          utrForm.bindFromRequest.fold(
            formWithErrors =>
              Future.successful(BadRequest(matchUtrSubscriptionView(formWithErrors, orgType.id, journey))),
            formData => destinationsByAnswer(formData.normalize(), journey, orgType)
          )
        case None => noOrgTypeSelected
      }
  }

  private def destinationsByAnswer(form: UtrMatchModel, journey: Journey.Value, orgType: CdsOrganisationType)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    form.id match {
      case Some(utr) if orgType == CdsOrganisationType.Company => cacheNameIdDetails(form, journey)
      case Some(utr) =>
        subscriptionDetailsService.cacheCustomsId(Utr(form.id.getOrElse(noUtrException))).map { _ =>
          Redirect(AddressController.createForm(journey))
        }
      case _ => throw new IllegalStateException("No Data from the form")
    }

  private def cacheNameIdDetails(form: UtrMatchModel, journey: Journey.Value)(
    implicit hc: HeaderCarrier
  ): Future[Result] =
    for {
      optionalName <- subscriptionDetailsService.cachedNameDetails
    } yield {
      (optionalName, form.id) match {
        case (Some(name), Some(id)) => subscriptionDetailsService.cacheNameIdAndCustomsId(name.name, id)
        case _                      => noBusinessNameOrId
      }
      Redirect(AddressController.createForm(journey))
    }

  private lazy val noUtrException = throw new IllegalStateException("User selected 'Yes' for Utr but no Utr found")
  private lazy val noOrgTypeSelected = throw new IllegalStateException("No organisation type selected by user")
  private lazy val noBusinessNameOrId = throw new IllegalStateException("No business name or CustomsId cached")

}
