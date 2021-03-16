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
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.OrgTypeNotFoundException
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.SubscriptionForm.{
  subscriptionCompanyShortNameForm,
  subscriptionPartnershipShortNameForm
}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BusinessShortNameController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  subscriptionFlowManager: SubscriptionFlowManager,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  businessShortName: business_short_name,
  orgTypeLookup: OrgTypeLookup
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def form(implicit request: Request[AnyContent]) =
    if (requestSessionData.isPartnership) subscriptionPartnershipShortNameForm
    else subscriptionCompanyShortNameForm

  private def populateView(
    companyShortName: Option[BusinessShortName],
    isInReviewMode: Boolean,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    lazy val shortNameForm = companyShortName.map(convertModelToViewModel).fold(form)(form.fill)

    orgTypeLookup.etmpOrgType map {
      case Some(orgType) => Ok(businessShortName(shortNameForm, isInReviewMode, orgType, journey))
      case None          => throw new OrgTypeNotFoundException()
    }
  }

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.companyShortName.flatMap(populateView(_, isInReviewMode = false, journey))
  }

  def reviewForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedCompanyShortName.flatMap(
        name => populateView(Some(name), isInReviewMode = true, journey)
      )
  }

  def submit(isInReviewMode: Boolean = false, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      form.bindFromRequest.fold(
        formWithErrors => {
          orgTypeLookup.etmpOrgType map {
            case Some(orgType) =>
              BadRequest(
                businessShortName(shortNameForm = formWithErrors, isInReviewMode = isInReviewMode, orgType, journey)
              )
            case None => throw new OrgTypeNotFoundException()
          }
        },
        formData => {
          submitNewDetails(formData, isInReviewMode, journey)
        }
      )
    }

  private def submitNewDetails(formData: CompanyShortNameViewModel, isInReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] = {
    val companyShortName = convertViewModelToModel(formData)
    subscriptionDetailsHolderService
      .cacheCompanyShortName(companyShortName)
      .map(
        _ =>
          if (isInReviewMode) {
            Redirect(
              uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController.determineRoute(journey)
            )
          } else {
            Redirect(subscriptionFlowManager.stepInformation(BusinessShortNameSubscriptionFlowPage).nextPage.url)
        }
      )
  }

  private def convertViewModelToModel(viewModel: CompanyShortNameViewModel): BusinessShortName = {
    val shortName = if (viewModel.useShortName == Option(true)) viewModel.shortName else None
    BusinessShortName(shortName)
  }

  private def convertModelToViewModel(model: BusinessShortName): CompanyShortNameViewModel =
    CompanyShortNameViewModel(if (model.shortName.isDefined) Option(true) else Option(false), model.shortName)
}
