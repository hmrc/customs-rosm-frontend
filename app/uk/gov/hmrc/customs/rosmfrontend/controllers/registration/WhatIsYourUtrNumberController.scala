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

import org.joda.time.LocalDate
import play.api.Application
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.{Action, _}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Individual
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching.Organisation
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.utrForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_organisation_utr
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatIsYourUtrNumberController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  matchingService: MatchingService,
  mcc: MessagesControllerComponents,
  matchOrganisationUtrView: match_organisation_utr,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private val OrganisationModeDM = "organisation"

  def form(organisationType: String, journey: Journey.Value, isInReviewMode: Boolean = false): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        Future.successful(
          Ok(matchOrganisationUtrView(utrForm, organisationType, OrganisationModeDM, journey, isInReviewMode))
        )
      }
    }

  def submit(organisationType: String, journey: Journey.Value, isInReviewMode: Boolean = false): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      {
        utrForm.bindFromRequest.fold(
          formWithErrors => Future.successful(BadRequest(view(organisationType, formWithErrors, journey))),
          formData =>
            destinationsByAnswer(
              formData.normalize(),
              organisationType,
              journey,
              isInReviewMode,
              InternalId(loggedInUser.internalId)
          )
        )
      }
    }

  private def destinationsByAnswer(
                                    formData: UtrMatchModel,
                                    organisationType: String,
                                    journey: Journey.Value,
                                    isInReviewMode: Boolean,
                                    internalId: InternalId
  )(implicit request: Request[AnyContent]): Future[Result] =
    formData.id match {
      case Some(_) =>
        matchBusinessOrIndividual(formData, journey, organisationType, internalId)
      case _ =>
        throw new IllegalArgumentException("Have UTR should be Some(true) or Some(false) but was None")
    }

  private def matchBusiness(
    id: CustomsId,
    name: String,
    dateEstablished: Option[LocalDate],
    matchingServiceType: String,
    internalId: InternalId
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    matchingService.matchBusiness(id, Organisation(name, matchingServiceType), dateEstablished, internalId)

  private def matchIndividual(id: CustomsId, internalId: InternalId)(implicit hc: HeaderCarrier): Future[Boolean] =
    subscriptionDetailsService.cachedNameDobDetails flatMap {
      case Some(details) =>
        matchingService.matchIndividualWithId(
          id,
          Individual.withLocalDate(details.firstName, details.middleName, details.lastName, details.dateOfBirth),
          internalId
        )
      case None => Future.successful(false)
    }

  private def view(organisationType: String, form: Form[UtrMatchModel], journey: Journey.Value)(
    implicit request: Request[AnyContent]
  ): HtmlFormat.Appendable =
    matchOrganisationUtrView(form, organisationType, OrganisationModeDM, journey)

  private def matchBusinessOrIndividual(
                                         formData: UtrMatchModel,
                                         journey: Journey.Value,
                                         organisationType: String,
                                         internalId: InternalId
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    (organisationType match {
      case CdsOrganisationType.ThirdCountrySoleTraderId | CdsOrganisationType.ThirdCountryIndividualId =>
        matchIndividual(Utr(formData.id.get), internalId)
      case orgType =>
        subscriptionDetailsService.cachedNameDetails.flatMap {
          case Some(NameOrganisationMatchModel(name)) =>
            matchBusiness(
              Utr(formData.id.get),
              name,
              None,
              EtmpOrganisationType(CdsOrganisationType(orgType)).toString,
              internalId
            )
          case None => Future.successful(false)
        }
    }).map {
      case true  => Redirect(ConfirmContactDetailsController.form(journey))
      case false => matchNotFoundBadRequest(organisationType, formData, journey)
    }

  private def matchNotFoundBadRequest(organisationType: String, formData: UtrMatchModel, journey: Journey.Value)(
    implicit request: Request[AnyContent]
  ): Result = {
    val errorMsg = organisationType match {
      case CdsOrganisationType.SoleTraderId | CdsOrganisationType.IndividualId |
          CdsOrganisationType.ThirdCountrySoleTraderId | CdsOrganisationType.ThirdCountryIndividualId =>
        Messages("cds.matching-error.individual-not-found")
      case _ => Messages("cds.matching-error-organisation.not-found")
    }
    val errorForm = utrForm.withGlobalError(errorMsg).fill(formData)
    BadRequest(view(organisationType, errorForm, journey))
  }
}
