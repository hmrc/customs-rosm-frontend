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

///*
// * Copyright 2021 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.customs.rosmfrontend.controllers.registration
//
//import javax.inject.{Inject, Singleton}
//import play.api.Application
//import play.api.i18n.Messages
//import play.api.mvc.{Action, _}
//import uk.gov.hmrc.auth.core.AuthConnector
//import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
//import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
//import uk.gov.hmrc.customs.rosmfrontend.domain._
//import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Individual
//import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.ninoOrUtrForm
//import uk.gov.hmrc.customs.rosmfrontend.models.Journey
//import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
//import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
//import uk.gov.hmrc.customs.rosmfrontend.views.html.migration._
//import uk.gov.hmrc.http.HeaderCarrier
//
//import scala.concurrent.{ExecutionContext, Future}
//
//@Singleton
//class GYEHowCanWeIdentifyYouController @Inject()(
//  override val currentApp: Application,
//  override val authConnector: AuthConnector,
//  matchingService: MatchingService,
//  mcc: MessagesControllerComponents,
//  howCanWeIdentifyYouView: how_can_we_identify_you,
//  cdsFrontendDataCache: SessionCache
//)(implicit ec: ExecutionContext)
//    extends CdsController(mcc) {
//
//  def form(organisationType: String, journey: Journey.Value): Action[AnyContent] =
//    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
//      {
//        val form = ninoOrUtrForm
//        Future.successful(Ok(howCanWeIdentifyYouView(form, isInReviewMode = false, journey, Some(organisationType))))
//      }
//    }
//
//  def submit(organisationType: String, journey: Journey.Value): Action[AnyContent] =
//    ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
//      {
//        ninoOrUtrForm.bindFromRequest.fold(
//          formWithErrors =>
//            Future.successful(
//              BadRequest(
//                howCanWeIdentifyYouView(formWithErrors, isInReviewMode = false, journey, Some(organisationType))
//              )
//          ),
//          formData =>
//            matchOnId(formData, InternalId(loggedInUser.internalId)).map {
//              case true =>
//                Redirect(ConfirmContactDetailsController.form(journey))
//              case false =>
//                matchNotFoundBadRequest(formData, organisationType, journey)
//          }
//        )
//      }
//    }
//
//  private def matchOnId(formData: NinoOrUtr, internalId: InternalId)(implicit hc: HeaderCarrier): Future[Boolean] =
//    formData match {
//      case NinoOrUtr(Some(nino), _, selected) if selected.contains("nino") =>
//        retrieveNameDobFromCache().flatMap(ind => matchingService.matchIndividualWithNino(nino, ind, internalId))
//      case NinoOrUtr(_, Some(utr), selected) if selected.contains("utr") =>
//        retrieveNameDobFromCache().flatMap(ind => matchingService.matchIndividualWithId(Utr(utr), ind, internalId))
//      case _ => Future.successful(false)
//    }
//
//  private def matchNotFoundBadRequest(individualFormData: NinoOrUtr, organisationType: String, journey: Journey.Value)(
//    implicit request: Request[AnyContent]
//  ): Result = {
//    val errorForm = ninoOrUtrForm
//      .withGlobalError(Messages("cds.matching-error.individual-not-found"))
//      .fill(individualFormData)
//    BadRequest(howCanWeIdentifyYouView(errorForm, isInReviewMode = false, journey, Some(organisationType)))
//  }
//
//  private def retrieveNameDobFromCache()(implicit hc: HeaderCarrier): Future[Individual] =
//    for {
//      nameDobDetails <- cdsFrontendDataCache.subscriptionDetails.map(_.nameDobDetails.get)
//    } yield
//      Individual.withLocalDate(
//        firstName = nameDobDetails.firstName,
//        middleName = None,
//        lastName = nameDobDetails.lastName,
//        dateOfBirth = nameDobDetails.dateOfBirth
//      )
//}
