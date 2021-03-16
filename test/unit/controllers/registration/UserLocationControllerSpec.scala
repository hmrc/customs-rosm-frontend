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

import java.util.UUID

import common.pages.registration.UserLocationPageOrganisation._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, mock => _}
import play.api.mvc.{AnyContent, Request, Result, Session}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.connector.ServiceUnavailableResponse
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.UserLocationController
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.SignInWithDifferentDetailsController
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Address
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching.{
  ContactResponse,
  IndividualResponse,
  OrganisationResponse
}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.registration.{
  RegistrationDisplayResponse,
  ResponseCommon,
  ResponseDetail
}
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{
  BusinessDetailsRecoveryPage,
  ContactDetailsSubscriptionFlowPageGetEori,
  SubscriptionPage,
  UserLocationPage
}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.Save4LaterService
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegistrationDisplayService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription._
import uk.gov.hmrc.customs.rosmfrontend.views.html.error_template
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.user_location
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.{subscription_status_outcome_processing, subscription_status_outcome_rejected}
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.UserLocationFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UserLocationControllerSpec extends ControllerSpec with MockitoSugar with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSessionCache = mock[SessionCache]
  private val mockSave4LaterService = mock[Save4LaterService]
  private val mockSubscriptionStatusService = mock[SubscriptionStatusService]
  private val mockTaxEnrolmentsService = mock[TaxEnrolmentsService]
  private val mockRegistrationDisplayService = mock[RegistrationDisplayService]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]

  private val userLocationView = app.injector.instanceOf[user_location]
  private val subscriptionStatusOutcomeProcessing =
    app.injector.instanceOf[subscription_status_outcome_processing]
  private val subscriptionStatusOutcomeRejected =
    app.injector.instanceOf[subscription_status_outcome_rejected]
  private val errorTemplate = app.injector.instanceOf[error_template]

  private val controller = new UserLocationController(
    app,
    mockAuthConnector,
    mockRequestSessionData,
    mockSave4LaterService,
    mockSubscriptionStatusService,
    mockTaxEnrolmentsService,
    mockSessionCache,
    mockRegistrationDisplayService,
    mcc,
    userLocationView,
    subscriptionStatusOutcomeProcessing,
    subscriptionStatusOutcomeRejected,
    errorTemplate
  )

  private val ProblemWithSelectionError = "Tell us where you are based"
  private val ProblemWithSelectionFieldError = "Error:Tell us where you are based"

  private val locationFieldName = "location"

  override def beforeEach(): Unit = {
    reset(
      mockRequestSessionData,
      mockSave4LaterService,
      mockSubscriptionStatusService,
      mockTaxEnrolmentsService,
      mockRegistrationDisplayService,
      mockSubscriptionFlowManager
    )
    when(mockRequestSessionData.sessionWithOrganisationTypeAdded(any(), any()))
      .thenReturn(Session())
    when(
      mockRequestSessionData
        .sessionWithUserLocationAdded(any[String])(any[Request[AnyContent]])
    ).thenReturn(Session())
    when(
      mockRequestSessionData.existingSessionWithUserLocationAdded(any[Session], any[String])(any[Request[AnyContent]])
    ).thenReturn(Session())
    when(mockRegistrationDisplayService.cacheDetails(any())(any(), any()))
      .thenReturn(Future.successful(true))
    when(mockSave4LaterService.fetchSafeId(any[InternalId]())(any[HeaderCarrier]())).thenReturn(Future.successful(None))
    when(
      mockEnrolmentStoreProxyService
        .isEnrolmentAssociatedToGroup(any())(any(), any())
    ).thenReturn(Future.successful(false))

  }

  "Viewing the user location form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.form(Journey.GetYourEORI))
    "display the form with no errors" in {
      showForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe empty
      }
    }
  }

  "Submitting the form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.submit(Journey.GetYourEORI))

    "ensure a location option has been selected" in {
      submitForm(ValidRequest - locationFieldName) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe ProblemWithSelectionError
        page.getElementsText(fieldLevelErrorLocation) shouldBe ProblemWithSelectionFieldError
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "ensure a valid answer option has been selected" in {
      val invalidOption = UUID.randomUUID.toString
      submitForm(ValidRequest + (locationFieldName -> invalidOption)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe ProblemWithSelectionError
        page.getElementsText(fieldLevelErrorLocation) shouldBe ProblemWithSelectionFieldError
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "redirect to uk vat registered page  when 'iom' is selected" in {
      when(mockSave4LaterService.fetchSafeId(any[InternalId])(any[HeaderCarrier])).thenReturn(Future.successful(None))

      submitForm(ValidRequest + (locationFieldName -> UserLocation.Iom)) { result =>
        status(result) shouldBe SEE_OTHER
        val expectedUrl =
          YouNeedADifferentServiceIomController.form(Journey.GetYourEORI).url
        result.header.headers(LOCATION) should endWith(expectedUrl)
      }
    }

    assertCorrectSessionDataAndRedirect(UserLocation.Uk)

    assertCorrectSessionDataAndRedirect(UserLocation.Eu)

    assertCorrectSessionDataAndRedirect(UserLocation.ThirdCountry)

    assertCorrectSessionDataAndRedirect(UserLocation.Islands)
  }

  "subscriptionStatus when sessionInfoBasedOnJourney for Row" should {
    "redirect to OrganisationTypeController form when 'Iom' is selected" in {
      subscriptionStatus() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith(OrganisationTypeController.form(Journey.GetYourEORI).url)
      }
    }

    "return IllegalStateException when invalid location is selected" in {
      val error = intercept[IllegalStateException] {
        subscriptionStatus(location = Some("location")) { result =>
          await(result)
        }
      }
      error.getMessage shouldBe "User Location not set"
    }

    "return IllegalStateException when Migrate journey is selected and location is not set" in {
      val error = intercept[IllegalStateException] {
        subscriptionStatus(Journey.Migrate, None) { result =>
          await(result)
        }
      }
      error.getMessage shouldBe "User Location not set"
    }
  }

  "cacheAndRedirect when registrationDisplay is Enabled" should {
    val controller = new UserLocationController(
      app,
      mockAuthConnector,
      mockRequestSessionData,
      mockSave4LaterService,
      mockSubscriptionStatusService,
      mockTaxEnrolmentsService,
      mockSessionCache,
      mockRegistrationDisplayService,
      mcc,
      userLocationView,
      subscriptionStatusOutcomeProcessing,
      subscriptionStatusOutcomeRejected,
      errorTemplate
    ) {}
    implicit val fakeRequest = FakeRequest()

    "cache registration display response and redirect to BusinessDetailsRecoveryPage for individual response" in {
      val mockSession = mock[Session]
      val mockFlowStart = (BusinessDetailsRecoveryPage, mockSession)
      val responseDetail = ResponseDetail(
        "",
        None,
        None,
        false,
        false,
        true,
        Some(mock[IndividualResponse]),
        None,
        mock[Address],
        mock[ContactResponse]
      )
      when(
        mockSubscriptionFlowManager.startSubscriptionFlow(
          meq(Some(UserLocationPage)),
          meq(CdsOrganisationType.ThirdCountryIndividual),
          meq(Journey.GetYourEORI)
        )(any[HeaderCarrier], any[Request[AnyContent]])
      ).thenReturn(Future.successful(mockFlowStart))

      val test =
        controller.cacheAndRedirect(Journey.GetYourEORI, "third-country")
      val result = await(test(Right(RegistrationDisplayResponse(mock[ResponseCommon], Some(responseDetail)))))

      status(result) shouldBe SEE_OTHER
      result.header.headers(LOCATION) should endWith(BusinessDetailsRecoveryController.form(Journey.GetYourEORI).url)
    }

    "cache registration display response and redirect to BusinessDetailsRecoveryPage for organisation response" in {
      val mockSession = mock[Session]
      val mockFlowStart = (BusinessDetailsRecoveryPage, mockSession)
      val responseDetail = ResponseDetail(
        "",
        None,
        None,
        false,
        false,
        false,
        None,
        Some(mock[OrganisationResponse]),
        mock[Address],
        mock[ContactResponse]
      )

      when(
        mockSubscriptionFlowManager.startSubscriptionFlow(
          meq(Some(UserLocationPage)),
          meq(CdsOrganisationType.ThirdCountryOrganisation),
          meq(Journey.GetYourEORI)
        )(any[HeaderCarrier], any[Request[AnyContent]])
      ).thenReturn(Future.successful(mockFlowStart))

      val test =
        controller.cacheAndRedirect(Journey.GetYourEORI, "third-country")
      val result = await(test(Right(RegistrationDisplayResponse(mock[ResponseCommon], Some(responseDetail)))))

      status(result) shouldBe SEE_OTHER
      result.header.headers(LOCATION) should endWith(BusinessDetailsRecoveryController.form(Journey.GetYourEORI).url)
    }

    "return service unavailable response when failed to retrieve registration display response" in {
      val test =
        controller.cacheAndRedirect(Journey.GetYourEORI, "third-country")
      val result = await(test(Left(ServiceUnavailableResponse)))

      status(result) shouldBe SERVICE_UNAVAILABLE
    }
  }

  private def showForm(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    test(
      controller
        .form(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSession(userId))
    )
  }

  private def submitForm(form: Map[String, String], userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    test(
      controller
        .submit(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  private def processing(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    test(
      controller.processing
        .apply(SessionBuilder.buildRequestWithSession(userId))
    )
  }

  private def rejected(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    test(controller.rejected.apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def subscriptionStatus(
    journey: Journey.Value = Journey.GetYourEORI,
    location: Option[String] = Some(UserLocation.Iom)
  )(test: Future[Result] => Any) = {

    val subStatus: PreSubscriptionStatus = NewSubscription
    implicit val hc: HeaderCarrier = mock[HeaderCarrier]
    implicit val rq: Request[AnyContent] = mock[Request[AnyContent]]

    test(controller.subscriptionStatus(subStatus, InternalId("InternalID"), journey, location)(rq, hc))
  }

  private def assertCorrectSessionDataAndRedirect(selectedOptionValue: String): Unit = {
    s"store the correct organisation type when '$selectedOptionValue' is selected" in {
      val selectedOptionToJourney = selectedOptionValue match {
        case UserLocation.Eu           => "eu"
        case UserLocation.ThirdCountry => "third-country"
        case UserLocation.Uk           => "uk"
        case UserLocation.Iom          => "iom"
        case UserLocation.Islands      => "islands"
      }

      when(mockSave4LaterService.fetchSafeId(any[InternalId])(any[HeaderCarrier])).thenReturn(Future.successful(None))

      submitForm(ValidRequest + (locationFieldName -> selectedOptionValue)) { result =>
        status(result)
        verify(mockRequestSessionData).sessionWithUserLocationAdded(ArgumentMatchers.eq(selectedOptionToJourney))(
          any[Request[AnyContent]]
        )
      }
    }

    if (selectedOptionValue != UserLocation.Uk) {

      s"redirect to SubscriptionProcessing page when '$selectedOptionValue' is selected" in {
        when(mockSave4LaterService.fetchSafeId(any[InternalId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(SafeId("safeid"))))
        when(
          mockSubscriptionStatusService
            .getStatus(any[String], any[String])(any[HeaderCarrier])
        ).thenReturn(Future.successful(SubscriptionProcessing))

        submitForm(ValidRequest + (locationFieldName -> selectedOptionValue)) { result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) should endWith(UserLocationController.processing().url)
        }
      }

      s"redirect to SignInWithDifferentDetailsController when SubscriptionExists status and enrolment exists is true page when '$selectedOptionValue' is selected" in {
        when(mockSave4LaterService.fetchSafeId(any[InternalId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(SafeId("safeid"))))
        when(
          mockSubscriptionStatusService
            .getStatus(any[String], any[String])(any[HeaderCarrier])
        ).thenReturn(Future.successful(SubscriptionExists))
        when(mockTaxEnrolmentsService.doesEnrolmentExist(any[SafeId])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(true))
        when(mockSessionCache.saveRegistrationDetails(any[RegistrationDetailsSafeId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(true))

        submitForm(ValidRequest + (locationFieldName -> selectedOptionValue)) { result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) should endWith(
            SignInWithDifferentDetailsController.form(Journey.GetYourEORI).url
          )
        }
      }

      s"redirect to CompleteEnrolmentAfterSubscriptionTimeoutController when SubscriptionExists status and enrolment exists is false and when '$selectedOptionValue' is selected" in {
        when(mockSave4LaterService.fetchSafeId(any[InternalId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(SafeId("safeid"))))
        when(
          mockSubscriptionStatusService
            .getStatus(any[String], any[String])(any[HeaderCarrier])
        ).thenReturn(Future.successful(SubscriptionExists))
        when(mockTaxEnrolmentsService.doesEnrolmentExist(any[SafeId])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(false))

        submitForm(ValidRequest + (locationFieldName -> selectedOptionValue)) { result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) should endWith(
            SubscriptionRecoveryController
              .complete(Journey.GetYourEORI)
              .url
          )
        }
      }
      s"redirect to BusinessDetailsRecoveryController when NewSubscription status and registration display is enabled and when '$selectedOptionValue' is selected" in {
        val mockResponseCommon = mock[ResponseCommon]
        val mockResponseDetail = mock[ResponseDetail]
        val mockSession = mock[Session]
        val mockFlowStart =
          (ContactDetailsSubscriptionFlowPageGetEori, mockSession)

        when(mockSave4LaterService.fetchSafeId(any[InternalId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(SafeId("safeid"))))
        when(
          mockSubscriptionStatusService
            .getStatus(any[String], any[String])(any[HeaderCarrier])
        ).thenReturn(Future.successful(SubscriptionRejected))
        when(mockRegistrationDisplayService.requestDetails(any())(any(), any()))
          .thenReturn(
            Future.successful(Right(RegistrationDisplayResponse(mockResponseCommon, Some(mockResponseDetail))))
          )
        when(
          mockSubscriptionFlowManager.startSubscriptionFlow(
            any[Option[SubscriptionPage]],
            any[CdsOrganisationType],
            any[Journey.Value]
          )(any[HeaderCarrier], any[Request[AnyContent]])
        ).thenReturn(Future.successful(mockFlowStart))

        val controller = new UserLocationController(
          app,
          mockAuthConnector,
          mockRequestSessionData,
          mockSave4LaterService,
          mockSubscriptionStatusService,
          mockTaxEnrolmentsService,
          mockSessionCache,
          mockRegistrationDisplayService,
          mcc,
          userLocationView,
          subscriptionStatusOutcomeProcessing,
          subscriptionStatusOutcomeRejected,
          errorTemplate
        ) {}

        val result = controller
          .submit(Journey.GetYourEORI)
          .apply(
            SessionBuilder.buildRequestWithSessionAndFormValues(
              defaultUserId,
              ValidRequest + (locationFieldName -> selectedOptionValue)
            )
          )

        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith(BusinessDetailsRecoveryController.form(Journey.GetYourEORI).url)
      }

      s"redirect to BusinessDetailsRecoveryController when SubscriptionRejected status and registration display is enabled and when '$selectedOptionValue' is selected" in {
        val mockResponseCommon = mock[ResponseCommon]
        val mockResponseDetail = mock[ResponseDetail]
        val mockSession = mock[Session]
        val mockFlowStart =
          (ContactDetailsSubscriptionFlowPageGetEori, mockSession)

        when(mockSave4LaterService.fetchSafeId(any[InternalId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(SafeId("safeid"))))
        when(
          mockSubscriptionStatusService
            .getStatus(any[String], any[String])(any[HeaderCarrier])
        ).thenReturn(Future.successful(NewSubscription))
        when(mockRegistrationDisplayService.requestDetails(any())(any(), any()))
          .thenReturn(
            Future.successful(Right(RegistrationDisplayResponse(mockResponseCommon, Some(mockResponseDetail))))
          )
        when(
          mockSubscriptionFlowManager.startSubscriptionFlow(
            any[Option[SubscriptionPage]],
            any[CdsOrganisationType],
            any[Journey.Value]
          )(any[HeaderCarrier], any[Request[AnyContent]])
        ).thenReturn(Future.successful(mockFlowStart))

        val controller = new UserLocationController(
          app,
          mockAuthConnector,
          mockRequestSessionData,
          mockSave4LaterService,
          mockSubscriptionStatusService,
          mockTaxEnrolmentsService,
          mockSessionCache,
          mockRegistrationDisplayService,
          mcc,
          userLocationView,
          subscriptionStatusOutcomeProcessing,
          subscriptionStatusOutcomeRejected,
          errorTemplate
        ) {}

        val result = controller
          .submit(Journey.GetYourEORI)
          .apply(
            SessionBuilder.buildRequestWithSessionAndFormValues(
              defaultUserId,
              ValidRequest + (locationFieldName -> selectedOptionValue)
            )
          )

        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith(BusinessDetailsRecoveryController.form(Journey.GetYourEORI).url)
      }

    } else if (selectedOptionValue == UserLocation.Uk) {

      s"redirect to organisation type page  when '$selectedOptionValue' is selected" in {
        when(mockSave4LaterService.fetchSafeId(any[InternalId])(any[HeaderCarrier])).thenReturn(Future.successful(None))

        submitForm(ValidRequest + (locationFieldName -> selectedOptionValue)) { result =>
          status(result) shouldBe SEE_OTHER
          val expectedUrl =
            OrganisationTypeController.form(Journey.GetYourEORI).url
          result.header.headers(LOCATION) should endWith(expectedUrl)
        }
      }
    }
  }

  "Viewing application state" should {
    "redirect to the processing page" in {

      val processedDate = SubscriptionStatusOutcome("01/01/2011")

      when(mockSessionCache.subscriptionStatusOutcome(any[HeaderCarrier]))
        .thenReturn(Future.successful(processedDate))
      processing() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("The EORI application is being processed")
      }
    }
  }

}
