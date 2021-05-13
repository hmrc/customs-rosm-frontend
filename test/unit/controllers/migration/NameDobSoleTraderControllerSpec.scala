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

import common.pages.migration.NameDobSoleTraderPage
import common.pages.migration.NameDobSoleTraderPage._
import common.pages.registration.DoYouHaveAnEoriPage.pageLevelErrorSummaryListXPath
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor2
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.NameDobSoleTraderController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.enter_your_details
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import unit.controllers.subscription.SubscriptionFlowSpec
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NameDobSoleTraderControllerSpec extends SubscriptionFlowSpec with ControllerSpec with BeforeAndAfterEach {

  protected override val mockSubscriptionFlowManager: SubscriptionFlowManager = mock[SubscriptionFlowManager]
  protected override val formId: String = NameDobSoleTraderPage.formId
  protected override val submitInCreateModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.NameDobSoleTraderController
      .submit(isInReviewMode = false, Journey.Migrate)
      .url
  protected override val submitInReviewModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.NameDobSoleTraderController
      .submit(isInReviewMode = true, Journey.Migrate)
      .url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockRegistrationDetails = mock[RegistrationDetails](RETURNS_DEEP_STUBS)
  private val mockCdsFrontendDataCache = mock[SessionCache]
  private val enterYourDetails = app.injector.instanceOf[enter_your_details]

  private val controller = new NameDobSoleTraderController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockRequestSessionData,
    mockCdsFrontendDataCache,
    mockSubscriptionFlowManager,
    mcc,
    enterYourDetails,
    mockSubscriptionDetailsHolderService
  )

  private val emulatedFailure = new UnsupportedOperationException("Emulation of service call failure")

  private val stringContaining36Characters = "Abcdef ghi-jklm - nopqrstuvwxyz ABCD"
  private val stringContainingInvalidCharacters = "John Doe%"

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockCdsFrontendDataCache,
      mockSubscriptionFlowManager,
      mockSubscriptionDetailsHolderService
    )
    when(mockSubscriptionBusinessService.cachedSubscriptionNameDobViewModel(any[HeaderCarrier])).thenReturn(None)
    when(mockSubscriptionBusinessService.getCachedSubscriptionNameDobViewModel(any[HeaderCarrier]))
      .thenReturn(Future.successful(NameDobSoleTraderPage.filledValues))

    when(mockRequestSessionData.userSelectedOrganisationType(any())).thenReturn(Some(CdsOrganisationType.SoleTrader))

    registerSaveNameDobDetailsMockSuccess()
    mockFunctionWithRegistrationDetails(mockRegistrationDetails)

    setupMockSubscriptionFlowManager(NameDobDetailsSubscriptionFlowPage)
  }

  val subscriptionFlows: TableFor2[SubscriptionFlow, String] = Table[SubscriptionFlow, String](
    ("Flow name", "Label"),
    (MigrationEoriSoleTraderSubscriptionFlow, "Enter your details")
  )

  val formModes = Table(
    ("formMode", "showFormFunction"),
    ("create", (flow: SubscriptionFlow) => showCreateForm(flow)(_)),
    ("review", (flow: SubscriptionFlow) => showReviewForm(flow)(_))
  )

  forAll(formModes) { (formMode, showFormFunction) =>
    s"The name / dob when viewing the $formMode form" should {

      forAll(subscriptionFlows) {
        case (subscriptionFlow, expectedLabel) =>
          s"display appropriate label in subscription flow $subscriptionFlow" in {
            showFormFunction(subscriptionFlow) { result =>
              val page = CdsPage(bodyOf(result))
              page.getElementsText(pageTitleXPath) shouldBe expectedLabel
            }
          }
      }

      "display name / dob correctly when all fields are populated" in {
        when(mockSubscriptionBusinessService.cachedSubscriptionNameDobViewModel(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(NameDobSoleTraderPage.filledValues)))

        showFormFunction(MigrationEoriSoleTraderSubscriptionFlow) { result =>
          val page = CdsPage(bodyOf(result))

          val expectedFirstName = s"${NameDobSoleTraderPage.filledValues.firstName}"
          val expectedLastName = s"${NameDobSoleTraderPage.filledValues.lastName}"
          val expectedDob = s"${NameDobSoleTraderPage.filledValues.dateOfBirth}"

          page.getElementValue(firstNameFieldXPath) shouldBe expectedFirstName
          page.getElementValue(lastNameFieldXPath) shouldBe expectedLastName
          getDobFromPage(page) shouldBe expectedDob
        }
      }
    }
  }

  "Viewing the create form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.createForm(Journey.Migrate))

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
      when(mockSubscriptionBusinessService.cachedSubscriptionNameDobViewModel(any[HeaderCarrier]))
        .thenReturn(Some(NameDobSoleTraderPage.filledValues))
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        val expectedFirstName = s"${NameDobSoleTraderPage.filledValues.firstName}"
        val expectedLastName = s"${NameDobSoleTraderPage.filledValues.lastName}"
        val expectedDob = s"${NameDobSoleTraderPage.filledValues.dateOfBirth}"

        page.getElementValue(firstNameFieldXPath) shouldBe expectedFirstName
        page.getElementValue(lastNameFieldXPath) shouldBe expectedLastName
        getDobFromPage(page) shouldBe expectedDob
      }
    }

    "leave fields empty if details weren't found in cache" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(firstNameFieldXPath) shouldBe 'empty
        page.getElementValue(lastNameFieldXPath) shouldBe 'empty
        page.getElementValue(dateOfBirthYearFieldXPath) shouldBe 'empty
        page.getElementValue(dateOfBirthMonthFieldXPath) shouldBe 'empty
        page.getElementValue(dateOfBirthDayFieldXPath) shouldBe 'empty
      }
    }
  }

  "Viewing the review form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.reviewForm(Journey.Migrate))

    "display relevant data in form fields when subscription details exist in the cache" in {
      when(mockSubscriptionBusinessService.getCachedSubscriptionNameDobViewModel)
        .thenReturn(NameDobSoleTraderPage.filledValues)

      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        val expectedFirstName = s"${NameDobSoleTraderPage.filledValues.firstName}"
        val expectedLastName = s"${NameDobSoleTraderPage.filledValues.lastName}"
        val expectedDob = s"${NameDobSoleTraderPage.filledValues.dateOfBirth}"

        page.getElementValue(firstNameFieldXPath) shouldBe expectedFirstName
        page.getElementValue(lastNameFieldXPath) shouldBe expectedLastName
        getDobFromPage(page) shouldBe expectedDob
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

  "Submitting the form in Create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.Migrate)
    )

    "save the details" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService).cacheNameDobDetails(meq(NameDobSoleTraderPage.filledValues))(
          any[HeaderCarrier]
        )
      }
    }

    "validation error when first name is not submitted" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap + (firstNameFieldId -> "")) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your first name"
        page.getElementsText(firstNameFieldLevelErrorXPath) shouldBe "Enter your first name"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when first name more than 35 characters" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap + (firstNameFieldName -> stringContaining36Characters)) {
        result =>
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The first name must be 35 characters or less"
          page.getElementsText(firstNameFieldLevelErrorXPath) shouldBe "The first name must be 35 characters or less"
          page.getElementsText("title") should startWith("Error: ")
          verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when first name has invalid characters" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap + (firstNameFieldName -> stringContainingInvalidCharacters)) {
        result =>
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter a first name without invalid characters"
          page.getElementsText(firstNameFieldLevelErrorXPath) shouldBe "Enter a first name without invalid characters"
          page.getElementsText("title") should startWith("Error: ")
          verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when last name is not submitted" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap + (lastNameFieldName -> "")) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your last name"
        page.getElementsText(lastNameFieldLevelErrorXPath) shouldBe "Enter your last name"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when last name more than 35 characters" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap + (lastNameFieldName -> stringContaining36Characters)) {
        result =>
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The last name must be 35 characters or less"
          page.getElementsText(lastNameFieldLevelErrorXPath) shouldBe "The last name must be 35 characters or less"
          page.getElementsText("title") should startWith("Error: ")
          verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when last name has invalid characters" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap + (lastNameFieldName -> stringContainingInvalidCharacters)) {
        result =>
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter a last name without invalid characters"
          page.getElementsText(lastNameFieldLevelErrorXPath) shouldBe "Enter a last name without invalid characters"
          page.getElementsText("title") should startWith("Error: ")
          verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when DAY of birth is not submitted" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap - dobDayFieldName) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter a date of birth in the right format"
        page.getElementsText(dobFieldLevelErrorXPath) shouldBe "Error: Enter a date of birth in the right format"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when MONTH of birth is not submitted" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap - dobMonthFieldName) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter a date of birth in the right format"
        page.getElementsText(dobFieldLevelErrorXPath) shouldBe "Error: Enter a date of birth in the right format"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when YEAR of birth is not submitted" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap - dobYearFieldName) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter a date of birth in the right format"
        page.getElementsText(dobFieldLevelErrorXPath) shouldBe "Error: Enter a date of birth in the right format"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when YEAR of birth is in the future" in {
      submitFormInCreateMode(createFormAllFieldsNameDobInFutureMap) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "You must specify a date that is not in the future"
        page.getElementsText(dobFieldLevelErrorXPath) shouldBe "Error: You must specify a date that is not in the future"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoInteractions(mockSubscriptionBusinessService)
      }
    }

    "display page level errors when nothing is entered" in {
      submitFormInCreateMode(createEmptyFormNameDobMap) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your first name Enter your last name Enter your date of birth"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "fail when system fails to create details" in {
      registerSaveNameDobDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInCreateMode(createFormAllFieldsNameDobMap) { result =>
          await(result)
        }
      }

      caught shouldBe emulatedFailure
    }

    "allow resubmission in create mode when details are invalid" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap - dobDayFieldName)(verifyFormActionInCreateMode)
    }

    "redirect to next page when details are valid" in {
      submitFormInCreateMode(createFormAllFieldsNameDobMap)(verifyRedirectToNextPageInCreateMode)
    }
  }

  "submitting the form in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(isInReviewMode = true, Journey.Migrate)
    )

    "allow resubmission in review mode when details are invalid" in {
      submitFormInReviewMode(createFormAllFieldsNameDobMap - dobDayFieldName)(verifyFormSubmitsInReviewMode)
    }

    "redirect to the review page when details are valid" in {
      submitFormInReviewMode(createFormAllFieldsNameDobMap)(verifyRedirectToReviewPage(Journey.Migrate))
    }
  }

  val createFormAllFieldsNameDobMap: Map[String, String] = Map(
    firstNameFieldName -> "Test First Name",
    lastNameFieldName -> "Test Last Name",
    middleNameFieldName -> "Test Middle Name",
    dobDayFieldName -> "03",
    dobMonthFieldName -> "09",
    dobYearFieldName -> "1983"
  )

  val createEmptyFormNameDobMap: Map[String, String] = Map(
    firstNameFieldName -> "",
    lastNameFieldName -> "",
    dobDayFieldName -> "",
    dobMonthFieldName -> "",
    dobYearFieldName -> ""
  )

  def createFormAllFieldsNameDobInFutureMap: Map[String, String] = {
    val todayPlusOneYear = LocalDate.now().plusYears(1)
    Map(
      firstNameFieldName -> "Test First Name",
      lastNameFieldName -> "Test Last Name",
      dobDayFieldName -> "03",
      dobMonthFieldName -> "09",
      dobYearFieldName -> todayPlusOneYear.toString("YYYY")
    )
  }

  private def mockFunctionWithRegistrationDetails(registrationDetails: RegistrationDetails) {
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(registrationDetails)
  }

  private def submitFormInCreateMode(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)

    val result = controller.submit(isInReviewMode = false, Journey.Migrate)(
      SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
    )
    test(result)
  }

  private def submitFormInReviewMode(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)

    val result = controller.submit(isInReviewMode = true, Journey.Migrate)(
      SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
    )
    test(result)
  }

  private def showCreateForm(
    subscriptionFlow: SubscriptionFlow = MigrationEoriSoleTraderSubscriptionFlow
  )(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)

    val result = controller.createForm(Journey.Migrate).apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    test(result)
  }

  private def showReviewForm(
    subscriptionFlow: SubscriptionFlow = MigrationEoriSoleTraderSubscriptionFlow
  )(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)
    when(mockSubscriptionBusinessService.getCachedSubscriptionNameDobViewModel(any[HeaderCarrier]))
      .thenReturn(Future.successful(NameDobSoleTraderPage.filledValues))

    val result = controller.reviewForm(Journey.Migrate).apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    test(result)
  }

  private def registerSaveNameDobDetailsMockSuccess() {
    when(mockSubscriptionDetailsHolderService.cacheNameDobDetails(any[NameDobMatchModel])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
  }

  private def registerSaveNameDobDetailsMockFailure(exception: Throwable) {
    when(mockSubscriptionDetailsHolderService.cacheNameDobDetails(any[NameDobMatchModel])(any[HeaderCarrier]))
      .thenReturn(Future.failed(exception))
  }

  private def getDobFromPage(page: CdsPage): String =
    (("0000" + page.getElementValue(dateOfBirthYearFieldXPath)) takeRight 4) + '-' + (("00" + page.getElementValue(
      dateOfBirthMonthFieldXPath
    )) takeRight 2) + '-' + (("00" + page.getElementValue(dateOfBirthDayFieldXPath)) takeRight 2)
}
