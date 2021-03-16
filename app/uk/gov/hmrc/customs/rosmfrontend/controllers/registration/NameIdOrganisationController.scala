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
import org.joda.time.LocalDate
import play.api.Application
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.{Action, _}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.CdsController
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching.Organisation
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.{
  nameUtrCompanyForm,
  nameUtrOrganisationForm,
  nameUtrPartnershipForm
}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_name_id_organisation
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NameIdOrganisationController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  matchNameIdOrganisationView: match_name_id_organisation,
  matchingService: MatchingService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {
  private val RegisteredCompanyDM = "registered-company"
  private val PartnershipDM = "partnership"
  private val OrganisationModeDM = "organisation"

  trait Configuration[M <: NameIdOrganisationMatch] {
    def matchingServiceType: String

    def displayMode: String

    def isNameAddressRegistrationAvailable: Boolean

    def form: Form[M]

    def createCustomsId(id: String): CustomsId
  }

  case class UtrConfiguration(
    matchingServiceType: String,
    displayMode: String,
    isNameAddressRegistrationAvailable: Boolean = false
  ) extends Configuration[NameIdOrganisationMatchModel] {
    val form: Form[NameIdOrganisationMatchModel] = matchingServiceType match {
      case mST if (mST == "Partnership" || mST == "LLP") => nameUtrPartnershipForm
      case mST if (mST == "Company")                     => nameUtrCompanyForm
      case _                                             => nameUtrOrganisationForm
    }

    def createCustomsId(utr: String): Utr = Utr(utr)
  }

  private val OrganisationTypeConfigurations: Map[String, Configuration[_ <: NameIdOrganisationMatch]] = Map(
    CdsOrganisationType.CompanyId -> UtrConfiguration("Corporate Body", displayMode = RegisteredCompanyDM),
    CdsOrganisationType.PartnershipId -> UtrConfiguration("Partnership", displayMode = PartnershipDM),
    CdsOrganisationType.LimitedLiabilityPartnershipId -> UtrConfiguration("LLP", displayMode = PartnershipDM),
    CdsOrganisationType.CharityPublicBodyNotForProfitId -> UtrConfiguration(
      "Unincorporated Body",
      displayMode = OrganisationModeDM,
      isNameAddressRegistrationAvailable = true
    )
  )

  def form(organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      {
        require(OrganisationTypeConfigurations.contains(organisationType), invalidOrganisationType(organisationType))
        Future.successful(Ok(view(organisationType, OrganisationTypeConfigurations(organisationType), journey)))
      }
    }

  def submit(organisationType: String, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      {
        require(OrganisationTypeConfigurations.contains(organisationType), invalidOrganisationType(organisationType))
        val configuration = OrganisationTypeConfigurations(organisationType)
        bind(organisationType, configuration, journey, InternalId(loggedInUser.internalId))
      }
    }

  private def bind[M <: NameIdOrganisationMatch](
    organisationType: String,
    conf: Configuration[M],
    journey: Journey.Value,
    internalId: InternalId
  )(implicit request: Request[AnyContent]): Future[Result] =
    conf.form.bindFromRequest
      .fold(
        formWithErrors => {
          Future.successful(BadRequest(view(organisationType, conf, formWithErrors, journey)))
        },
        formData => {
          matchBusiness(conf.createCustomsId(formData.id), formData.name, None, conf.matchingServiceType, internalId).map {
            case true =>
              journey match {
                case Journey.Migrate =>
                  Redirect(
                    uk.gov.hmrc.customs.rosmfrontend.controllers.routes.AddressController.createForm(journey).url
                  )
                case _ =>
                  Redirect(
                    uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.ConfirmContactDetailsController
                      .form(journey)
                  )
              }
            case false => matchNotFoundBadRequest(organisationType, conf, formData, journey)
          }
        }
      )

  private def invalidOrganisationType(organisationType: String): Any = s"Invalid organisation type '$organisationType'."

  private def matchBusiness(
    id: CustomsId,
    name: String,
    dateEstablished: Option[LocalDate],
    matchingServiceType: String,
    internalId: InternalId
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    matchingService.matchBusiness(id, Organisation(name, matchingServiceType), dateEstablished, internalId)

  private def matchNotFoundBadRequest[M <: NameIdOrganisationMatch](
    organisationType: String,
    conf: Configuration[M],
    formData: M,
    journey: Journey.Value
  )(implicit request: Request[AnyContent]): Result = {
    val errorMsg = Messages("cds.matching-error.not-found")
    val errorForm = conf.form.withGlobalError(errorMsg).fill(formData)
    BadRequest(view(organisationType, conf, errorForm, journey))
  }

  private def view[M <: NameIdOrganisationMatch](
    organisationType: String,
    conf: Configuration[M],
    form: Form[_ <: M],
    journey: Journey.Value
  )(implicit request: Request[AnyContent]): HtmlFormat.Appendable =
    matchNameIdOrganisationView(
      form,
      organisationType,
      conf.displayMode,
      conf.isNameAddressRegistrationAvailable,
      journey
    )

  private def view[M <: NameIdOrganisationMatch](
    organisationType: String,
    conf: Configuration[M],
    journey: Journey.Value
  )(implicit request: Request[AnyContent]): HtmlFormat.Appendable =
    matchNameIdOrganisationView(
      conf.form,
      organisationType,
      conf.displayMode,
      conf.isNameAddressRegistrationAvailable,
      journey
    )
}
