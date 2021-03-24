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

package unit.controllers.subscription

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.prop.Tables.Table
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContent, Request, Session}
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{IndividualSubscriptionFlow, _}
import uk.gov.hmrc.customs.rosmfrontend.domain.{
  CdsOrganisationType,
  RegistrationDetailsIndividual,
  RegistrationDetailsOrganisation
}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec
import util.ControllerSpec

import scala.concurrent.Future

class SubscriptionFlowManagerSpec
    extends UnitSpec with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach with ControllerSpec {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Map("features.rowHaveUtrEnabled" -> false))
    .build()

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockCdsFrontendDataCache = mock[SessionCache]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  val controller =
    new SubscriptionFlowManager(app, mockRequestSessionData, mockCdsFrontendDataCache, mockSubscriptionDetailsService)
  private val mockOrgRegistrationDetails = mock[RegistrationDetailsOrganisation]
  private val mockIndividualRegistrationDetails = mock[RegistrationDetailsIndividual]
  private val mockSession = mock[Session]

  private val mockHC = mock[HeaderCarrier]
  private val mockRequest = mock[Request[AnyContent]]

  private val mockSubscriptionFlow = mock[SubscriptionFlow]

  val noSubscriptionFlowInSessionException = new IllegalStateException("No subscription flow in session.")

  override def beforeEach(): Unit = {
    reset(mockRequestSessionData, mockSession, mockCdsFrontendDataCache)
    when(mockRequestSessionData.storeUserSubscriptionFlow(any[SubscriptionFlow], any[String])(any[Request[AnyContent]]))
      .thenReturn(mockSession)
    when(mockCdsFrontendDataCache.saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))
  }

  "Getting current subscription flow" should {
    "return value from session when stored there before" in {
      when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(mockSubscriptionFlow)

      controller.currentSubscriptionFlow(mockRequest) shouldBe mockSubscriptionFlow
    }

    "fail when there was no flow stored in session before" in {
      when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]]))
        .thenThrow(noSubscriptionFlowInSessionException)

      intercept[IllegalStateException](controller.currentSubscriptionFlow(mockRequest)) shouldBe noSubscriptionFlowInSessionException
    }
  }

  "Flow already started" should {
    val values = Table(
      ("flow", "currentPage", "expectedStepNumber", "expectedTotalSteps", "expectedNextPage"),
      (
        OrganisationSubscriptionFlow,
        DateOfEstablishmentSubscriptionFlowPage,
        1,
        12,
        ContactDetailsSubscriptionFlowPageGetEori
      ),
      (
        OrganisationSubscriptionFlow,
        ContactDetailsSubscriptionFlowPageGetEori,
        2,
        12,
        ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori
      ),
      (OrganisationSubscriptionFlow, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori, 3, 12, ContactDetailsAddressSubscriptionFlowPageGetEori),
      (OrganisationSubscriptionFlow, ContactDetailsAddressSubscriptionFlowPageGetEori, 4, 12, BusinessShortNameSubscriptionFlowPage),
      (OrganisationSubscriptionFlow, BusinessShortNameSubscriptionFlowPage, 5, 12, SicCodeSubscriptionFlowPage),
      (OrganisationSubscriptionFlow, SicCodeSubscriptionFlowPage, 6, 12, VatRegisteredUkSubscriptionFlowPage),
      (OrganisationSubscriptionFlow, VatRegisteredUkSubscriptionFlowPage, 7, 12, VatDetailsSubscriptionFlowPage),
      (OrganisationSubscriptionFlow, VatDetailsSubscriptionFlowPage, 8, 12, VatRegisteredEuSubscriptionFlowPage),
      (OrganisationSubscriptionFlow, VatRegisteredEuSubscriptionFlowPage, 9, 12, VatEUIdsSubscriptionFlowPage),
      (OrganisationSubscriptionFlow, VatEUIdsSubscriptionFlowPage, 10, 12, VatEUConfirmSubscriptionFlowPage),
      (OrganisationSubscriptionFlow, VatEUConfirmSubscriptionFlowPage, 11, 12, EoriConsentSubscriptionFlowPage),
      (OrganisationSubscriptionFlow, EoriConsentSubscriptionFlowPage, 12, 12, ReviewDetailsPageGetYourEORI),
      (
        PartnershipSubscriptionFlow,
        DateOfEstablishmentSubscriptionFlowPage,
        1,
        12,
        ContactDetailsSubscriptionFlowPageGetEori
      ),
      (
        PartnershipSubscriptionFlow,
        ContactDetailsSubscriptionFlowPageGetEori,
        2,
        12,
        ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori
      ),
      (PartnershipSubscriptionFlow, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori, 3, 12, ContactDetailsAddressSubscriptionFlowPageGetEori),
      (PartnershipSubscriptionFlow, ContactDetailsAddressSubscriptionFlowPageGetEori, 4, 12, BusinessShortNameSubscriptionFlowPage),
      (PartnershipSubscriptionFlow, BusinessShortNameSubscriptionFlowPage, 5, 12, SicCodeSubscriptionFlowPage),
      (PartnershipSubscriptionFlow, SicCodeSubscriptionFlowPage, 6, 12, VatRegisteredUkSubscriptionFlowPage),
      (PartnershipSubscriptionFlow, VatRegisteredUkSubscriptionFlowPage, 7, 12, VatDetailsSubscriptionFlowPage),
      (PartnershipSubscriptionFlow, VatDetailsSubscriptionFlowPage, 8, 12, VatRegisteredEuSubscriptionFlowPage),
      (PartnershipSubscriptionFlow, VatRegisteredEuSubscriptionFlowPage, 9, 12, VatEUIdsSubscriptionFlowPage),
      (PartnershipSubscriptionFlow, VatEUIdsSubscriptionFlowPage, 10, 12, VatEUConfirmSubscriptionFlowPage),
      (PartnershipSubscriptionFlow, VatEUConfirmSubscriptionFlowPage, 11, 12, EoriConsentSubscriptionFlowPage),
      (PartnershipSubscriptionFlow, EoriConsentSubscriptionFlowPage, 12, 12, ReviewDetailsPageGetYourEORI),
      (SoleTraderSubscriptionFlow, ContactDetailsSubscriptionFlowPageGetEori, 1, 10, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori),
      (SoleTraderSubscriptionFlow, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori, 2, 10, ContactDetailsAddressSubscriptionFlowPageGetEori),
      (SoleTraderSubscriptionFlow, ContactDetailsAddressSubscriptionFlowPageGetEori, 3, 10, SicCodeSubscriptionFlowPage),
      (SoleTraderSubscriptionFlow, SicCodeSubscriptionFlowPage, 4, 10, VatRegisteredUkSubscriptionFlowPage),
      (SoleTraderSubscriptionFlow, VatRegisteredUkSubscriptionFlowPage, 5, 10, VatDetailsSubscriptionFlowPage),
      (SoleTraderSubscriptionFlow, VatDetailsSubscriptionFlowPage, 6, 10, VatRegisteredEuSubscriptionFlowPage),
      (SoleTraderSubscriptionFlow, VatRegisteredEuSubscriptionFlowPage, 7, 10, VatEUIdsSubscriptionFlowPage),
      (SoleTraderSubscriptionFlow, VatEUIdsSubscriptionFlowPage, 8, 10, VatEUConfirmSubscriptionFlowPage),
      (SoleTraderSubscriptionFlow, VatEUConfirmSubscriptionFlowPage, 9, 10, EoriConsentSubscriptionFlowPage),
      (SoleTraderSubscriptionFlow, EoriConsentSubscriptionFlowPage, 10, 10, ReviewDetailsPageGetYourEORI),
      (IndividualSubscriptionFlow, ContactDetailsSubscriptionFlowPageGetEori, 1, 4, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori),
      (IndividualSubscriptionFlow, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori, 2, 4, ContactDetailsAddressSubscriptionFlowPageGetEori),
      (IndividualSubscriptionFlow, ContactDetailsAddressSubscriptionFlowPageGetEori, 3,4, EoriConsentSubscriptionFlowPage),
      (IndividualSubscriptionFlow, EoriConsentSubscriptionFlowPage, 4, 4, ReviewDetailsPageGetYourEORI),
      (
        ThirdCountryOrganisationSubscriptionFlow,
        DateOfEstablishmentSubscriptionFlowPage,
        1,
        12,
        ContactDetailsSubscriptionFlowPageGetEori
      ),
      (
        ThirdCountryOrganisationSubscriptionFlow,
        ContactDetailsSubscriptionFlowPageGetEori,
        2,
        12,
        ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori
      ),
      (ThirdCountryOrganisationSubscriptionFlow, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori, 3, 12, ContactDetailsAddressSubscriptionFlowPageGetEori),
      (ThirdCountryOrganisationSubscriptionFlow, ContactDetailsAddressSubscriptionFlowPageGetEori, 4, 12, BusinessShortNameSubscriptionFlowPage),
      (ThirdCountryOrganisationSubscriptionFlow, BusinessShortNameSubscriptionFlowPage, 5, 12, SicCodeSubscriptionFlowPage),
      (
        ThirdCountryOrganisationSubscriptionFlow,
        SicCodeSubscriptionFlowPage,
        6,
        12,
        VatRegisteredUkSubscriptionFlowPage
      ),
      (
        ThirdCountryOrganisationSubscriptionFlow,
        VatRegisteredUkSubscriptionFlowPage,
        7,
        12,
        VatDetailsSubscriptionFlowPage
      ),
      (
        ThirdCountryOrganisationSubscriptionFlow,
        VatDetailsSubscriptionFlowPage,
        8,
        12,
        VatRegisteredEuSubscriptionFlowPage
      ),
      (
        ThirdCountryOrganisationSubscriptionFlow,
        VatRegisteredEuSubscriptionFlowPage,
        9,
        12,
        VatEUIdsSubscriptionFlowPage
      ),
      (ThirdCountryOrganisationSubscriptionFlow, VatEUIdsSubscriptionFlowPage, 10, 12, VatEUConfirmSubscriptionFlowPage),
      (
        ThirdCountryOrganisationSubscriptionFlow,
        VatEUConfirmSubscriptionFlowPage,
        11,
        12,
        EoriConsentSubscriptionFlowPage
      ),
      (ThirdCountryOrganisationSubscriptionFlow, EoriConsentSubscriptionFlowPage, 12, 12, ReviewDetailsPageGetYourEORI),
      (ThirdCountryIndividualSubscriptionFlow, ContactDetailsSubscriptionFlowPageGetEori, 1, 4, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori),
      (ThirdCountryIndividualSubscriptionFlow, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori, 2, 4, ContactDetailsAddressSubscriptionFlowPageGetEori),
      (ThirdCountryIndividualSubscriptionFlow, ContactDetailsAddressSubscriptionFlowPageGetEori, 3,4, EoriConsentSubscriptionFlowPage),
      (ThirdCountryIndividualSubscriptionFlow, EoriConsentSubscriptionFlowPage, 4, 4, ReviewDetailsPageGetYourEORI),
      (ThirdCountrySoleTraderSubscriptionFlow, ContactDetailsSubscriptionFlowPageGetEori, 1, 10, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori),
      (ThirdCountrySoleTraderSubscriptionFlow, ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori, 2, 10, ContactDetailsAddressSubscriptionFlowPageGetEori),
      (ThirdCountrySoleTraderSubscriptionFlow, ContactDetailsAddressSubscriptionFlowPageGetEori, 3, 10, SicCodeSubscriptionFlowPage),
      (ThirdCountrySoleTraderSubscriptionFlow, SicCodeSubscriptionFlowPage, 4, 10, VatRegisteredUkSubscriptionFlowPage),
      (ThirdCountrySoleTraderSubscriptionFlow, VatRegisteredUkSubscriptionFlowPage, 5, 10, VatDetailsSubscriptionFlowPage),
      (ThirdCountrySoleTraderSubscriptionFlow, VatDetailsSubscriptionFlowPage, 6, 10, VatRegisteredEuSubscriptionFlowPage),
      (ThirdCountrySoleTraderSubscriptionFlow, VatRegisteredEuSubscriptionFlowPage, 7, 10, VatEUIdsSubscriptionFlowPage),
      (ThirdCountrySoleTraderSubscriptionFlow, VatEUIdsSubscriptionFlowPage, 8, 10, VatEUConfirmSubscriptionFlowPage),
      (ThirdCountrySoleTraderSubscriptionFlow, VatEUConfirmSubscriptionFlowPage, 9, 10, EoriConsentSubscriptionFlowPage),
      (ThirdCountrySoleTraderSubscriptionFlow, EoriConsentSubscriptionFlowPage, 10, 10, ReviewDetailsPageGetYourEORI),
      (
        MigrationEoriOrganisationSubscriptionFlow,
        EoriNumberSubscriptionFlowPage,
        1,
        4,
        NameUtrDetailsSubscriptionFlowPage
      ),
      (
        MigrationEoriOrganisationSubscriptionFlow,
        NameUtrDetailsSubscriptionFlowPage,
        2,
        4,
        DateOfEstablishmentSubscriptionFlowPageMigrate
      ),
      (
        MigrationEoriOrganisationSubscriptionFlow,
        DateOfEstablishmentSubscriptionFlowPageMigrate,
        3,
        4,
        AddressDetailsSubscriptionFlowPage
      ),
      (
        MigrationEoriOrganisationSubscriptionFlow,
        AddressDetailsSubscriptionFlowPage,
        4,
        4,
        ReviewDetailsPageSubscription
      ),
      (
        MigrationEoriSoleTraderSubscriptionFlow,
        EoriNumberSubscriptionFlowPage,
        1,
        4,
        NameDobDetailsSubscriptionFlowPage
      ),
      (
        MigrationEoriSoleTraderSubscriptionFlow,
        NameDobDetailsSubscriptionFlowPage,
        2,
        4,
        HowCanWeIdentifyYouSubscriptionFlowPage
      ),
      (
        MigrationEoriSoleTraderSubscriptionFlow,
        HowCanWeIdentifyYouSubscriptionFlowPage,
        3,
        4,
        AddressDetailsSubscriptionFlowPage
      ),
      (MigrationEoriSoleTraderSubscriptionFlow, AddressDetailsSubscriptionFlowPage, 4, 4, ReviewDetailsPageSubscription)
    )

    TableDrivenPropertyChecks.forAll(values) {
      (
        flow: SubscriptionFlow,
        currentPage: SubscriptionPage,
        expectedStepNumber: Int,
        expectedTotalSteps: Int,
        expectedNextPage: SubscriptionPage
      ) =>
        when(mockRequestSessionData.userSubscriptionFlow(mockRequest)).thenReturn(flow)
        when(mockRequestSessionData.uriBeforeSubscriptionFlow(mockRequest)).thenReturn(None)
        val actual = controller.stepInformation(currentPage)(mockHC, mockRequest)

        s"${flow.name} flow: current step is $expectedStepNumber when currentPage is $currentPage" in {
          actual.stepNumber shouldBe expectedStepNumber
        }

        s"${flow.name} flow: total Number of steps are $expectedTotalSteps when currentPage is $currentPage" in {
          actual.totalSteps shouldBe expectedTotalSteps
        }

        s"${flow.name} flow: next page is $expectedNextPage when currentPage is $currentPage" in {
          actual.nextPage shouldBe expectedNextPage
        }
    }
  }

  "First Page" should {

    "start Individual Subscription Flow for individual" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)

      when(mockCdsFrontendDataCache.registrationDetails(mockHC))
        .thenReturn(Future.successful(mockIndividualRegistrationDetails))
      val (subscriptionPage, session) = await(
        controller.startSubscriptionFlow(Some(ConfirmIndividualTypePage), Journey.GetYourEORI)(mockHC, mockRequest)
      )

      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession

      verify(mockRequestSessionData)
        .storeUserSubscriptionFlow(IndividualSubscriptionFlow, ConfirmIndividualTypePage.url)(mockRequest)
    }

    "start Corporate Subscription Flow when cached registration details are for an Organisation" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)

      when(mockCdsFrontendDataCache.registrationDetails(mockHC))
        .thenReturn(Future.successful(mockOrgRegistrationDetails))
      val (subscriptionPage, session) =
        await(controller.startSubscriptionFlow(Journey.GetYourEORI)(mockHC, mockRequest))

      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession

      verify(mockRequestSessionData)
        .storeUserSubscriptionFlow(OrganisationSubscriptionFlow, RegistrationConfirmPage.url)(mockRequest)
    }

    "start Corporate Subscription Flow when selected organisation type is Sole Trader" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest))
        .thenReturn(Some(CdsOrganisationType.SoleTrader))

      when(mockCdsFrontendDataCache.registrationDetails(mockHC))
        .thenReturn(Future.successful(mockIndividualRegistrationDetails))
      val (subscriptionPage, session) =
        await(controller.startSubscriptionFlow(Journey.GetYourEORI)(mockHC, mockRequest))

      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession
      verify(mockRequestSessionData).storeUserSubscriptionFlow(SoleTraderSubscriptionFlow, RegistrationConfirmPage.url)(
        mockRequest
      )
    }

    "start Corporate Subscription Flow when cached registration details are for an Organisation Reg-existing (a.k.a migration)" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)
      when(mockCdsFrontendDataCache.registrationDetails(mockHC))
        .thenReturn(Future.successful(mockOrgRegistrationDetails))
      val (subscriptionPage, session) = await(controller.startSubscriptionFlow(Journey.Migrate)(mockHC, mockRequest))

      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession

      verify(mockRequestSessionData)
        .storeUserSubscriptionFlow(MigrationEoriOrganisationSubscriptionFlow, RegistrationConfirmPage.url)(mockRequest)
    }

    "start Corporate Subscription Flow when cached registration details are for an Organisation Reg-existing (a.k.a migration) user location is set to channel islands" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)
      when(mockRequestSessionData.selectedUserLocation(mockRequest)).thenReturn(Some("islands"))

      when(mockCdsFrontendDataCache.registrationDetails(mockHC))
        .thenReturn(Future.successful(mockOrgRegistrationDetails))
      val (subscriptionPage, session) = await(controller.startSubscriptionFlow(Journey.Migrate)(mockHC, mockRequest))

      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession

      verify(mockRequestSessionData).storeUserSubscriptionFlow(
        MigrationEoriRowOrganisationSubscriptionFlow,
        RegistrationConfirmPage.url
      )(mockRequest)
    }
  }
}

class SubscriptionFlowManagerNinoUtrEnabledSpec
    extends UnitSpec with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach with ControllerSpec {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Map("features.rowHaveUtrEnabled" -> true))
    .build()

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockCdsFrontendDataCache = mock[SessionCache]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  val controller =
    new SubscriptionFlowManager(app, mockRequestSessionData, mockCdsFrontendDataCache, mockSubscriptionDetailsService)
  private val mockSession = mock[Session]

  private val mockHC = mock[HeaderCarrier]
  private val mockRequest = mock[Request[AnyContent]]

  val noSubscriptionFlowInSessionException = new IllegalStateException("No subscription flow in session.")

  override def beforeEach(): Unit = {
    reset(mockRequestSessionData, mockSession, mockCdsFrontendDataCache)
    when(mockRequestSessionData.storeUserSubscriptionFlow(any[SubscriptionFlow], any[String])(any[Request[AnyContent]]))
      .thenReturn(mockSession)
    when(mockCdsFrontendDataCache.saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))
  }

  "First Page" should {

    "start Corporate Subscription Flow when selected organisation type is Sole Trader" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest))
        .thenReturn(Some(CdsOrganisationType.SoleTrader))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockCdsFrontendDataCache.registrationDetails(mockHC))
        .thenReturn(Future.successful(RegistrationDetailsIndividual()))

      val (subscriptionPage, session) = await(controller.startSubscriptionFlow(Journey.Migrate)(mockHC, mockRequest))
      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession
      verify(mockRequestSessionData).storeUserSubscriptionFlow(
        MigrationEoriRowIndividualsSubscriptionUtrNinoEnabledFlow,
        RegistrationConfirmPage.url
      )(mockRequest)
    }

    "start Corporate Subscription Flow when selected organisation type is Individual" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest))
        .thenReturn(Some(CdsOrganisationType.Individual))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockCdsFrontendDataCache.registrationDetails(mockHC))
        .thenReturn(Future.successful(RegistrationDetailsIndividual()))

      val (subscriptionPage, session) = await(controller.startSubscriptionFlow(Journey.Migrate)(mockHC, mockRequest))
      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession
      verify(mockRequestSessionData).storeUserSubscriptionFlow(
        MigrationEoriRowIndividualsSubscriptionUtrNinoEnabledFlow,
        RegistrationConfirmPage.url
      )(mockRequest)
    }

    "start Corporate Subscription Flow when cached registration details are for an Organisation" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockCdsFrontendDataCache.registrationDetails(mockHC))
        .thenReturn(Future.successful(RegistrationDetailsOrganisation()))

      val (subscriptionPage, session) = await(controller.startSubscriptionFlow(Journey.Migrate)(mockHC, mockRequest))
      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession
      verify(mockRequestSessionData).storeUserSubscriptionFlow(
        MigrationEoriRowOrganisationSubscriptionUtrNinoEnabledFlow,
        RegistrationConfirmPage.url
      )(mockRequest)
    }
  }
}
