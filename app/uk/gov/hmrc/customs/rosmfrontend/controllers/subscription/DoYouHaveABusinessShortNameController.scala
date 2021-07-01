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
import uk.gov.hmrc.customs.rosmfrontend.OrgTypeNotFoundException
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.BusinessShortNameController
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.SubscriptionForm.{subscriptionCompanyShortNameYesNoForm, subscriptionPartnershipShortNameYesNoForm}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DoYouHaveABusinessShortNameController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  subscriptionFlowManager: SubscriptionFlowManager,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  businessShortName: business_short_name_yes_no,
  orgTypeLookup: OrgTypeLookup
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def form(implicit request: Request[AnyContent]) =
    if (requestSessionData.isPartnership)
      subscriptionPartnershipShortNameYesNoForm
    else
      subscriptionCompanyShortNameYesNoForm


  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
    orgTypeLookup.etmpOrgType.map {
      case Some(orgType) => Ok(businessShortName(form, false, orgType, journey))
      case None          => throw new OrgTypeNotFoundException()
    }
  }

  def submit(isInReviewMode: Boolean = false, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      form.bindFromRequest.fold(
        formWithErrors => {
          orgTypeLookup.etmpOrgType map {
            case Some(orgType) =>
              BadRequest(
                businessShortName(formWithErrors, isInReviewMode = isInReviewMode, orgType, journey)
              )
            case None => throw new OrgTypeNotFoundException()
          }
        },
        formData => {
          if (formData.isYes)
            Future.successful(Redirect(BusinessShortNameController.createForm(journey)))
          else
            Future.successful(Redirect(subscriptionFlowManager.stepInformation(BusinessShortNameSubscriptionFlowYesNoPage).nextPage.url))
        }
      )
    }
}
