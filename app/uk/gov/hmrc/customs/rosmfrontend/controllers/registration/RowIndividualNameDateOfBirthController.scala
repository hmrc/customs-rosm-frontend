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
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.{DetermineReviewPageController, _}
import uk.gov.hmrc.customs.rosmfrontend.controllers.{CdsController, FeatureFlags}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.thirdCountryIndividualNameDateOfBirthForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RowIndividualNameDateOfBirthController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  sessionCache: SessionCache,
  subscriptionDetailsService: SubscriptionDetailsService,
  mcc: MessagesControllerComponents,
  rowIndividualNameDob: row_individual_name_dob,
  requestSessionData: RequestSessionData
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with FeatureFlags {

  def form(organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUser =>
      assertOrganisationTypeIsValid(organisationType)
      Future.successful(
        Ok(rowIndividualNameDob(thirdCountryIndividualNameDateOfBirthForm, organisationType, journey, false))
      )
    }

  def reviewForm(organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUser =>
      assertOrganisationTypeIsValid(organisationType)
      subscriptionDetailsService.cachedNameDobDetails flatMap {
        case Some(NameDobMatchModel(firstName, middleName, lastName, dateOfBirth)) =>
          val form = thirdCountryIndividualNameDateOfBirthForm.fill(
            IndividualNameAndDateOfBirth(firstName, middleName, lastName, dateOfBirth)
          )
          Future.successful(Ok(rowIndividualNameDob(form, organisationType, journey, true)))
        case _ => Future.successful(Redirect(SecuritySignOutController.signOut(journey)))
      }
    }

  def submit(isInReviewMode: Boolean, organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit loggedInUser: LoggedInUser =>
      assertOrganisationTypeIsValid(organisationType)
      thirdCountryIndividualNameDateOfBirthForm.bindFromRequest.fold(
        formWithErrors =>
          Future.successful(
            BadRequest(rowIndividualNameDob(formWithErrors, organisationType, journey, isInReviewMode))
        ),
        form => submitDetails(isInReviewMode, form, organisationType, journey)
      )
    }

  private def assertOrganisationTypeIsValid(cdsOrganisationType: String): Unit =
    require(
      formsByOrganisationTypes contains cdsOrganisationType,
      message = s"Invalid organisation type '$cdsOrganisationType'."
    )

  private lazy val formsByOrganisationTypes =
    Seq(CdsOrganisationType.ThirdCountryIndividualId, CdsOrganisationType.ThirdCountrySoleTraderId)

  private def submitDetails(
    isInReviewMode: Boolean,
    formData: IndividualNameAndDateOfBirth,
    organisationType: String,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    val nameDobMatchModel =
      NameDobMatchModel(formData.firstName, formData.middleName, formData.lastName, formData.dateOfBirth)

    subscriptionDetailsService.cacheNameDobDetails(nameDobMatchModel) map { _ =>
      (isInReviewMode, rowHaveUtrEnabled) match {
        case (true, _)      => Redirect(DetermineReviewPageController.determineRoute(journey))
        case (false, true)  => Redirect(DoYouHaveAUtrNumberController.form(organisationType, journey, false))
        case (false, false) => Redirect(SixLineAddressController.showForm(false, organisationType, journey))
      }
    }
  }
}
