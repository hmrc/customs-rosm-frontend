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

package uk.gov.hmrc.customs.rosmfrontend.controllers.migration

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.data.Form
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.NameIdOrganisationDisplayMode._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.NameUtrDetailsSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.{
  nameUtrCompanyForm,
  nameUtrOrganisationForm,
  nameUtrPartnershipForm
}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.nameId
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NameIDOrgController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  requestSessionData: RequestSessionData,
  cdsFrontendDataCache: SessionCache,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  nameIdView: nameId,
  subscriptionDetailsHolderService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def form(implicit request: Request[AnyContent]) =
    if (requestSessionData.isPartnership) nameUtrPartnershipForm
    else if (requestSessionData.isCompany) nameUtrCompanyForm
    else nameUtrOrganisationForm

  def createForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        subscriptionBusinessService.cachedNameIdOrganisationViewModel flatMap { cachedNameUtrViewModel =>
          val selectedOrganisationType =
            requestSessionData.userSelectedOrganisationType.map(_.id)
          populateOkView(
            cachedNameUtrViewModel,
            selectedOrganisationType.getOrElse(""),
            OrganisationTypeConfigurations(selectedOrganisationType.getOrElse("")),
            isInReviewMode = false,
            journey
          )
        }
      }
    }

  def reviewForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedNameIdViewModel flatMap { cdm =>
        val selectedOrganisationType =
          requestSessionData.userSelectedOrganisationType.map(_.id)
        populateOkView(
          Some(cdm),
          selectedOrganisationType.getOrElse(""),
          OrganisationTypeConfigurations(selectedOrganisationType.getOrElse("")),
          isInReviewMode = true,
          journey
        )
      }
    }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      form.bindFromRequest
        .fold(
          formWithErrors => {
            cdsFrontendDataCache.registrationDetails map { registrationDetails =>
              val selectedOrganisationType =
                requestSessionData.userSelectedOrganisationType.map(_.id)
              BadRequest(
                nameIdView(
                  formWithErrors,
                  registrationDetails,
                  isInReviewMode,
                  OrganisationTypeConfigurations(selectedOrganisationType.getOrElse("")).displayMode,
                  journey
                )
              )
            }
          },
          formData => {
            storeNameUtrDetails(formData.normalize(), isInReviewMode, journey)
          }
        )
    }

  private def populateOkView(
    nameUtrViewModel: Option[NameIdOrganisationMatchModel],
    organisationType: String,
    conf: Configuration,
    isInReviewMode: Boolean,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    lazy val nameUtrForm = nameUtrViewModel.fold(form)(form.fill)

    require(OrganisationTypeConfigurations.contains(organisationType), invalidOrganisationType(organisationType))

    cdsFrontendDataCache.registrationDetails map { registrationDetails =>
      Ok(nameIdView(nameUtrForm, registrationDetails, isInReviewMode, conf.displayMode, journey))
    }
  }

  private def storeNameUtrDetails(
    formData: NameIdOrganisationMatchModel,
    inReviewMode: Boolean,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    subscriptionDetailsHolderService
      .cacheNameIdDetails(formData)
      .map(
        _ =>
          if (inReviewMode) {
            Redirect(
              uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController
                .determineRoute(journey)
            )

          } else {
            Redirect(
              subscriptionFlowManager
                .stepInformation(NameUtrDetailsSubscriptionFlowPage)
                .nextPage
                .url
            )
        }
      )

  trait Configuration {
    def matchingServiceType: String
    def displayMode: String
    def isNameAddressRegistrationAvailable: Boolean
    def form: Form[NameIdOrganisationMatchModel]
    def createCustomsId(id: String): CustomsId
  }

  case class UtrConfiguration(
    matchingServiceType: String,
    displayMode: String,
    isNameAddressRegistrationAvailable: Boolean = false
  ) extends Configuration {
    lazy val form: Form[NameIdOrganisationMatchModel] = nameUtrOrganisationForm
    def createCustomsId(utr: String): Utr = Utr(utr)
  }

  def invalidOrganisationType(organisationType: String): Any =
    s"Invalid organisation type '$organisationType'."

  private val OrganisationTypeConfigurations: Map[String, Configuration] =
    Map(
      CdsOrganisationType.CompanyId -> UtrConfiguration("Corporate Body", displayMode = RegisteredCompanyDM),
      CdsOrganisationType.PartnershipId -> UtrConfiguration("Partnership", displayMode = PartnershipDM),
      CdsOrganisationType.LimitedLiabilityPartnershipId -> UtrConfiguration("LLP", displayMode = PartnershipDM),
      CdsOrganisationType.CharityPublicBodyNotForProfitId -> UtrConfiguration(
        "Unincorporated Body",
        displayMode = OrganisationModeDM,
        isNameAddressRegistrationAvailable = true
      )
    )
}

object NameIdOrganisationDisplayMode {
  val RegisteredCompanyDM = "registered-company"
  val CompanyDM = "company"
  val PartnershipDM = "partnership"
  val OrganisationModeDM = "organisation"
}
