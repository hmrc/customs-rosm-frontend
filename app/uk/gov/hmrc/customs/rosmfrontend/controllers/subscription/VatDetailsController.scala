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
import org.joda.time.LocalDate
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.connector.{InvalidResponse, NotFoundResponse, ServiceUnavailableResponse, VatControlListConnector}
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.VatDetailsSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.domain.{LoggedInUserWithEnrolments, VatControlListRequest, VatControlListResponse}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.VatDetails
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.VatDetailsForm.vatDetailsForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.subscription._
import uk.gov.hmrc.customs.rosmfrontend.views.html._
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.{vat_details, we_cannot_confirm_your_identity}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatDetailsController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionFlowManager: SubscriptionFlowManager,
  vatControlListConnector: VatControlListConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  mcc: MessagesControllerComponents,
  vatDetailsView: vat_details,
  errorTemplate: error_template,
  weCannotConfirmYourIdentity: we_cannot_confirm_your_identity,
  subscriptionDetailsService: SubscriptionDetailsService,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(Ok(vatDetailsView(vatDetailsForm, isInReviewMode = false, journey, appConfig)))
  }

  def reviewForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedUkVatDetails.map {
        case Some(vatDetails) => Ok(vatDetailsView(vatDetailsForm.fill(vatDetails), isInReviewMode = true, journey, appConfig))
        case None             => Ok(vatDetailsView(vatDetailsForm, isInReviewMode = true, journey, appConfig))
      }
  }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      vatDetailsForm.bindFromRequest.fold(formWithErrors => {
        Future.successful(BadRequest(vatDetailsView(formWithErrors, isInReviewMode, journey, appConfig)))
      }, formData => {
        lookupVatDetails(formData, isInReviewMode, journey)
      })
    }

  private def lookupVatDetails(vatForm: VatDetails, isInReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] = {

    def isEffectiveDateAssociatedWithVrn(effectiveDate: Option[String]) =
      effectiveDate.map(LocalDate.parse).fold(false)(_ == vatForm.effectiveDate)

    def stripSpaces: String => String = s => s.filterNot(_.isSpaceChar)

    def isPostcodeAssociatedWithVrn(postcode: Option[String]) =
      postcode.fold(false)(stripSpaces(_) equalsIgnoreCase stripSpaces(vatForm.postcode))

    def confirmKnownFacts(knownFacts: VatControlListResponse) =
      isEffectiveDateAssociatedWithVrn(knownFacts.dateOfReg) && isPostcodeAssociatedWithVrn(knownFacts.postcode)

    vatControlListConnector.vatControlList(VatControlListRequest(vatForm.normalizeVATNumber)).flatMap {
      case Right(knownFacts) =>
        if (confirmKnownFacts(knownFacts)) {
          subscriptionDetailsService
            .cacheUkVatDetails(vatForm)
            .map(
              _ =>
                if (isInReviewMode) {
                  Redirect(
                    uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController
                      .determineRoute(journey)
                  )
                } else {
                  Redirect(subscriptionFlowManager.stepInformation(VatDetailsSubscriptionFlowPage).nextPage.url)
              }
            )
        } else {
          Future.successful(Redirect(VatDetailsController.vatDetailsNotMatched(isInReviewMode, journey)))
        }
      case Left(errorResponse) =>
        errorResponse match {
          case NotFoundResponse =>
            Future.successful(Redirect(VatDetailsController.vatDetailsNotMatched(isInReviewMode, journey)))
          case InvalidResponse =>
            Future.successful(Redirect(VatDetailsController.vatDetailsNotMatched(isInReviewMode, journey)))
          case ServiceUnavailableResponse => Future.successful(Results.ServiceUnavailable(errorTemplate()))
        }
    }
  }

  def vatDetailsNotMatched(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(Ok(weCannotConfirmYourIdentity(isInReviewMode)))
    }
}
