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
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Individual
import uk.gov.hmrc.customs.rosmfrontend.domain.{InternalId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_nino

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NinoController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  matchNinoView: match_nino,
  matchingService: MatchingService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        Future.successful(Ok(matchNinoView(ninoForm, organisationType, journey)))
      }
    }

  def submit(organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      {
        ninoForm.bindFromRequest.fold(
          invalidForm => {
            Future.successful(BadRequest(matchNinoView(invalidForm, organisationType, journey)))
          },
          form => {
            val normalisedForm =  form.normalize()
            matchingService.matchIndividualWithNino(
              normalisedForm.nino,
              Individual.withLocalDate(normalisedForm.firstName, normalisedForm.lastName, normalisedForm.dateOfBirth),
              InternalId(loggedInUser.internalId)
            ) map {

              case true =>
                Redirect(
                  uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.ConfirmContactDetailsController
                    .form(journey)
                )
              case false =>
                val errorForm = ninoForm
                  .withGlobalError(Messages("cds.matching-error.individual-not-found"))
                  .fill(form)
                BadRequest(matchNinoView(errorForm, organisationType, journey))
            }
          }
        )
      }
    }
}
