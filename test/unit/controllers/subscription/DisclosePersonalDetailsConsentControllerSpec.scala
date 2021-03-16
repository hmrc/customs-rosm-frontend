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

import java.util.UUID

import common.pages.subscription.DisclosePersonalDetailsConsentPage
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.DisclosePersonalDetailsConsentController
import uk.gov.hmrc.customs.rosmfrontend.domain.YesNo
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.disclose_personal_details_consent
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.YesNoFormBuilder._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DisclosePersonalDetailsConsentControllerSpec
    extends ControllerSpec with SubscriptionFlowSpec with MockitoSugar with BeforeAndAfterEach {

  protected override val formId: String = DisclosePersonalDetailsConsentPage.formId
  protected override val submitInCreateModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.DisclosePersonalDetailsConsentController
      .submit(isInReviewMode = false, Journey.GetYourEORI)
      .url
  protected override val submitInReviewModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.DisclosePersonalDetailsConsentController
      .submit(isInReviewMode = true, Journey.GetYourEORI)
      .url

  private val mockRequestSession = mock[RequestSessionData]
  private val disclosePersonalDetailsConsentView = app.injector.instanceOf[disclose_personal_details_consent]

  private val problemWithSelectionError = "Tell us if you want to show your name and address with your EORI number"
  private val problemWithSelectionSummaryError = "Error:Tell us if you want to show your name and address with your EORI number"
  private val yesNoInputName = "yes-no-answer"

  private val controller = new DisclosePersonalDetailsConsentController(
    app,
    mockAuthConnector,
    mockSubscriptionDetailsHolderService,
    mockSubscriptionBusinessService,
    mockRequestSession,
    mcc,
    disclosePersonalDetailsConsentView,
    mockSubscriptionFlowManager
  )

  private val subscriptionFlows = Table[SubscriptionFlow, String, String, String](
    ("Flow name", "Consent Info", "Yes Label", "No Label"),
    (
      IndividualSubscriptionFlow,
      "Anyone you share your EORI number with will be able to check it is valid using the check an EORI number service (opens in new tab). You can choose if you want your name and address to be shown with your EORI number. This can help customs agents, freight agents and businesses you are trading with identify you and process your goods. ",
      "Yes, show my name and address with my EORI number",
      "No, only show my EORI number"
    ),
    (
      OrganisationSubscriptionFlow,
      "Anyone you share your EORI number with will be able to check it is valid using the check an EORI number service (opens in new tab). You can choose if you want your organisation name and address to be shown with your EORI number. This can help customs agents, freight agents and businesses you are trading with identify you and process your goods. ",
      "Yes, show my organisation name and address with my EORI number",
      "No, only show my EORI number"
    ),
    (
      SoleTraderSubscriptionFlow,
      "Anyone you share your EORI number with will be able to check it is valid using the check an EORI number service (opens in new tab). You can choose if you want your name and address to be shown with your EORI number. This can help customs agents, freight agents and businesses you are trading with identify you and process your goods. ",
      "Yes, show my name and address with my EORI number",
      "No, only show my EORI number"
    )
  )

  override def beforeEach() {
    reset(mockSubscriptionDetailsHolderService, mockSubscriptionFlowManager)
    when(mockSubscriptionDetailsHolderService.cacheConsentToDisclosePersonalDetails(any[YesNo])(any[HeaderCarrier]))
      .thenReturn(Future.successful({}))
    setupMockSubscriptionFlowManager(EoriConsentSubscriptionFlowPage)
  }

  val formModes = Table(
    ("formMode", "showFormFunction"),
    ("create", (flow: SubscriptionFlow) => showCreateForm(flow)(_)),
    ("review", (flow: SubscriptionFlow) => showReviewForm(flow)(_))
  )

  forAll(formModes) { (formMode, showFormFunction) =>
    s"Loading the page in $formMode mode" should {

      forAll(subscriptionFlows) {
        case (subscriptionFlow, consentInfo, yesLabel, noLabel) =>
          s"display the form for subscription flow $subscriptionFlow" in {
            showFormFunction(subscriptionFlow) { result =>
              status(result) shouldBe OK
              val html: String = bodyOf(result)
              html should include("id=\"yes-no-answer-true\"")
              html should include("id=\"yes-no-answer-false\"")
              if (subscriptionFlow.isIndividualFlow) {
                html should include("Do you want to show your name and address on the check an EORI number service?")
              } else {
                html should include("Do you want to show your organisation name and address on the check an EORI number service?")
              }
            }
          }

          s"display proper labels for subscription flow $subscriptionFlow" in {
            showFormFunction(subscriptionFlow) { result =>
              status(result) shouldBe OK
              val page = CdsPage(bodyOf(result))
              page.getElementsText(DisclosePersonalDetailsConsentPage.consentInfoXpath) shouldBe consentInfo
              page.getElementsText(DisclosePersonalDetailsConsentPage.yesToDiscloseXpath) shouldBe yesLabel
              page.getElementsText(DisclosePersonalDetailsConsentPage.noToDiscloseXpath) shouldBe noLabel
            }
          }
      }
    }
  }

  "Loading the page in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.GetYourEORI))

    "set form action url to submit in create mode" in {
      showCreateForm()(verifyFormActionInCreateMode)
    }

    "display the 'Back' link according the current subscription flow" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }
  }

  "Loading the page in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.reviewForm(Journey.GetYourEORI))

    "set form action url to submit in review mode" in {
      showReviewForm()(verifyFormSubmitsInReviewMode)
    }

    "not display the number of steps and display the 'Back' link to review page" in {
      showReviewForm()(verifyNoStepsAndBackLinkInReviewMode)
    }

    "display yes when the user's previous answer of yes is in the cache" in {
      showReviewForm(previouslyAnswered = true)({ result =>
        val page = CdsPage(bodyOf(result))
        page.radioButtonIsChecked(DisclosePersonalDetailsConsentPage.noToDiscloseInputXpath) shouldBe false
        page.radioButtonIsChecked(DisclosePersonalDetailsConsentPage.yesToDiscloseInputXpath) shouldBe true
      })
    }

    "display no when the user's previous answer of no is in the cache" in {
      showReviewForm(previouslyAnswered = false)({ result =>
        val page = CdsPage(bodyOf(result))
        page.radioButtonIsChecked(DisclosePersonalDetailsConsentPage.noToDiscloseInputXpath) shouldBe true
        page.radioButtonIsChecked(DisclosePersonalDetailsConsentPage.yesToDiscloseInputXpath) shouldBe false
      })
    }

    "display the correct text for the continue button" in {
      showReviewForm()({ result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(DisclosePersonalDetailsConsentPage.continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      })
    }
  }

  "The Yes No Radio Button " should {
    "display a relevant error if no option is chosen" in {
      submitForm(ValidRequest - yesNoInputName) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(DisclosePersonalDetailsConsentPage.pageLevelErrorSummaryListXPath) shouldBe problemWithSelectionError
        page.getElementsText(DisclosePersonalDetailsConsentPage.fieldLevelErrorYesNoAnswer) shouldBe problemWithSelectionSummaryError
      }
    }

    "display a relevant error if an invalid answer option is selected" in {
      val invalidOption = UUID.randomUUID.toString
      submitForm(ValidRequest + (yesNoInputName -> invalidOption)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(DisclosePersonalDetailsConsentPage.pageLevelErrorSummaryListXPath) shouldBe problemWithSelectionError
        page.getElementsText(DisclosePersonalDetailsConsentPage.fieldLevelErrorYesNoAnswer) shouldBe problemWithSelectionSummaryError
      }
    }
  }

  "Submitting in Review Mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = true, Journey.GetYourEORI)
    )

    "allow resubmission in review mode when details are invalid" in {
      submitFormInReviewMode(ValidRequest - yesNoInputName)(verifyFormSubmitsInReviewMode)
    }

    "redirect to review page when details are valid" in {
      submitFormInReviewMode(ValidRequest)(verifyRedirectToReviewPage(Journey.GetYourEORI))
    }
  }

  "Submitting in Create Mode" should {

    "allow resubmission in create mode when details are invalid" in {
      submitFormInCreateMode(ValidRequest - yesNoInputName)(verifyFormActionInCreateMode)
    }

    "redirect to next page when details are valid" in {
      submitFormInCreateMode(ValidRequest)(verifyRedirectToNextPageInCreateMode)
    }
  }

  private def showCreateForm(
    subscriptionFlow: SubscriptionFlow = OrganisationSubscriptionFlow,
    userId: String = defaultUserId,
    journey: Journey.Value = Journey.GetYourEORI
  )(test: (Future[Result]) => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockSubscriptionFlowManager.currentSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)

    test(controller.createForm(journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def showReviewForm(
    subscriptionFlow: SubscriptionFlow = OrganisationSubscriptionFlow,
    previouslyAnswered: Boolean = true,
    userId: String = defaultUserId,
    journey: Journey.Value = Journey.GetYourEORI
  )(test: (Future[Result]) => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockSubscriptionBusinessService.getCachedPersonalDataDisclosureConsent(any[HeaderCarrier]))
      .thenReturn(previouslyAnswered)

    when(mockSubscriptionFlowManager.currentSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)

    test(controller.reviewForm(journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def submitFormInCreateMode(form: Map[String, String])(test: Future[Result] => Any): Unit =
    submitForm(form, isInReviewMode = false)(test)

  private def submitFormInReviewMode(form: Map[String, String])(test: Future[Result] => Any): Unit =
    submitForm(form, isInReviewMode = true)(test)

  private def submitForm(
    form: Map[String, String],
    isInReviewMode: Boolean = false,
    userId: String = defaultUserId,
    journey: Journey.Value = Journey.GetYourEORI
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockSubscriptionFlowManager.currentSubscriptionFlow(any[Request[AnyContent]]))
      .thenReturn(OrganisationSubscriptionFlow)
    test(
      controller
        .submit(isInReviewMode, journey)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }
}
