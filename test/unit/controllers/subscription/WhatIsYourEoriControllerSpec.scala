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

import common.pages.subscription.{EoriNumberPage, SubscriptionAmendCompanyDetailsPage, SubscriptionContactDetailsPage}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.WhatIsYourEoriController
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.EoriNumberSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.domain.{
  CdsOrganisationType,
  EnrolmentResponse,
  KeyValue,
  RegistrationDetailsIndividual
}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.what_is_your_eori
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.SubscriptionAmendCompanyDetailsFormBuilder._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class WhatIsYourEoriControllerSpec
    extends SubscriptionFlowCreateModeTestSupport with BeforeAndAfterEach
    with SubscriptionFlowReviewModeTestSupport {
  protected override val formId: String = EoriNumberPage.formId

  protected override def submitInCreateModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.WhatIsYourEoriController
      .submit(isInReviewMode = false, Journey.Migrate)
      .url

  protected override def submitInReviewModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.WhatIsYourEoriController
      .submit(isInReviewMode = true, Journey.Migrate)
      .url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]
  private val whatIsYourEoriView = app.injector.instanceOf[what_is_your_eori]

  private val controller = new WhatIsYourEoriController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsHolderService,
    mockEnrolmentStoreProxyService,
    mcc,
    whatIsYourEoriView,
    mockRequestSessionData
  )

  private val emulatedFailure = new UnsupportedOperationException("Emulation of service call failure")

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockSubscriptionFlowManager,
      mockRequestSessionData,
      mockSubscriptionDetailsHolderService,
      mockEnrolmentStoreProxyService
    )
    when(mockSubscriptionBusinessService.cachedEoriNumber(any[HeaderCarrier])).thenReturn(None)
    when(mockEnrolmentStoreProxyService.groupIdEnrolments(any())(any(), any()))
      .thenReturn(Future.successful(List.empty))
    when(mockEnrolmentStoreProxyService.isEoriEnrolledWithAnotherGG(any())(any(), any()))
      .thenReturn(Future.successful(false))

    registerSaveDetailsMockSuccess()
    setupMockSubscriptionFlowManager(EoriNumberSubscriptionFlowPage)
  }

  "Subscription What Is Your Eori Number form in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.createForm(Journey.Migrate))

    "display title as 'Enter your EORI number'" in {
      showCreateForm(journey = Journey.Migrate) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("What is your EORI number?")
      }
    }

    "submit in create mode" in {
      showCreateForm(journey = Journey.Migrate)(verifyFormActionInCreateMode)
    }

    "display the back link" in {
      showCreateForm(journey = Journey.Migrate)(verifyBackLinkInCreateModeSubscribe)
    }

    "display the back link for subscribe user journey" in {
      showCreateForm(journey = Journey.Migrate) { result =>
        verifyBackLinkIn(result)("/customs/subscribe-for-cds/matching/organisation-type")
      }
    }

    "have Eori Number input field without data if not cached previously" in {
      showCreateForm(journey = Journey.Migrate) { result =>
        val page = CdsPage(bodyOf(result))
        verifyEoriNumberFieldExistsWithNoData(page)
      }
    }

    "redirect to when user has EORI already associated with different GG" in {
      val eori = "GB123456789"
      when(mockEnrolmentStoreProxyService.isEoriEnrolledWithAnotherGG(any())(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockSubscriptionDetailsHolderService.cacheExistingEoriNumber(any())(any()))
        .thenReturn(Future.successful((): Unit))
      when(mockEnrolmentStoreProxyService.isEoriEnrolledWithAnotherGG(any())(any(), any()))
        .thenReturn(Future.successful(true))

      when(mockSubscriptionBusinessService.cachedEoriNumber(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(eori)))
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
      submitFormInCreateMode(mandatoryFieldsMap)(
        verifyRedirectToNextPageIn(_)("/customs/subscribe-for-cds/eori-already-used")
      )
    }

    "redirect to Use This EORI page when user has EORI already" in {
      val eori = "GB123456789"
      when(mockEnrolmentStoreProxyService.groupIdEnrolments(any())(any(), any()))
        .thenReturn(
          Future
            .successful(List(EnrolmentResponse("HMRC-SERVICE-ORG", "Activated", List(KeyValue("EORINumber", eori)))))
        )
      when(mockSubscriptionDetailsHolderService.cacheExistingEoriNumber(any())(any()))
        .thenReturn(Future.successful((): Unit))

      showCreateForm(journey = Journey.Migrate) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.UseThisEoriController
          .display(Journey.Migrate)
          .url
      }
    }

    "have Eori Number input field prepopulated if cached previously" in {
      when(mockSubscriptionBusinessService.cachedEoriNumber(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(EoriNumber)))
      showCreateForm(journey = Journey.Migrate) { result =>
        val page = CdsPage(bodyOf(result))
        verifyEoriNumberFieldExistsAndPopulatedCorrectly(page)
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm(journey = Journey.Migrate)({ result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(EoriNumberPage.continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      })
    }

  }

  "Subscription Eori Number form in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.reviewForm(Journey.Migrate))

    "display title as 'Enter your EORI number'" in {
      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("What is your EORI number?")
      }
    }

    "submit in review mode" in {
      showReviewForm()(verifyFormSubmitsInReviewMode)
    }

    "retrieve the cached data" in {
      showReviewForm() { result =>
        CdsPage(bodyOf(result))
        verify(mockSubscriptionBusinessService).cachedEoriNumber(any[HeaderCarrier])
      }
    }

    "have all the required input fields without data" in {
      showReviewForm(EoriNumber) { result =>
        val page = CdsPage(bodyOf(result))
        verifyEoriNumberFieldExistsAndPopulatedCorrectly(page)
      }
    }

    "display the correct text for the continue button" in {
      showReviewForm()({ result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(EoriNumberPage.continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      })
    }
  }

  "submitting the form with all mandatory fields filled when in create mode for all organisation types" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.Migrate)
    )

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInCreateMode(mandatoryFieldsMap) { result =>
          await(result)
        }
      }

      caught shouldBe emulatedFailure
    }

    "redirect to next screen" in {
      submitFormInCreateMode(mandatoryFieldsMap)(verifyRedirectToNextPageIn(_)("next-page-url"))
    }

    "redirect to next screen when selectedOrganisationType is not set" in {
      RegistrationDetailsIndividual
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)

      submitFormInCreateMode(mandatoryFieldsMap)(verifyRedirectToNextPageIn(_)("next-page-url"))
    }

    "redirect to next screen when unmatched journey is  set" in {
      when(mockSubscriptionBusinessService.cachedEoriNumber(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some("EORINUMBER")))
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
      submitFormInCreateMode(mandatoryFieldsMap)(verifyRedirectToNextPageIn(_)("next-page-url"))
    }

  }

  "submitting the form with all mandatory fields filled when in review mode for all organisation types" should {

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInReviewMode(populatedEoriNumberFieldsMap) { result =>
          await(result)
        }
      }

      caught shouldBe emulatedFailure
    }

    "redirect to review screen" in {
      submitFormInReviewMode(populatedEoriNumberFieldsMap)(verifyRedirectToReviewPage(Journey.Migrate))
    }

    "redirect to review screen for unmatched user" in {
      when(mockRequestSessionData.mayBeUnMatchedUser(any[Request[AnyContent]])).thenReturn(Some("true"))
      submitFormInReviewMode(populatedEoriNumberFieldsMap)(verifyRedirectToReviewPage(Journey.Migrate))
    }
  }

  "Submitting in Create Mode when entries are invalid" should {

    "allow resubmission in create mode" in {
      submitFormInCreateMode(unpopulatedEoriNumberFieldsMap)(verifyFormActionInCreateMode)
    }
  }

  "Submitting in Review Mode when entries are invalid" should {

    "allow resubmission in review mode" in {
      submitFormInReviewMode(unpopulatedEoriNumberFieldsMap)(verifyFormSubmitsInReviewMode)
    }
  }

  "eori number" should {

    "be mandatory" in {
      submitFormInCreateMode(unpopulatedEoriNumberFieldsMap) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe "Enter your EORI number"
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.eoriNumberFieldLevelErrorXpath) shouldBe "Enter your EORI number"
      }
    }



    "be of the correct format" in {
      submitFormInCreateMode(Map("eori-number" -> "GBX45678901234")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe enterAValidEori
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.eoriNumberFieldLevelErrorXpath) shouldBe enterAValidEori

      }
    }

    "reject none GB EORI number" in {
      submitFormInCreateMode(Map("eori-number" -> "FR145678901234")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe enterAValidEori
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.eoriNumberFieldLevelErrorXpath) shouldBe enterAValidEori

      }
    }
  }

  private def submitFormInCreateMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    userSelectedOrgType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(userSelectedOrgType)

    test(
      controller.submit(isInReviewMode = false, Journey.Migrate)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def submitFormInReviewMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    userSelectedOrgType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(userSelectedOrgType)

    test(
      controller.submit(isInReviewMode = true, Journey.Migrate)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def registerSaveDetailsMockSuccess() {
    when(mockSubscriptionDetailsHolderService.cacheEoriNumber(anyString())(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
  }

  private def registerSaveDetailsMockFailure(exception: Throwable) {
    when(mockSubscriptionDetailsHolderService.cacheEoriNumber(anyString)(any[HeaderCarrier]))
      .thenReturn(Future.failed(exception))
  }

  private def showCreateForm(
    userId: String = defaultUserId,
    userSelectedOrganisationType: Option[CdsOrganisationType] = None,
    journey: Journey.Value
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(userSelectedOrganisationType)

    test(controller.createForm(journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def showReviewForm(
    dataToEdit: String = EoriNumber,
    userId: String = defaultUserId,
    userSelectedOrganisationType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(userSelectedOrganisationType)
    when(mockSubscriptionBusinessService.cachedEoriNumber(any[HeaderCarrier])).thenReturn(Some(dataToEdit))

    test(controller.reviewForm(Journey.Migrate).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyEoriNumberFieldExistsAndPopulatedCorrectly(page: CdsPage): Unit =
    page.getElementValueForLabel(SubscriptionAmendCompanyDetailsPage.eoriNumberLabelXpath) should be(EoriNumber)

  private def verifyEoriNumberFieldExistsWithNoData(page: CdsPage): Unit =
    page.getElementValueForLabel(SubscriptionAmendCompanyDetailsPage.eoriNumberLabelXpath) shouldBe ""

  private def verifyBackLinkIn(result: Result)(linkToVerify: String) = {
    val page = CdsPage(bodyOf(result))
    page.getElementAttributeHref(SubscriptionContactDetailsPage.backLinkXPath) shouldBe previousPageUrl
  }

  private def verifyRedirectToNextPageIn(result: Result)(linkToVerify: String) = {
    status(result) shouldBe SEE_OTHER
    result.header.headers(LOCATION) should endWith(linkToVerify)
  }
}
