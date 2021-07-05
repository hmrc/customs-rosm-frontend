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
import play.api.mvc.{AnyContent, Request, Session}
import uk.gov.hmrc.customs.rosmfrontend.controllers.FeatureFlags
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{SubscriptionFlow, SubscriptionPage, _}
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger.logger
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.util.Constants.ONE
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class SubscriptionFlowConfig(
  pageBeforeFirstFlowPage: SubscriptionPage,
  pagesInOrder: List[SubscriptionPage],
  pageAfterLastFlowPage: SubscriptionPage
) {

  private def lastPageInTheFlow(currentPos: Int): Boolean = currentPos == pagesInOrder.size - ONE

  def determinePageBeforeSubscriptionFlow(uriBeforeSubscriptionFlow: Option[String]): SubscriptionPage =
    uriBeforeSubscriptionFlow.fold(pageBeforeFirstFlowPage)(url => PreviousPage(url))

  def stepInformation(
    currentPage: SubscriptionPage,
    uriBeforeSubscriptionFlow: Option[String]
  ): SubscriptionFlowInfo = {

    val currentPos = pagesInOrder.indexOf(currentPage)

    val nextPage = if (lastPageInTheFlow(currentPos)) pageAfterLastFlowPage else pagesInOrder(currentPos + ONE)

    SubscriptionFlowInfo(stepNumber = currentPos + ONE, totalSteps = pagesInOrder.size, nextPage = nextPage)
  }
}

@Singleton
class SubscriptionFlowManager @Inject()(
  override val currentApp: Application,
  requestSessionData: RequestSessionData,
  cdsFrontendDataCache: SessionCache,
  subscriptionDetailsService: SubscriptionDetailsService
) extends FeatureFlags {

  def currentSubscriptionFlow(implicit request: Request[AnyContent]): SubscriptionFlow =
    requestSessionData.userSubscriptionFlow

  def stepInformation(
    currentPage: SubscriptionPage
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): SubscriptionFlowInfo =
    SubscriptionFlows(currentSubscriptionFlow)
      .stepInformation(currentPage, requestSessionData.uriBeforeSubscriptionFlow)

  def startSubscriptionFlow(
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[(SubscriptionPage, Session)] =
    startSubscriptionFlow(None, requestSessionData.userSelectedOrganisationType, journey)

  def startSubscriptionFlow(
    previousPage: Option[SubscriptionPage] = None,
    cdsOrganisationType: CdsOrganisationType,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[(SubscriptionPage, Session)] =
    startSubscriptionFlow(previousPage, Some(cdsOrganisationType), journey)

  def startSubscriptionFlow(
    previousPage: Option[SubscriptionPage],
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[(SubscriptionPage, Session)] =
    startSubscriptionFlow(previousPage, requestSessionData.userSelectedOrganisationType, journey)

  private def startSubscriptionFlow(
    previousPage: Option[SubscriptionPage],
    orgType: => Option[CdsOrganisationType],
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[(SubscriptionPage, Session)] = {
    val maybePreviousPageUrl = previousPage.map(page => page.url)
    cdsFrontendDataCache.registrationDetails map { registrationDetails =>
      {
        val flow = selectFlow(registrationDetails, orgType, journey)

        logger.info(s"select Subscription flow: ${flow.name}")
        (
          SubscriptionFlows(flow).pagesInOrder.head,
          requestSessionData.storeUserSubscriptionFlow(
            flow,
            SubscriptionFlows(flow).determinePageBeforeSubscriptionFlow(maybePreviousPageUrl).url
          )
        )
      }
    }
  }

  private def selectFlow(
    registrationDetails: RegistrationDetails,
    maybeOrgType: => Option[CdsOrganisationType],
    journey: Journey.Value
  )(implicit request: Request[AnyContent], headerCarrier: HeaderCarrier): SubscriptionFlow = {
    val userLocation = requestSessionData.selectedUserLocation

    val subscribePrefix = (userLocation, journey, registrationDetails.customsId, rowHaveUtrEnabled) match {
      case (
          Some(UserLocation.Islands) | Some(UserLocation.ThirdCountry),
          Journey.Migrate,
          None,
          true
          ) =>
        "migration-eori-row-utrNino-enabled-"
      case (
          Some(UserLocation.Islands) | Some(UserLocation.ThirdCountry),
          Journey.Migrate,
          _,
          _
          ) =>
        "migration-eori-row-"
      case (_, Journey.Migrate, _, _) => "migration-eori-" // This means UK
      case _                          => ""
    }

    val selectedFlow: SubscriptionFlow =
      (registrationDetails, maybeOrgType, rowHaveUtrEnabled, registrationDetails.customsId, journey) match {
        case (_: RegistrationDetailsOrganisation, Some(CdsOrganisationType.Partnership), _, _, _) =>
          SubscriptionFlow(subscribePrefix + PartnershipSubscriptionFlow.name)
        case (_: RegistrationDetailsOrganisation, _, true, None, Journey.Migrate) =>
          SubscriptionFlow(subscribePrefix + OrganisationSubscriptionFlow.name)
        case (_: RegistrationDetailsIndividual, _, true, None, Journey.Migrate) =>
          SubscriptionFlow(subscribePrefix + IndividualSubscriptionFlow.name)
        case (_: RegistrationDetailsOrganisation, _, _, _, _) =>
          SubscriptionFlow(subscribePrefix + OrganisationSubscriptionFlow.name)
        case (_: RegistrationDetailsIndividual, _, _, _, _) =>
          SubscriptionFlow(subscribePrefix + IndividualSubscriptionFlow.name)
        case _ => throw new IllegalStateException("Incomplete cache cannot complete journey")
      }

    maybeOrgType.fold(selectedFlow)(
      orgType => SubscriptionFlows.flows.keys.find(_.name == (subscribePrefix + orgType.id)).getOrElse(selectedFlow)
    )
  }
}
