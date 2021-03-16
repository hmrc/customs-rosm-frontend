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
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.VatDetailsEuConfirmController
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails.EuVatDetailsLimit
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.VatEUDetailsModel
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.SubscriptionForm.euVatForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.countries.Countries
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionVatEUDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.vat_details_eu
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatDetailsEuController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  vatEUDetailsService: SubscriptionVatEUDetailsService,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  vatDetailsEuView: vat_details_eu,
  countries: Countries
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value, isInReviewMode: Boolean = false): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      isEuVatDetailsSeqOnLimit map {
        case true => Redirect(VatDetailsEuConfirmController.createForm(journey))
        case _    => Ok(vatDetailsEuView(euVatForm, countries.eu, isInReviewMode = false, journey = journey))
      }
    }

  def reviewForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      isEuVatDetailsSeqOnLimit map {
        case true => Redirect(VatDetailsEuConfirmController.reviewForm(journey))
        case _    => Ok(vatDetailsEuView(euVatForm, countries.eu, isInReviewMode = true, journey = journey))
      }
  }

  def submit(journey: Journey.Value, isInReviewMode: Boolean): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      vatEUDetailsService.cachedEUVatDetails.flatMap { vatEUDetailsModel =>
        euVatForm.bindFromRequest.fold(
          formWithErrors =>
            Future.successful(
              BadRequest(
                vatDetailsEuView(formWithErrors, countries.eu, isInReviewMode = isInReviewMode, journey = journey)
              )
          ),
          validFormModel => {
            validAddition(validFormModel, vatEUDetailsModel).fold(
              storeVatDetails(validFormModel, journey, isInReviewMode)
            )(badRequest(euVatForm.fill(validFormModel), _, isInReviewMode = isInReviewMode, journey = journey))
          }
        )
      }
    }

  def submitUpdate(index: Int, journey: Journey.Value, isInReviewMode: Boolean): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      vatEUDetailsService.vatEuDetails(index) flatMap {
        case Some(oldEuVatDetails) =>
          vatEUDetailsService.cachedEUVatDetails flatMap { vatEUDetailsModel =>
            euVatForm.bindFromRequest.fold(
              formWithErrors =>
                Future.successful(
                  BadRequest(
                    vatDetailsEuView(
                      formWithErrors,
                      countries.eu,
                      journey = journey,
                      isInReviewMode = isInReviewMode,
                      vatDetails = Option(oldEuVatDetails),
                      updateDetails = true
                    )
                  )
              ),
              newEuVatDetails => {
                validAddition(newEuVatDetails, vatEUDetailsModel, isChanged(oldEuVatDetails, newEuVatDetails))
                  .fold(updateDetails(oldEuVatDetails, newEuVatDetails, journey, isInReviewMode))(
                    badRequest(
                      euVatForm.fill(newEuVatDetails),
                      _,
                      journey = journey,
                      Option(oldEuVatDetails),
                      updateDetails = true,
                      isInReviewMode
                    )
                  )
              }
            )
          }
        case _ => throw new IllegalStateException("Vat for update not found")
      }
    }

  def updateForm(index: Int, journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      vatEUDetailsService.vatEuDetails(index) flatMap {
        case Some(vatDetails) =>
          Future.successful(
            Ok(
              vatDetailsEuView(
                euVatForm.fill(vatDetails),
                countries.eu,
                updateDetails = true,
                journey,
                isInReviewMode = false,
                Option(vatDetails)
              )
            )
          )
        case _ => goToConfirmVat(journey, isInReviewMode = false)
      }
  }

  def reviewUpdateForm(index: Int, journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      vatEUDetailsService.vatEuDetails(index) flatMap {
        case Some(vatDetails) =>
          Future.successful(
            Ok(
              vatDetailsEuView(
                euVatForm.fill(vatDetails),
                countries.eu,
                updateDetails = true,
                journey,
                isInReviewMode = true,
                Option(vatDetails)
              )
            )
          )
        case _ => goToConfirmVat(journey, isInReviewMode = true)
      }
  }

  private def storeVatDetails(formData: VatEUDetailsModel, journey: Journey.Value, isInReviewMode: Boolean)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    vatEUDetailsService.saveOrUpdate(formData) flatMap (_ => goToConfirmVat(journey, isInReviewMode))

  private def updateDetails(
    oldVatDetails: VatEUDetailsModel,
    newVatDetails: VatEUDetailsModel,
    journey: Journey.Value,
    isInReviewMode: Boolean
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    vatEUDetailsService.updateVatEuDetailsModel(oldVatDetails, newVatDetails) flatMap { vatEuDetails =>
      vatEUDetailsService.saveOrUpdate(vatEuDetails) flatMap (_ => goToConfirmVat(journey, isInReviewMode))
    }

  private def isChanged(newEuVatDetails: VatEUDetailsModel, oldEuVatDetails: VatEUDetailsModel): Boolean =
    oldEuVatDetails != newEuVatDetails

  private def validAddition(
    newEuVatDetails: VatEUDetailsModel,
    cachedVats: Seq[VatEUDetailsModel],
    isChanged: Boolean = true
  )(implicit messages: Messages): Option[String] =
    if (cachedVats.contains(newEuVatDetails) && isChanged)
      Some(messages("cds.subscription.vat-details.page-duplicate-vat-error"))
    else None

  private def badRequest(
    form: Form[VatEUDetailsModel],
    error: String,
    journey: Journey.Value,
    vatEUDetailsModel: Option[VatEUDetailsModel] = None,
    updateDetails: Boolean = false,
    isInReviewMode: Boolean
  )(implicit request: Request[AnyContent]): Future[Result] =
    Future.successful(
      BadRequest(
        vatDetailsEuView(
          form.withError("vatNumber", error),
          countries.eu,
          updateDetails,
          journey,
          isInReviewMode,
          vatEUDetailsModel
        )
      )
    )

  private def isEuVatDetailsSeqOnLimit(implicit hc: HeaderCarrier): Future[Boolean] =
    vatEUDetailsService.cachedEUVatDetails map (_.size == EuVatDetailsLimit)

  private def goToConfirmVat(journey: Journey.Value, isInReviewMode: Boolean) =
    isInReviewMode match {
      case false => Future.successful(Redirect(VatDetailsEuConfirmController.createForm(journey)))
      case _     => Future.successful(Redirect(VatDetailsEuConfirmController.reviewForm(journey)))
    }
}
