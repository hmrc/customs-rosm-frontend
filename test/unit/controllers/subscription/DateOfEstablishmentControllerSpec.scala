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

import common.pages.subscription.SubscriptionContactDetailsPage._
import common.pages.subscription.{SubscriptionDateOfBirthPage, SubscriptionDateOfEstablishmentPage, SubscriptionPartnershipDateOfEstablishmentPage}
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.DateOfEstablishmentController
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{DateOfEstablishmentSubscriptionFlowPage, SubscriptionDetails}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.date_of_establishment
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DateOfEstablishmentControllerSpec
    extends SubscriptionFlowTestSupport with BeforeAndAfterEach with SubscriptionFlowCreateModeTestSupport
    with SubscriptionFlowReviewModeTestSupport {

  protected override val formId: String = SubscriptionDateOfBirthPage.formId
  protected override val submitInCreateModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.DateOfEstablishmentController
      .submit(isInReviewMode = false, Journey.GetYourEORI)
      .url
  protected override val submitInReviewModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.DateOfEstablishmentController
      .submit(isInReviewMode = true, Journey.GetYourEORI)
      .url

  private val mockOrgTypeLookup = mock[OrgTypeLookup]
  private val mockRequestSessionData = mock[RequestSessionData]

  private val dateOfEstablishmentView = app.injector.instanceOf[date_of_establishment]

  private val controller = new DateOfEstablishmentController(
    app,
    mockAuthConnector,
    mockSubscriptionFlowManager,
    mockSubscriptionBusinessService,
    mockSubscriptionDetailsHolderService,
    mockRequestSessionData,
    mcc,
    dateOfEstablishmentView,
    mockOrgTypeLookup
  )

  private val DateOfEstablishmentString = "1962-05-12"
  private val DateOfEstablishment = LocalDate.parse(DateOfEstablishmentString)

  private val ValidRequest = Map(
    "date-of-establishment.day" -> DateOfEstablishment.dayOfMonth.getAsString,
    "date-of-establishment.month" -> DateOfEstablishment.monthOfYear.getAsString,
    "date-of-establishment.year" -> DateOfEstablishment.year.getAsString
  )

  val existingSubscriptionDetailsHolder = SubscriptionDetails()

  private val DateOfEstablishmentMissingPageLevelError = "Enter your date of establishment"
  private val DateOfEstablishmentMissingError = "Error: Enter your date of establishment"
  private val DateOfEstablishmentInvalidError = "Please enter a valid date of establishment"
  private val DateOfEstablishmentInFutureError = "You cannot enter a date of establishment in the future"

  override protected def beforeEach(): Unit = {
    reset(mockSubscriptionFlowManager, mockSubscriptionBusinessService, mockSubscriptionDetailsHolderService)
    setupMockSubscriptionFlowManager(DateOfEstablishmentSubscriptionFlowPage)
    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(CorporateBody))
  }

  val formModes = Table(
    ("formMode", "submitFormFunction"),
    ("create", (form: Map[String, String]) => submitFormInCreateMode(form) _),
    ("review", (form: Map[String, String]) => submitFormInReviewMode(form) _)
  )

  "Loading the page in create mode for subscription rest of the world" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.Migrate))

    "display the form" in {
      showCreateForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.elementIsPresent(SubscriptionDateOfEstablishmentPage.dayOfDateFieldXpath) shouldBe true
        page.elementIsPresent(SubscriptionDateOfEstablishmentPage.monthOfDateFieldXpath) shouldBe true
        page.elementIsPresent(SubscriptionDateOfEstablishmentPage.yearOfDateFieldXpath) shouldBe true
        page.elementIsPresent(helpAndSupportLabelXpath) shouldBe true
      }
    }
  }

  "Loading the page in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.GetYourEORI))

    "display the form" in {
      showCreateForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.elementIsPresent(SubscriptionDateOfEstablishmentPage.dayOfDateFieldXpath) shouldBe true
        page.elementIsPresent(SubscriptionDateOfEstablishmentPage.monthOfDateFieldXpath) shouldBe true
        page.elementIsPresent(SubscriptionDateOfEstablishmentPage.yearOfDateFieldXpath) shouldBe true
      }
    }

    "set form action url to submit in create mode" in {
      showCreateForm()(verifyFormActionInCreateMode)
    }

    "display the number of 'Back' link according the current subscription flow" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "have all the required input fields without data if not cached previously" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(SubscriptionDateOfEstablishmentPage.dayOfDateFieldXpath) shouldBe 'empty
        page.getElementValue(SubscriptionDateOfEstablishmentPage.monthOfDateFieldXpath) shouldBe 'empty
        page.getElementValue(SubscriptionDateOfEstablishmentPage.yearOfDateFieldXpath) shouldBe 'empty
      }
    }

    "have all the required input fields with data if cached previously" in {
      showCreateForm(cachedDate = Some(DateOfEstablishment)) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(SubscriptionDateOfEstablishmentPage.dayOfDateFieldXpath) shouldBe DateOfEstablishment.dayOfMonth.getAsString
        page.getElementValue(SubscriptionDateOfEstablishmentPage.monthOfDateFieldXpath) shouldBe DateOfEstablishment.monthOfYear.getAsString
        page.getElementValue(SubscriptionDateOfEstablishmentPage.yearOfDateFieldXpath) shouldBe DateOfEstablishment.year.getAsString
      }
    }

    "use partnership in title and heading for Partnership Org Type" in {
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(Partnership))
      showCreateForm(cachedDate = Some(DateOfEstablishment)) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith("When was the partnership established?")
        page.getElementsText(SubscriptionPartnershipDateOfEstablishmentPage.dateOfEstablishmentHeadingXPath) shouldBe "When was the partnership established?"
      }
    }

    "use partnership in title and heading for Limited Liability Partnership Org Type" in {
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(LLP))
      showCreateForm(cachedDate = Some(DateOfEstablishment)) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith("When was the partnership established?")
        page.getElementsText(SubscriptionDateOfEstablishmentPage.dateOfEstablishmentHeadingXPath) shouldBe "When was the partnership established?"
      }
    }

    "use business in Date of Establishment title and heading for non-partnership Org Type" in {
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier]))
        .thenReturn(Some(UnincorporatedBody))
      showCreateForm(cachedDate = Some(DateOfEstablishment)) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith("When was the organisation established?")
        page.getElementsText(SubscriptionDateOfEstablishmentPage.dateOfEstablishmentHeadingXPath) shouldBe "When was the organisation established?"
      }
    }

    "use business in Date of Establishment text and organisation in title and heading for Company Org Type" in {
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(CorporateBody))
      showCreateForm(cachedDate = Some(DateOfEstablishment)) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(SubscriptionDateOfEstablishmentPage.dateOfEstablishmentLabelXPath) should startWith(
          "Enter the date shown on the organisation's certificate of incorporation. You can find the date your organisation was established on the Companies House register (opens in a new window or tab)"
        )
        page.title should startWith("When was the organisation established?")
        page.getElementsText(SubscriptionDateOfEstablishmentPage.dateOfEstablishmentHeadingXPath) shouldBe "When was the organisation established?"
      }
    }
  }

  "Loading the page in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.reviewForm(Journey.GetYourEORI))

    "display the form" in {
      showReviewForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.elementIsPresent(SubscriptionDateOfEstablishmentPage.dayOfDateFieldXpath) shouldBe true
        page.elementIsPresent(SubscriptionDateOfEstablishmentPage.monthOfDateFieldXpath) shouldBe true
        page.elementIsPresent(SubscriptionDateOfEstablishmentPage.yearOfDateFieldXpath) shouldBe true
      }
    }

    "set form action url to submit in review mode" in {
      showReviewForm()(verifyFormSubmitsInReviewMode)
    }

    "not display the 'Back' link to review page" in {
      showReviewForm()(verifyBackLinkInReviewMode)
    }

    "display relevant data in form fields when subscription details exist in the cache" in {
      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(SubscriptionDateOfEstablishmentPage.dayOfDateFieldXpath) shouldBe DateOfEstablishment.dayOfMonth.getAsString
        page.getElementValue(SubscriptionDateOfEstablishmentPage.monthOfDateFieldXpath) shouldBe DateOfEstablishment.monthOfYear.getAsString
        page.getElementValue(SubscriptionDateOfEstablishmentPage.yearOfDateFieldXpath) shouldBe DateOfEstablishment.year.getAsString
      }
    }

    "display the correct text for the continue button" in {
      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      }
    }

  }

  forAll(formModes) { (formMode, submitFormFunction) =>
    s"date of establishment in $formMode mode" should {

      "be mandatory" in {
        submitFormFunction(
          ValidRequest + ("date-of-establishment.day" -> "", "date-of-establishment.month" -> "",
          "date-of-establishment.year" -> "")
        ) { result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(SubscriptionDateOfEstablishmentPage.pageLevelErrorSummaryListXPath) shouldBe DateOfEstablishmentMissingPageLevelError
          page.getElementsText(SubscriptionDateOfEstablishmentPage.dateOfEstablishmentErrorXpath) shouldBe DateOfEstablishmentMissingError
        }
      }

      "be a valid date" in {
        submitFormFunction(ValidRequest + ("date-of-establishment.day" -> "32")) { result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(SubscriptionDateOfEstablishmentPage.pageLevelErrorSummaryListXPath) shouldBe DateOfEstablishmentInvalidError
          page.getElementsText(SubscriptionDateOfEstablishmentPage.dateOfEstablishmentErrorXpath) shouldBe "Error: " + DateOfEstablishmentInvalidError
        }
      }

      "not be a future date" in {
        val tomorrow = LocalDate.now().plusDays(1)
        submitFormFunction(
          ValidRequest + ("date-of-establishment.day" -> tomorrow.getDayOfMonth.toString,
          "date-of-establishment.month" -> tomorrow.getMonthOfYear.toString,
          "date-of-establishment.year" -> tomorrow.getYear.toString)
        ) { result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(SubscriptionDateOfEstablishmentPage.pageLevelErrorSummaryListXPath) shouldBe DateOfEstablishmentInFutureError
          page.getElementsText(SubscriptionDateOfEstablishmentPage.dateOfEstablishmentErrorXpath) shouldBe "Error: " + DateOfEstablishmentInFutureError
        }
      }
    }

    s"Submitting the form in $formMode mode" should {

      "capture date of birth entered by user" in {
        submitFormInCreateMode(ValidRequest) { result =>
          await(result)
          verify(mockSubscriptionDetailsHolderService).cacheDateEstablished(meq(DateOfEstablishment))(
            any[HeaderCarrier]
          )
        }
      }
    }
  }

  "Submitting the invalid form in create mode" should {

    "allow resubmission in create mode" in {
      submitFormInCreateMode(ValidRequest - "date-of-establishment.day")(verifyFormActionInCreateMode)
    }
  }

  "Submitting the valid form in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.GetYourEORI)
    )

    "redirect to next page in subscription flow" in {
      submitFormInCreateMode(ValidRequest)(verifyRedirectToNextPageInCreateMode)
    }
  }

  "Submitting the valid form in review mode" should {

    "redirect to the review page" in {
      submitFormInReviewMode(ValidRequest)(verifyRedirectToReviewPage(Journey.GetYourEORI))
    }
  }

  "Submitting the invalid form in review mode" should {

    "allow resubmission in review mode" in {
      submitFormInReviewMode(ValidRequest - "date-of-establishment.day")(verifyFormSubmitsInReviewMode)
    }
  }

  private def showCreateForm(
    userId: String = defaultUserId,
    cachedDate: Option[LocalDate] = None,
    journey: Journey.Value = Journey.GetYourEORI
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockSubscriptionBusinessService.maybeCachedDateEstablished(any[HeaderCarrier]))
      .thenReturn(Future.successful(cachedDate))

    val result = controller.createForm(journey).apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  private def showReviewForm(userId: String = defaultUserId, journey: Journey.Value = Journey.GetYourEORI)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockSubscriptionBusinessService.getCachedDateEstablished(any[HeaderCarrier])).thenReturn(DateOfEstablishment)

    val result = controller.reviewForm(journey).apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  private def submitFormInCreateMode(form: Map[String, String])(test: Future[Result] => Any): Unit =
    submitForm(form, isInReviewMode = false)(test)

  private def submitFormInReviewMode(form: Map[String, String])(test: Future[Result] => Any): Unit =
    submitForm(form, isInReviewMode = true)(test)

  private def submitForm(
    form: Map[String, String],
    isInReviewMode: Boolean,
    userId: String = defaultUserId,
    journey: Journey.Value = Journey.GetYourEORI
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockSubscriptionDetailsHolderService.cacheDateEstablished(any[LocalDate])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
    val result = controller
      .submit(isInReviewMode, journey)
      .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    test(result)
  }
}
