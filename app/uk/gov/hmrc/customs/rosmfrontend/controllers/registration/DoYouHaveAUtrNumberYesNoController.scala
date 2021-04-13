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

import play.api.Application
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.{Action, _}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType.forId
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.yesNoUtrAnswerForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_organisation_utr_yes_no
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DoYouHaveAUtrNumberYesNoController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  matchingService: MatchingService,
  mcc: MessagesControllerComponents,
  matchOrganisationUtrView: match_organisation_utr_yes_no,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private val OrganisationModeDM = "organisation"

  def form(organisationType: String, journey: Journey.Value, isInReviewMode: Boolean = false): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        Future.successful(
          Ok(matchOrganisationUtrView(yesNoUtrAnswerForm(errorMessageByAnswer(organisationType)), organisationType, OrganisationModeDM, journey, isInReviewMode))
        )
      }
    }

  def submit(organisationType: String, journey: Journey.Value, isInReviewMode: Boolean = false): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      {
        yesNoUtrAnswerForm(errorMessageByAnswer(organisationType)).bindFromRequest.fold(
          formWithErrors => Future.successful(BadRequest(view(organisationType, formWithErrors, journey))),
          { formData =>
            formData.isYes match {
              case true => Future.successful(Redirect(WhatIsYourUtrNumberController.form(organisationType, journey, isInReviewMode)))
              case false => noUtrDestination(organisationType, journey, isInReviewMode)
              case _ => throw new IllegalArgumentException("Have UTR should be Some(true) or Some(false) but was None")
            }
          }
        )
      }
    }

  private def errorMessageByAnswer(orgType: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): String = {
    forId(orgType) match {
      case CdsOrganisationType.ThirdCountryOrganisation => Messages("cds.matching.row-organisation.utr.error")
      case _ => Messages("cds.matching.row-sole-trader-individual.utr.error")
    }
  }

  private def noUtrDestination(
    organisationType: String,
    journey: Journey.Value,
    isInReviewMode: Boolean
  ): Future[Result] =
    organisationType match {
      case CdsOrganisationType.CharityPublicBodyNotForProfitId =>
        Future.successful(Redirect(VatRegisteredUkController.form()))
      case CdsOrganisationType.ThirdCountryOrganisationId =>
        noUtrThirdCountryOrganisationRedirect(isInReviewMode, organisationType, journey)
      case CdsOrganisationType.ThirdCountrySoleTraderId | CdsOrganisationType.ThirdCountryIndividualId =>
        noUtrThirdCountryIndividualsRedirect(journey)
      case _ =>
        Future.successful(Redirect(YouNeedADifferentServiceController.form(journey)))
    }

  private def noUtrThirdCountryOrganisationRedirect(
    isInReviewMode: Boolean,
    organisationType: String,
    journey: Journey.Value
  ): Future[Result] =
    if (isInReviewMode) {
      Future.successful(Redirect(DetermineReviewPageController.determineRoute(journey)))
    } else {
      Future.successful(Redirect(SixLineAddressController.showForm(isInReviewMode = false, organisationType, journey))
      )
    }

  private def noUtrThirdCountryIndividualsRedirect(journey: Journey.Value): Future[Result] =
    Future.successful(Redirect(DoYouHaveNinoController.displayForm(journey)))


  private def view(organisationType: String, form: Form[YesNo], journey: Journey.Value)(
    implicit request: Request[AnyContent]
  ): HtmlFormat.Appendable =
    matchOrganisationUtrView(form, organisationType, OrganisationModeDM, journey)

}
