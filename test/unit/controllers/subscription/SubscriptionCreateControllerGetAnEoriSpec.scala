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

import common.pages.{RegistrationCompletePage, RegistrationRejectedPage}
import common.support.testdata.TestData
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.connector.PdfGeneratorConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.{routes, SubscriptionCreateController}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.SubscriptionCreateResponse._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription._
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.migration_success
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription._
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder._
import util.builders.SessionBuilder

import scala.concurrent.Future

class SubscriptionCreateControllerGetAnEoriSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSessionCache = mock[SessionCache]
  private val mockCdsSubscriber = mock[CdsSubscriber]
  private val mockCdsOrganisationType = mock[CdsOrganisationType]
  private val mockPdfGeneratorService = mock[PdfGeneratorConnector]
  private val mockRegDetails = mock[RegistrationDetails]
  private val mockSubscribeOutcome = mock[SubscriptionCreateOutcome]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]

  private val migrationSuccessView = app.injector.instanceOf[migration_success]
  private val subscriptionStatusOutcomeView = app.injector.instanceOf[subscription_status_outcome_processing]
  private val subscriptionCreateRequestNotProcessed = app.injector.instanceOf[subscription_create_request_not_processed]
  private val subscriptionCreateSubscriptionInProgressView = app.injector.instanceOf[subscription_create_subscription_in_progress]
  private val subscriptionCreateEoriAlreadyAssociatedView = app.injector.instanceOf[subscription_create_eori_already_associated]
  private val subscriptionCreateEoriAlreadyExists = app.injector.instanceOf[subscription_create_eori_already_exists]
  private val subscriptionStatusOutcomeRejected = app.injector.instanceOf[subscription_status_outcome_rejected]
  private val subscriptionOutcomeView = app.injector.instanceOf[subscription_outcome]
  private val subscriptionOutcomeGuidanceView = app.injector.instanceOf[subscription_outcome_xieori_guidance]
  private val EORI = "ZZZ1ZZZZ23ZZZZZZZ"

  private val subscriptionController = new SubscriptionCreateController(
    app,
    mockAuthConnector,
    mockRequestSessionData,
    mockSessionCache,
    mockSubscriptionDetailsService,
    mcc,
    migrationSuccessView,
    subscriptionStatusOutcomeView,
    subscriptionCreateRequestNotProcessed,
    subscriptionCreateSubscriptionInProgressView,
    subscriptionCreateEoriAlreadyAssociatedView,
    subscriptionCreateEoriAlreadyExists,
    subscriptionStatusOutcomeRejected,
    subscriptionOutcomeView,
    subscriptionOutcomeGuidanceView,
    mockCdsSubscriber
  )

  val eoriNumberResponse: String = "EORI-Number"
  val formBundleIdResponse: String = "Form-Bundle-Id"
  private val processingDate = "12 May 2018"
  val emailVerificationTimestamp: DateTime = TestData.emailVerificationTimestamp
  val emulatedFailure = new UnsupportedOperationException("Emulated service call failure.")

  override def beforeEach: Unit = {
    reset(mockAuthConnector, mockCdsSubscriber, mockPdfGeneratorService, mockSessionCache)
    when(mockSubscriptionDetailsService.saveKeyIdentifiers(any[GroupId], any[InternalId])(any()))
      .thenReturn(Future.successful(()))

  }

  "Subscribe" should {
    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      subscriptionController.subscribe(Journey.GetYourEORI)
    )
  }

  "End" should {
    assertNotLoggedInUserShouldBeRedirectedToLoginPage(
      mockAuthConnector,
      "Access the end page",
      subscriptionController.end()
    )
  }

  "Rejected" should {
    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, subscriptionController.rejected)
  }

  "clicking on the register button" should {

    "subscribe using selected organisation type when available" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
          Future.successful(
            SubscriptionSuccessful(
              Eori(eoriNumberResponse),
              formBundleIdResponse,
              processingDate,
              Some(emailVerificationTimestamp)
            )
          )
        )
      subscribeForGetYourEORI(organisationTypeOption = Some(mockCdsOrganisationType)) { result =>
        {
          await(result)
          verify(mockCdsSubscriber).subscribeWithCachedDetails(
            meq(Some(mockCdsOrganisationType)),
            meq(Journey.GetYourEORI)
          )(any[HeaderCarrier], any[Request[AnyContent]])
        }
      }
    }

    "subscribe without a selected organisation type when selection is not available (automatic matching case)" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
          Future.successful(
            SubscriptionSuccessful(
              Eori(eoriNumberResponse),
              formBundleIdResponse,
              processingDate,
              Some(emailVerificationTimestamp)
            )
          )
        )
      subscribeForGetYourEORI(organisationTypeOption = None) { result =>
        {
          await(result)
          verify(mockCdsSubscriber).subscribeWithCachedDetails(meq(None), meq(Journey.GetYourEORI))(
            any[HeaderCarrier],
            any[Request[AnyContent]]
          )
        }
      }
    }

    "redirect to 'Application complete' page with EORI number when subscription successful" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
          Future.successful(
            SubscriptionSuccessful(
              Eori(eoriNumberResponse),
              formBundleIdResponse,
              processingDate,
              Some(emailVerificationTimestamp)
            )
          )
        )

      subscribeForGetYourEORI() { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe routes.SubscriptionCreateController.end().url
        }
      }
    }

    "redirect to 'Registration in review' page when subscription returns pending status" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
          Future.successful(SubscriptionPending(formBundleIdResponse, processingDate, Some(emailVerificationTimestamp)))
        )

      subscribeForGetYourEORI() { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe routes.SubscriptionCreateController.pending().url
        }
      }
    }

    "redirect to 'Registration rejected' page when subscription returns failed status" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(Future.successful(SubscriptionFailed("Subscription application has been rejected", processingDate)))

      subscribeForGetYourEORI() { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe routes.SubscriptionCreateController.rejected().url
        }
      }
    }

    "redirect to 'eori already exists' page when subscription returns failed status with EORI already exists status text" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(Future.successful(SubscriptionFailed(EoriAlreadyExists, processingDate)))

      subscribeForGetYourEORI() { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe routes.SubscriptionCreateController.eoriAlreadyExists().url
        }
      }
    }

    "redirect to 'eori already associated' page when subscription returns failed status when the provided EORI already associated to Business partner record" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(Future.successful(SubscriptionFailed(EoriAlreadyAssociated, processingDate)))

      subscribeForGetYourEORI() { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe routes.SubscriptionCreateController.eoriAlreadyAssociated().url
        }
      }
    }

    "redirect to 'subscription in-progress' page when subscription returns failed status with Subscription is already in-progress status text" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(Future.successful(SubscriptionFailed(SubscriptionInProgress, processingDate)))

      subscribeForGetYourEORI() { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe routes.SubscriptionCreateController.subscriptionInProgress().url
        }
      }
    }

    "redirect to 'request not processed' page when subscription returns failed status when the request could not be processed" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(Future.successful(SubscriptionFailed(RequestNotProcessed, processingDate)))

      subscribeForGetYourEORI() { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.SubscriptionCreateController
            .requestNotProcessed()
            .url
        }
      }
    }

    "fail when subscription fails unexpectedly" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(Future.failed(emulatedFailure))

      val caught = intercept[Exception] {
        subscribeForGetYourEORI() { result =>
          await(result)
        }
      }
      caught.getMessage shouldBe "Subscription Error. "
      caught.getCause shouldBe emulatedFailure
    }

    "allow authenticated users to access the end page" in {
      invokeEndPageWithAuthenticatedUser() {
        mockSessionCacheForOutcomePage
        when(mockSubscribeOutcome.eori).thenReturn(Some(EORI))
        when(mockSubscribeOutcome.fullName).thenReturn("orgName")

        result =>
          status(result) shouldBe OK
          val page = CdsPage(bodyOf(result))
          verify(mockSessionCache).remove(any[HeaderCarrier])
          page.title should startWith("Application complete")
          page.getElementsText(RegistrationCompletePage.pageHeadingXpath) shouldBe s"The EORI number for orgName is $EORI"
          page.getElementsText(RegistrationCompletePage.eoriXpath) shouldBe EORI
          page.getElementsText(RegistrationCompletePage.issuedDateXpath) shouldBe "issued by HMRC on 22 May 2016"

          page.getElementsText(RegistrationCompletePage.additionalInformationXpath) should include(
            messages("cds.subscription.outcomes.success.optional-paragraph")
          )
          page.getElementsText(RegistrationCompletePage.whatHappensNextXpath) shouldBe
            strim("""
                |Your EORI number will be ready to use within 48 hours. It will appear on the check an EORI service within 48 hours. It has no expiry date.
                |You should only share it with trusted business partners or approved customs representatives. For example, you should give it to your courier or freight forwarder. They will use it to make customs declarations on your behalf.
                | """)
          page.elementIsPresent(RegistrationCompletePage.LeaveFeedbackLinkXpath) shouldBe true
          page.getElementsText(RegistrationCompletePage.LeaveFeedbackLinkXpath) shouldBe "What did you think of this service? (opens in a new window or tab)"
          page.getElementsHref(RegistrationCompletePage.LeaveFeedbackLinkXpath) shouldBe "/feedback/CDS"
      }
    }

    "allow authenticated users to access the rejected page" in {
      invokeRejectedPageWithAuthenticatedUser() {
        mockSessionCacheForOutcomePage

        result =>
          status(result) shouldBe OK
          val page = CdsPage(bodyOf(result))
          page.title should startWith(RegistrationRejectedPage.title)
          page.getElementsText(RegistrationRejectedPage.pageHeadingXpath) shouldBe RegistrationRejectedPage.heading
          page.getElementsText(RegistrationRejectedPage.processedDateXpath) shouldBe "Application received by HMRC on 22 May 2016"
      }
    }
  }

  "calling eoriAlreadyExists on SubscriptionCreateController" should {
    "render eori already exists page" in {
      when(mockSessionCache.subscriptionCreateOutcome(any[HeaderCarrier]))
        .thenReturn(Future.successful(SubscriptionCreateOutcome("testDate", "testFullName", Some("EoriTest"))))
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      invokeEoriAlreadyExists { result =>
        status(result) shouldBe OK
      }
    }
  }

  "calling subscriptionInProgress on SubscriptionCreateController" should {
    "render subscription in-progress page" in {
      when(mockSessionCache.subscriptionCreateOutcome(any[HeaderCarrier]))
        .thenReturn(Future.successful(SubscriptionCreateOutcome("testDate", "testFullName", Some("EoriTest"))))
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      invokeSubscriptionInProgress { result =>
        status(result) shouldBe OK
      }
    }
  }

  "calling pending on SubscriptionCreateController" should {
    "render subscription status processing page" in {
      when(mockSessionCache.subscriptionCreateOutcome(any[HeaderCarrier]))
        .thenReturn(Future.successful(SubscriptionCreateOutcome("testDate", "testFullName", Some("EoriTest"))))
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      invokePending { result =>
        status(result) shouldBe OK
      }
    }
  }

  "calling migrationEnd on SubscriptionCreateController" should {
    val subscriptionCreateOutcome = SubscriptionCreateOutcome("testDate", "testFullName", Some("EoriTest"))

    "render page with name for UK location" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("uk"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(Utr("someUtr"))))
      mockNameAndSubscriptionCreateOutcomeRetrieval
      when(mockSessionCache.subscriptionCreateOutcome(any[HeaderCarrier])).thenReturn(Future.successful(subscriptionCreateOutcome))
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(mockSessionCache.saveSubscriptionCreateOutcome(any[SubscriptionCreateOutcome])(any[HeaderCarrier])).thenReturn(Future.successful(true))
      invokeMigrationEnd { result =>
        status(result) shouldBe OK
      }
    }

    "render page with name for ROW location when customsId exists" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("third-country"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(Utr("someUtr"))))
      mockNameAndSubscriptionCreateOutcomeRetrieval
      when(mockSessionCache.subscriptionCreateOutcome(any[HeaderCarrier])).thenReturn(Future.successful(subscriptionCreateOutcome))
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(mockSessionCache.saveSubscriptionCreateOutcome(any[SubscriptionCreateOutcome])(any[HeaderCarrier])).thenReturn(Future.successful(true))
      invokeMigrationEnd { result =>
        status(result) shouldBe OK
      }
    }

    "render page with name for ROW location when no customsId exists" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("third-country"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier])).thenReturn(Future.successful(None))
      when(mockSessionCache.subscriptionCreateOutcome(any[HeaderCarrier])).thenReturn(Future.successful(subscriptionCreateOutcome))
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(mockSessionCache.saveSubscriptionCreateOutcome(any[SubscriptionCreateOutcome])(any[HeaderCarrier])).thenReturn(Future.successful(true))
      invokeMigrationEnd { result =>
        status(result) shouldBe OK
      }
    }
  }

  private def mockSessionCacheForOutcomePage = {
    when(mockSessionCache.registrationDetails(any[HeaderCarrier])).thenReturn(Future.successful(mockRegDetails))
    when(mockSessionCache.saveSubscriptionCreateOutcome(any[SubscriptionCreateOutcome])(any[HeaderCarrier])).thenReturn(Future.successful(true))
    when(mockRegDetails.name).thenReturn("orgName")
    when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
    when(mockSessionCache.subscriptionCreateOutcome(any[HeaderCarrier])).thenReturn(Future.successful(mockSubscribeOutcome))
    when(mockSubscribeOutcome.processedDate).thenReturn("22 May 2016")
  }

  def invokeEndPageWithAuthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    mockSessionCacheForOutcomePage
    test(subscriptionController.end().apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  def invokeRejectedPageWithAuthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    mockSessionCacheForOutcomePage
    test(subscriptionController.rejected.apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  def invokeEndPageWithUnAuthenticatedUser(test: Future[Result] => Any) {
    withNotLoggedInUser(mockAuthConnector)
    test(subscriptionController.end().apply(SessionBuilder.buildRequestWithSessionNoUser))
  }

  private def subscribeForGetYourEORI(
    userId: String = defaultUserId,
    organisationTypeOption: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(organisationTypeOption)
    test(subscriptionController.subscribe(Journey.GetYourEORI)(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def invokeMigrationEnd(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(subscriptionController.migrationEnd.apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def invokeEoriAlreadyExists(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(subscriptionController.eoriAlreadyExists.apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def invokeSubscriptionInProgress(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(subscriptionController.subscriptionInProgress.apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def invokePending(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(subscriptionController.pending.apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def mockNameAndSubscriptionCreateOutcomeRetrieval = {
    val mrweair = mock[RegisterWithEoriAndIdResponse]
    val mrweaird = mock[RegisterWithEoriAndIdResponseDetail]
    val rd = mock[ResponseData]
    val trader = mock[Trader]
    when(mockSessionCache.registerWithEoriAndIdResponse(any[HeaderCarrier])).thenReturn(Future.successful(mrweair))
    when(mrweair.responseDetail).thenReturn(Some(mrweaird))
    when(mrweaird.responseData).thenReturn(Some(rd))
    when(rd.trader).thenReturn(trader)
    when(trader.fullName).thenReturn("testName")
  }
}
