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

package unit.controllers.registration

import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentMatchers.{any, anyString, startsWith, eq => meq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.connector.{SubscriptionDisplayConnector, ServiceUnavailableResponse}
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.SubscriptionRecoveryController
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{RecipientDetails, SubscriptionDetails}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.ContactDetailsModel
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.RandomUUIDGenerator
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{
  HandleSubscriptionService,
  SubscriptionDetailsService,
  TaxEnrolmentsService,
  UpdateVerifiedEmailService
}
import uk.gov.hmrc.customs.rosmfrontend.views.html.error_template
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.SubscriptionInfoBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionRecoveryControllerSpec extends ControllerSpec with MockitoSugar with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockCdsFrontendDataCache: SessionCache = mock[SessionCache]
  private val mockSubscriptionDisplayConnector = mock[SubscriptionDisplayConnector]
  private val mockSubscriptionStatusOutcome = mock[SubscriptionStatusOutcome]
  private val mockHandleSubscriptionService = mock[HandleSubscriptionService]
  private val mockOrgRegistrationDetails = mock[RegistrationDetailsOrganisation]
  private val mockSubscriptionDetailsHolder = mock[SubscriptionDetails]
  private val mockRegisterWithEoriAndIdResponse = mock[RegisterWithEoriAndIdResponse]
  private val mockRandomUUIDGenerator = mock[RandomUUIDGenerator]
  private val contactDetails = mock[ContactDetailsModel]
  private val mockTaxEnrolmentsService = mock[TaxEnrolmentsService]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockUpdateVerifiedEmailService = mock[UpdateVerifiedEmailService]

  private val errorTemplateView = app.injector.instanceOf[error_template]

  private val controller = new SubscriptionRecoveryController(
    app,
    mockAuthConnector,
    mockHandleSubscriptionService,
    mockTaxEnrolmentsService,
    mockCdsFrontendDataCache,
    mockSubscriptionDisplayConnector,
    mcc,
    errorTemplateView,
    mockRandomUUIDGenerator,
    mockRequestSessionData,
    mockSubscriptionDetailsService,
    mockUpdateVerifiedEmailService
  )

  def registerWithEoriAndIdResponseDetail: Option[RegisterWithEoriAndIdResponseDetail] = {
    val trader = Trader(fullName = "New trading", shortName = "nt")
    val establishmentAddress = EstablishmentAddress(streetAndNumber = "new street", city = "leeds", countryCode = "GB")
    val responseData: ResponseData = ResponseData(
      SAFEID = "SomeSafeId",
      trader = trader,
      establishmentAddress = establishmentAddress,
      hasInternetPublication = true,
      startDate = "2018-01-01"
    )
    Some(
      RegisterWithEoriAndIdResponseDetail(
        outcome = Some("PASS"),
        caseNumber = Some("case no 1"),
        responseData = Some(responseData)
      )
    )
  }

  override def beforeEach: Unit = {
    reset(
      mockCdsFrontendDataCache,
      mockOrgRegistrationDetails,
      mockRequestSessionData,
      mockSubscriptionDetailsService,
      mockTaxEnrolmentsService,
      mockUpdateVerifiedEmailService,
      mockHandleSubscriptionService
    )
    when(mockRandomUUIDGenerator.generateUUIDAsString).thenReturn("MOCKUUID12345")
  }

  def setupMockCommon() = {
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockSubscriptionDetailsHolder))
    when(mockSubscriptionDisplayConnector.subscriptionDisplay(any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(Right(fullyPopulatedResponse)))
    when(mockSubscriptionDetailsHolder.contactDetails).thenReturn(Some(contactDetails))
    when(contactDetails.emailAddress).thenReturn("test@example.com")
    when(mockSubscriptionDetailsHolder.email).thenReturn(Some("test@example.com"))
    when(mockCdsFrontendDataCache.email(any[HeaderCarrier])).thenReturn(Future.successful("test@example.com"))
    when(mockCdsFrontendDataCache.saveEori(any[Eori])(any[HeaderCarrier])).thenReturn(Future.successful(true))
    when(mockCdsFrontendDataCache.eori(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some("GBEORI111222111")))
    when(mockSubscriptionDetailsHolder.dateOfBirth).thenReturn(None)
    when(mockSubscriptionDetailsHolder.nameDobDetails)
      .thenReturn(Some(NameDobMatchModel("fname", Some("mName"), "lname", LocalDate.parse("2019-01-01"))))

    when(mockCdsFrontendDataCache.subscriptionStatusOutcome(any[HeaderCarrier])).thenReturn(Future.successful(mockSubscriptionStatusOutcome))
    when(mockSubscriptionStatusOutcome.processedDate).thenReturn("01 May 2016")

    when(mockCdsFrontendDataCache.saveSubscriptionCreateOutcome(any[SubscriptionCreateOutcome])(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))
    when(
      mockHandleSubscriptionService.handleSubscription(
        anyString,
        any[RecipientDetails],
        any[TaxPayerId],
        any[Option[Eori]],
        any[Option[DateTime]],
        any[SafeId]
      )(any[HeaderCarrier], any[ExecutionContext])
    ).thenReturn(Future.successful(result = ()))
    when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(None))
  }

  "Viewing the Organisation Name Matching form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.complete(Journey.GetYourEORI))

    "call Enrolment Complete with successful Subscription Display call for Get Your EORI journey" in {

      setupMockCommon()
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockOrgRegistrationDetails))
      when(mockOrgRegistrationDetails.safeId).thenReturn(SafeId("testsafeId"))

      callEnrolmentComplete(journey = Journey.GetYourEORI) { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result) shouldBe Some("/customs/register-for-cds/complete")
      }
      verify(mockTaxEnrolmentsService, times(0))
        .issuerCall(anyString, any[Eori], any[Option[LocalDate]])(any[HeaderCarrier], any[ExecutionContext])
      verify(mockUpdateVerifiedEmailService, times(0)).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])

    }

    "call Enrolment Complete with successful Subscription Display call for Subscription UK journey" in {
      setupMockCommon()

      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(true)))
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("testEORInumber"))

      when(mockCdsFrontendDataCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockRegisterWithEoriAndIdResponse))
      when(mockRegisterWithEoriAndIdResponse.responseDetail).thenReturn(registerWithEoriAndIdResponseDetail)

      when(
        mockTaxEnrolmentsService
          .issuerCall(anyString, any[Eori], any[Option[LocalDate]])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(NO_CONTENT))

      callEnrolmentComplete(journey = Journey.Migrate) { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result) shouldBe Some("/customs/subscribe-for-cds/complete")
      }

      verify(mockTaxEnrolmentsService, times(1))
        .issuerCall(anyString, any[Eori], any[Option[LocalDate]])(any[HeaderCarrier], any[ExecutionContext])
      verify(mockUpdateVerifiedEmailService, times(1)).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])

    }

    "call Enrolment Complete with successful Subscription Display call for Subscription ROW journey" in {
      setupMockCommon()
      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(true)))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("third-country"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(Utr("someUtr"))))
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("testEORInumber"))
      when(mockCdsFrontendDataCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockRegisterWithEoriAndIdResponse))
      when(mockRegisterWithEoriAndIdResponse.responseDetail).thenReturn(registerWithEoriAndIdResponseDetail)

      when(
        mockTaxEnrolmentsService
          .issuerCall(anyString, any[Eori], any[Option[LocalDate]])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(NO_CONTENT))

      callEnrolmentComplete(journey = Journey.Migrate) { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result) shouldBe Some("/customs/subscribe-for-cds/complete")
      }

      verify(mockTaxEnrolmentsService).issuerCall(anyString, any[Eori], any[Option[LocalDate]])(
        any[HeaderCarrier],
        any[ExecutionContext]
      )
      verify(mockUpdateVerifiedEmailService).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])
    }

    "call Enrolment Complete with successful Subscription Display call for Subscription ROW journey throw IllegalArgumentException when update verified email failed" in {
      setupMockCommon()
      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(false)))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("third-country"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(Utr("someUtr"))))

      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("testEORInumber"))
      when(mockCdsFrontendDataCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockRegisterWithEoriAndIdResponse))
      when(mockRegisterWithEoriAndIdResponse.responseDetail).thenReturn(registerWithEoriAndIdResponseDetail)

      when(
        mockTaxEnrolmentsService
          .issuerCall(anyString, any[Eori], any[Option[LocalDate]])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(NO_CONTENT))
      the[IllegalArgumentException] thrownBy {
        callEnrolmentComplete(journey = Journey.Migrate) { result =>
          await(result)
        }
      }
      verify(mockTaxEnrolmentsService, times(0))
        .issuerCall(anyString, any[Eori], any[Option[LocalDate]])(any[HeaderCarrier], any[ExecutionContext])
      verify(mockUpdateVerifiedEmailService, times(1)).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])

    }

    "call Enrolment Complete with successful Subscription Display call for Subscription ROW journey without Identifier" in {
      setupMockCommon()
      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(true)))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("third-country"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("testEORInumber"))
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockOrgRegistrationDetails))
      when(mockOrgRegistrationDetails.safeId).thenReturn(SafeId("testsafeId"))

      when(
        mockTaxEnrolmentsService
          .issuerCall(anyString, any[Eori], any[Option[LocalDate]])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(NO_CONTENT))

      callEnrolmentComplete(journey = Journey.Migrate) { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result) shouldBe Some("/customs/subscribe-for-cds/complete")
      }
      verify(mockTaxEnrolmentsService).issuerCall(anyString, any[Eori], any[Option[LocalDate]])(
        any[HeaderCarrier],
        any[ExecutionContext]
      )
      verify(mockUpdateVerifiedEmailService).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])

    }

    "call Enrolment Complete with unsuccessful Subscription Display call" in {
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockOrgRegistrationDetails))
      when(mockOrgRegistrationDetails.safeId).thenReturn(SafeId("testSapNumber"))
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscriptionDetailsHolder))
      when(mockSubscriptionDisplayConnector.subscriptionDisplay(any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Left(ServiceUnavailableResponse)))

      when(mockCdsFrontendDataCache.subscriptionStatusOutcome(any[HeaderCarrier])).thenReturn(Future.successful(mockSubscriptionStatusOutcome))
      when(mockCdsFrontendDataCache.saveSubscriptionCreateOutcome(any[SubscriptionCreateOutcome])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))
      when(
        mockHandleSubscriptionService.handleSubscription(
          anyString,
          any[RecipientDetails],
          any[TaxPayerId],
          any[Option[Eori]],
          any[Option[DateTime]],
          any[SafeId]
        )(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(result = ()))

      callEnrolmentComplete(journey = Journey.GetYourEORI) { result =>
        status(result) shouldBe SERVICE_UNAVAILABLE
      }
    }

    "call Enrolment Complete with successful Subscription Display call for Subscription UK journey for formbundle with cds" in {
      setupMockCommon()
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("testEORInumber"))
      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(true)))
      when(mockCdsFrontendDataCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockRegisterWithEoriAndIdResponse))
      when(mockRegisterWithEoriAndIdResponse.responseDetail).thenReturn(registerWithEoriAndIdResponseDetail)

      when(
        mockTaxEnrolmentsService
          .issuerCall(anyString, any[Eori], any[Option[LocalDate]])(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(Future.successful(NO_CONTENT))

      val expectedFormBundleId = fullyPopulatedResponse.responseCommon.returnParameters
        .flatMap(_.find(_.paramName.equals("ETMPFORMBUNDLENUMBER")).map(_.paramValue))
        .get

      callEnrolmentComplete(journey = Journey.Migrate) { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result) shouldBe Some("/customs/subscribe-for-cds/complete")
      }

      verify(mockTaxEnrolmentsService).issuerCall(
        startsWith(expectedFormBundleId),
        meq(Eori("testEORInumber")),
        any[Option[LocalDate]]
      )(any[HeaderCarrier], any[ExecutionContext])

      verify(mockHandleSubscriptionService).handleSubscription(
        startsWith(expectedFormBundleId),
        any(),
        any(),
        meq(Some(Eori("testEORInumber"))),
        any(),
        any()
      )(any(), any())
    }
  }

  "call Enrolment Complete with successful Subscription Display call with empty ResponseCommon should throw IllegalArgumentException" in {
    when(mockCdsFrontendDataCache.saveEori(any[Eori])(any[HeaderCarrier])).thenReturn(Future.successful(true))
    when(mockCdsFrontendDataCache.eori(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some("GBEORI111222111")))
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockOrgRegistrationDetails))
    when(mockOrgRegistrationDetails.safeId).thenReturn(SafeId("testSapNumber"))
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockSubscriptionDetailsHolder))
    when(mockSubscriptionDisplayConnector.subscriptionDisplay(any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(Right(responseWithoutEmailAddress)))
    when(mockCdsFrontendDataCache.subscriptionStatusOutcome(any[HeaderCarrier])).thenReturn(Future.successful(mockSubscriptionStatusOutcome))

    callEnrolmentComplete(journey = Journey.GetYourEORI) { result =>
      status(result) shouldBe SEE_OTHER
      header(LOCATION, result) shouldBe Some("/customs/register-for-cds/eori-exist")
    }
    verify(mockUpdateVerifiedEmailService, times(0)).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])

  }

  "call Enrolment Complete with successful Subscription Display call with ResponseCommon with no ETMPFORMBUNDLENUMBER should throw IllegalStateException" in {
    when(mockCdsFrontendDataCache.saveEori(any[Eori])(any[HeaderCarrier])).thenReturn(Future.successful(true))
    when(mockCdsFrontendDataCache.eori(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some("GBEORI111222111")))
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockOrgRegistrationDetails))
    when(mockOrgRegistrationDetails.safeId).thenReturn(SafeId("testSapNumber"))
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockSubscriptionDetailsHolder))
    when(mockSubscriptionDisplayConnector.subscriptionDisplay(any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(Right(fullyPopulatedResponseWithNoETMPFORMBUNDLENUMBER)))
    when(mockCdsFrontendDataCache.subscriptionStatusOutcome(any[HeaderCarrier])).thenReturn(Future.successful(mockSubscriptionStatusOutcome))

    the[IllegalStateException] thrownBy {
      callEnrolmentComplete(journey = Journey.GetYourEORI) { result =>
        await(result)
      }
    } should have message "NO ETMPFORMBUNDLENUMBER specified"
  }

  "call Enrolment Complete with successful Subscription Display call with empty ContactDetails should throw IllegalStateException" in {
    when(mockCdsFrontendDataCache.saveEori(any[Eori])(any[HeaderCarrier])).thenReturn(Future.successful(true))
    when(mockCdsFrontendDataCache.eori(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some("GBEORI111222111")))
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockOrgRegistrationDetails))
    when(mockOrgRegistrationDetails.safeId).thenReturn(SafeId("testSapNumber"))
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockSubscriptionDetailsHolder))
    when(mockSubscriptionDisplayConnector.subscriptionDisplay(any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(Right(responseWithoutContactDetails)))
    when(mockCdsFrontendDataCache.subscriptionStatusOutcome(any[HeaderCarrier])).thenReturn(Future.successful(mockSubscriptionStatusOutcome))
    callEnrolmentComplete(journey = Journey.GetYourEORI) { result =>
      status(result) shouldBe SEE_OTHER
      header(LOCATION, result) shouldBe Some("/customs/register-for-cds/eori-exist")
    }
  }

  "call Enrolment Complete with successful Subscription Display call without EmailAddress should throw IllegalStateException" in {
    when(mockCdsFrontendDataCache.saveEori(any[Eori])(any[HeaderCarrier])).thenReturn(Future.successful(true))
    when(mockCdsFrontendDataCache.eori(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some("GBEORI111222111")))
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockOrgRegistrationDetails))
    when(mockOrgRegistrationDetails.safeId).thenReturn(SafeId("testSapNumber"))
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockSubscriptionDetailsHolder))
    when(mockSubscriptionDisplayConnector.subscriptionDisplay(any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(Right(responseWithoutEmailAddress)))
    when(mockCdsFrontendDataCache.subscriptionStatusOutcome(any[HeaderCarrier])).thenReturn(Future.successful(mockSubscriptionStatusOutcome))

    callEnrolmentComplete(journey = Journey.GetYourEORI) { result =>
      status(result) shouldBe SEE_OTHER
      header(LOCATION, result) shouldBe Some("/customs/register-for-cds/eori-exist")
    }
  }

  "call Enrolment Complete with successful Subscription Display call without personOfContact should not throw exception" in {
    when(mockCdsFrontendDataCache.saveEori(any[Eori])(any[HeaderCarrier])).thenReturn(Future.successful(true))
    when(mockCdsFrontendDataCache.eori(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some("GBEORI111222111")))
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockSubscriptionDetailsHolder))
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockOrgRegistrationDetails))
    when(mockOrgRegistrationDetails.safeId).thenReturn(SafeId("testSapNumber"))

    when(mockSubscriptionDisplayConnector.subscriptionDisplay(any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(Right(responseWithoutPersonOfContact)))
    when(mockCdsFrontendDataCache.subscriptionStatusOutcome(any[HeaderCarrier])).thenReturn(Future.successful(mockSubscriptionStatusOutcome))
    when(mockCdsFrontendDataCache.saveSubscriptionCreateOutcome(any[SubscriptionCreateOutcome])(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))
    when(
      mockHandleSubscriptionService.handleSubscription(
        anyString,
        any[RecipientDetails],
        any[TaxPayerId],
        any[Option[Eori]],
        any[Option[DateTime]],
        any[SafeId]
      )(any[HeaderCarrier], any[ExecutionContext])
    ).thenReturn(Future.successful(result = ()))

    callEnrolmentComplete(journey = Journey.GetYourEORI) { result =>
      status(result) shouldBe SEE_OTHER
      header(LOCATION, result) shouldBe Some("/customs/register-for-cds/complete")
    }
  }

  def callEnrolmentComplete(userId: String = defaultUserId, journey: Journey.Value)(test: Future[Result] => Any) {

    withAuthorisedUser(userId, mockAuthConnector)
    test(controller.complete(journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }
}
