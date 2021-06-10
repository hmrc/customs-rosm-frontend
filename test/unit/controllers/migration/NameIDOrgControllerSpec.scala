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

package unit.controllers.migration

import common.pages.migration.NameIdDetailsPage
import common.pages.migration.NameIdDetailsPage._
import common.pages.subscription.SubscriptionContactDetailsPage.pageLevelErrorSummaryListXPath
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor2
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.NameIDOrgController
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.NameIdOrganisationDisplayMode.RegisteredCompanyDM
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.domain.{RegistrationDetails, _}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.nameUtrOrganisationForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.nameId
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import unit.controllers.subscription.SubscriptionFlowSpec
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NameIDOrgControllerSpec extends SubscriptionFlowSpec with ControllerSpec with BeforeAndAfterEach {

  protected override val mockSubscriptionFlowManager: SubscriptionFlowManager = mock[SubscriptionFlowManager]
  protected override val formId: String = NameIdDetailsPage.formId
  protected override val submitInCreateModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.NameIDOrgController
      .submit(isInReviewMode = false, Journey.GetYourEORI)
      .url
  protected override val submitInReviewModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.NameIDOrgController
      .submit(isInReviewMode = true, Journey.GetYourEORI)
      .url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockRegistrationDetails = mock[RegistrationDetails](RETURNS_DEEP_STUBS)
  private val mockCdsFrontendDataCache = mock[SessionCache]

  private val nameIdView = app.injector.instanceOf[nameId]

  private val controller = new NameIDOrgController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockRequestSessionData,
    mockCdsFrontendDataCache,
    mockSubscriptionFlowManager,
    mcc,
    nameIdView,
    mockSubscriptionDetailsHolderService,
    appConfig
  )

  private val emulatedFailure = new UnsupportedOperationException("Emulation of service call failure")

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockCdsFrontendDataCache,
      mockSubscriptionFlowManager,
      mockSubscriptionDetailsHolderService
    )
    when(mockSubscriptionBusinessService.cachedNameIdOrganisationViewModel(any[HeaderCarrier])).thenReturn(None)
    when(mockSubscriptionBusinessService.getCachedNameIdViewModel(any[HeaderCarrier]))
      .thenReturn(Future.successful(NameIdDetailsPage.filledValues))

    when(mockRequestSessionData.userSelectedOrganisationType(any())).thenReturn(Some(CdsOrganisationType.Company))

    registerSaveNameIdDetailsMockSuccess()
    mockFunctionWithRegistrationDetails(mockRegistrationDetails)

    setupMockSubscriptionFlowManager(NameUtrDetailsSubscriptionFlowPage)
  }

  val subscriptionFlows: TableFor2[SubscriptionFlow, String] = Table[SubscriptionFlow, String](
    ("Flow name", "Label"),
    (MigrationEoriOrganisationSubscriptionFlow, "What are your company details?")
  )

  val formModes = Table(
    ("formMode", "showFormFunction"),
    ("create", (flow: SubscriptionFlow) => showCreateForm(flow)(_)),
    ("review", (flow: SubscriptionFlow) => showReviewForm(flow)(_))
  )

  forAll(formModes) { (formMode, showFormFunction) =>
    s"The name / id when viewing the $formMode form" should {

      forAll(subscriptionFlows) {
        case (subscriptionFlow, expectedLabel) =>
          s"display appropriate label in subscription flow $subscriptionFlow" in {
            showFormFunction(subscriptionFlow) { result =>
              val page = CdsPage(bodyOf(result))
              page.getElementsText(pageTitleXPath) shouldBe expectedLabel
            }
          }
      }

      "display name / id correctly when all fields are populated" in {
        when(mockSubscriptionBusinessService.cachedNameIdOrganisationViewModel(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(NameIdDetailsPage.filledValues)))

        showFormFunction(MigrationEoriOrganisationSubscriptionFlow) { result =>
          val page = CdsPage(bodyOf(result))
          val expectedName = s"${NameIdDetailsPage.filledValues.name}"
          val expectedUtr = s"${NameIdDetailsPage.filledValues.id}"

          page.getElementValue(nameFieldXPath) shouldBe expectedName
          page.getElementValue(utrFieldXPath) shouldBe expectedUtr

        }
      }
    }
  }

  "Viewing the create form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.GetYourEORI))

    "display back link correctly" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "display the correct text for the continue button" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }

    "fill fields with details if stored in cache" in {
      when(mockSubscriptionBusinessService.cachedNameIdOrganisationViewModel(any[HeaderCarrier]))
        .thenReturn(Some(NameIdDetailsPage.filledValues))
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        val expectedName = s"${NameIdDetailsPage.filledValues.name}"
        val expectedUtr = s"${NameIdDetailsPage.filledValues.id}"

        page.getElementValue(nameFieldXPath) shouldBe expectedName
        page.getElementValue(utrFieldXPath) shouldBe expectedUtr
      }
    }

    "leave fields empty if details weren't found in cache" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(nameFieldXPath) shouldBe 'empty
        page.getElementValue(utrFieldXPath) shouldBe 'empty
      }
    }

  }

  "Viewing the review form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.reviewForm(Journey.GetYourEORI))

    "display relevant data in form fields when subscription details exist in the cache" in {
      when(mockSubscriptionBusinessService.getCachedNameIdViewModel).thenReturn(NameIdDetailsPage.filledValues)

      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        val expectedName = s"${NameIdDetailsPage.filledValues.name}"
        val expectedUtr = s"${NameIdDetailsPage.filledValues.id}"

        page.getElementValue(nameFieldXPath) shouldBe expectedName
        page.getElementValue(utrFieldXPath) shouldBe expectedUtr
      }
    }

    "not display the number of steps and back link to review page" in {
      showReviewForm()(verifyNoStepsAndBackLinkInReviewMode)
    }

    "display the correct text for the continue button" in {
      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      }
    }
  }

  "submitting the form in Create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.GetYourEORI)
    )

    "save the details" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService).cacheNameIdDetails(meq(NameIdDetailsPage.filledValues))(
          any[HeaderCarrier]
        )
      }
    }

    "validation error when full name is not submitted" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap + (nameFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your registered organisation name"
        page.getElementsText(nameFieldLevelErrorXPath) shouldBe "Error: Enter your registered organisation name"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when full name more than 105 characters" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap + (nameFieldName -> List.fill(106)("D").mkString)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Registered company name must be 105 characters or fewer"
        page.getElementsText(nameFieldLevelErrorXPath) shouldBe "Error: Registered company name must be 105 characters or fewer"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when ID UTR is not submitted" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap + (utrFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your Unique Taxpayer Reference"
        page.getElementsText(utrFieldLevelErrorXPath) shouldBe "Error: Enter your Unique Taxpayer Reference"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "display page level errors when nothing is entered" in {
      submitFormInCreateMode(createEmptyFormUtrMap) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your registered organisation name Enter your Unique Taxpayer Reference"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "fail when system fails to create details" in {
      registerSaveNameIdDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInCreateMode(createFormAllFieldsUtrMap) { result =>
          await(result)
        }
      }

      caught shouldBe emulatedFailure
    }

    "allow resubmission in create mode when details are invalid" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap - utrFieldName)(verifyFormActionInCreateMode)
    }

    "redirect to next page when details are valid" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap)(verifyRedirectToNextPageInCreateMode)
    }
  }

  "submitting the form in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = true, Journey.GetYourEORI)
    )

    "allow resubmission in review mode when details are invalid" in {
      submitFormInReviewMode(createFormAllFieldsUtrMap - utrFieldName)(verifyFormSubmitsInReviewMode)
    }

    "redirect to the review page when details are valid" in {
      submitFormInReviewMode(createFormAllFieldsUtrMap)(verifyRedirectToReviewPage(Journey.GetYourEORI))
    }
  }

  "UtrConfiguration returns correct Configuration" should {

    "for RegExistingEoriLimitedCompanyId" in {
      val cfg = controller.UtrConfiguration("Corporate Body", displayMode = RegisteredCompanyDM)
      cfg.matchingServiceType shouldBe "Corporate Body"
      cfg.displayMode shouldBe "registered-company"
      cfg.isNameAddressRegistrationAvailable shouldBe false
      cfg.form shouldBe nameUtrOrganisationForm
    }

    "will create a customs id" in {
      val cfg = controller.UtrConfiguration("Corporate Body", displayMode = RegisteredCompanyDM)
      val utr = cfg.createCustomsId("1234567890")
      utr shouldBe Utr("1234567890")
    }
  }

  "invalidOrganisationType returns correct message" should {

    "for wrong-org-type" in {
      val msg = controller.invalidOrganisationType("wrong-org-type")
      msg shouldBe "Invalid organisation type 'wrong-org-type'."

    }
  }

  val createFormAllFieldsUtrMap: Map[String, String] =
    Map(nameFieldName -> "Test Business Name", utrFieldName -> "1111111111")

  val createEmptyFormUtrMap: Map[String, String] = Map(nameFieldName -> "", utrFieldName -> "")

  private def mockFunctionWithRegistrationDetails(registrationDetails: RegistrationDetails) {
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(registrationDetails)
  }

  private def submitFormInCreateMode(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)

    val result = controller.submit(isInReviewMode = false, Journey.GetYourEORI)(
      SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
    )
    test(result)
  }

  private def submitFormInReviewMode(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)

    val result = controller.submit(isInReviewMode = true, Journey.GetYourEORI)(
      SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
    )
    test(result)
  }

  private def showCreateForm(
    subscriptionFlow: SubscriptionFlow = MigrationEoriOrganisationSubscriptionFlow
  )(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)

    val result = controller.createForm(Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    test(result)
  }

  private def showReviewForm(
    subscriptionFlow: SubscriptionFlow = MigrationEoriOrganisationSubscriptionFlow
  )(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)
    when(mockSubscriptionBusinessService.getCachedNameIdViewModel(any[HeaderCarrier]))
      .thenReturn(Future.successful(NameIdDetailsPage.filledValues))

    val result = controller.reviewForm(Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    test(result)
  }

  private def registerSaveNameIdDetailsMockSuccess() {
    when(mockSubscriptionDetailsHolderService.cacheNameIdDetails(any[NameIdOrganisationMatchModel])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
  }

  private def registerSaveNameIdDetailsMockFailure(exception: Throwable) {
    when(mockSubscriptionDetailsHolderService.cacheNameIdDetails(any[NameIdOrganisationMatchModel])(any[HeaderCarrier]))
      .thenReturn(Future.failed(exception))
  }

}
