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
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Address
import uk.gov.hmrc.customs.rosmfrontend.domain.{LoggedInUser, SixLineAddressMatchModel}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.countries._
import uk.gov.hmrc.customs.rosmfrontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.six_line_address
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SixLineAddressController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  regDetailsCreator: RegistrationDetailsCreator,
  subscriptionFlowManager: SubscriptionFlowManager,
  sessionCache: SessionCache,
  requestSessionData: RequestSessionData,
  countries: Countries,
  mcc: MessagesControllerComponents,
  sixLineAddressView: six_line_address,
  registrationDetailsService: RegistrationDetailsService,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def populateView(
    address: Option[Address],
    isInReviewMode: Boolean,
    organisationType: String,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    val formByOrgType = formsByOrganisationTypes(request)(organisationType)
    lazy val form = address.map(ad => createSixLineAddress(ad)).fold(formByOrgType)(formByOrgType.fill)
    val (countriesToInclude, countriesInCountryPicker) =
      countries.getCountryParameters(requestSessionData.selectedUserLocationWithIslands)
    Future.successful(
      Ok(
        sixLineAddressView(
          isInReviewMode,
          form,
          countriesToInclude,
          countriesInCountryPicker,
          organisationType,
          journey,
          appConfig
        )
      )
    )
  }

  def showForm(isInReviewMode: Boolean = false, organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit loggedInUser: LoggedInUser =>
      assertOrganisationTypeIsValid(organisationType)
      sessionCache.registrationDetails.flatMap(
        rd => populateView(Some(rd.address), isInReviewMode, organisationType, journey)
      )
    }

  def submit(isInReviewMode: Boolean = false, organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit loggedInUser: LoggedInUser =>
      val (countriesToInclude, countriesInCountryPicker) =
        countries.getCountryParameters(requestSessionData.selectedUserLocationWithIslands)
      assertOrganisationTypeIsValid(organisationType)(request)
      formsByOrganisationTypes(request)(organisationType).bindFromRequest.fold(
        invalidForm =>
          Future.successful(
            BadRequest(
              sixLineAddressView(
                isInReviewMode,
                invalidForm,
                countriesToInclude,
                countriesInCountryPicker,
                organisationType,
                journey,
                appConfig
              )
            )
        ),
        formData => submitAddressDetails(isInReviewMode, formData.sanitise(), organisationType, journey)
      )
    }

  private def submitAddressDetails(
    isInReviewMode: Boolean,
    formData: SixLineAddressMatchModel,
    organisationType: String,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    if (isInReviewMode) {
      registrationDetailsService
        .cacheAddress(regDetailsCreator.registrationAddress(formData))
        .map(
          _ =>
            Redirect(
              uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController.determineRoute(journey)
          )
        )
    } else {
      registrationDetailsService.cacheAddress(regDetailsCreator.registrationAddress(formData)).flatMap { _ =>
        subscriptionFlowManager.startSubscriptionFlow(journey)
      } map {
        case (firstSubscriptionPage, session) => Redirect(firstSubscriptionPage.url).withSession(session)
      }
    }

  private def assertOrganisationTypeIsValid(organisationType: String)(implicit request: Request[AnyContent]): Unit =
    require(
      formsByOrganisationTypes(request) contains organisationType,
      message = s"Invalid organisation type '$organisationType'."
    )

  private def formsByOrganisationTypes(implicit request: Request[AnyContent]) = {
    val form = requestSessionData.selectedUserLocationWithIslands(request) match {
      case Some("islands") => islandsSixLineAddressForm
      case _               => thirdCountrySixLineAddressForm
    }

    Map("third-country-organisation" -> form, "third-country-individual" -> form, "third-country-sole-trader" -> form)
  }
}
