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

import common.pages.subscription.SubscriptionContactDetailsPage
import common.pages.subscription.SubscriptionContactDetailsPage._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.{ContactDetailsController, SubscriptionFlowManager}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.{AddressViewModel, ContactDetailsModel}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.countries.{Countries, Country}
import uk.gov.hmrc.customs.rosmfrontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.contact_details
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.RegistrationDetailsBuilder.defaultAddress
import util.builders.SessionBuilder
import util.builders.SubscriptionContactDetailsFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ContactDetailsControllerSpec
    extends SubscriptionFlowSpec
    with ControllerSpec
    with BeforeAndAfterEach {

  protected override val mockSubscriptionFlowManager: SubscriptionFlowManager =
    mock[SubscriptionFlowManager]
  protected override val formId: String = SubscriptionContactDetailsPage.formId
  protected override val submitInCreateModeUrl: String =
    ContactDetailsController
      .submit(isInReviewMode = false, Journey.GetYourEORI)
      .url
  protected override val submitInReviewModeUrl: String =
    ContactDetailsController
      .submit(isInReviewMode = true, Journey.GetYourEORI)
      .url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockRegistrationDetails =
    mock[RegistrationDetails](RETURNS_DEEP_STUBS)
  private val mockSubscriptionDetails =
    mock[SubscriptionDetails](RETURNS_DEEP_STUBS)
  private val mockRegistrationDetailsService = mock[RegistrationDetailsService]
  private val mockRegistrationDetailsCreator = mock[RegistrationDetailsCreator]

  private val hintTextTelAndFax =
    "Only enter numbers, for example 01632 960 001"

  private val mockCdsFrontendDataCache = mock[SessionCache]
  private val mockCountries = mock[Countries]
  private val mockOrgTypeLookup = mock[OrgTypeLookup]
  private val contactDetailsView = app.injector.instanceOf[contact_details]
  private val mockAppConfig = mock[AppConfig]

  private val controller = new ContactDetailsController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockRequestSessionData,
    mockCdsFrontendDataCache,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsHolderService,
    mockRegistrationDetailsService,
    mcc,
    contactDetailsView,
    mockRegistrationDetailsCreator,
    mockAppConfig
  )

  private val aFewCountries =
    List(Country("France", "FR"),
         Country("Germany", "DE"),
         Country("Italy", "IT"),
         Country("Albania", "AL"))

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockCdsFrontendDataCache,
      mockSubscriptionFlowManager,
      mockSubscriptionDetailsHolderService
    )
    when(
      mockSubscriptionBusinessService.cachedContactDetailsModel(
        any[HeaderCarrier])).thenReturn(None)
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
      .thenReturn(mockSubscriptionDetails)
    registerSaveContactDetailsMockSuccess()
    mockFunctionWithRegistrationDetails(mockRegistrationDetails)
    setupMockSubscriptionFlowManager(ContactDetailsSubscriptionFlowPageGetEori)
    when(mockCountries.all).thenReturn(aFewCountries)
    when(mockCdsFrontendDataCache.email(any[HeaderCarrier]))
      .thenReturn(Future.successful(Email))
    when(mockCdsFrontendDataCache.mayBeEmail(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(Email)))
    when(
      mockRequestSessionData.userSelectedOrganisationType(
        any[Request[AnyContent]]))
      .thenReturn(Some(CdsOrganisationType("company")))
    when(mockAppConfig.autoCompleteEnabled).thenReturn(true)
  }

  val orgTypeFlows: TableFor3[SubscriptionFlow, String, EtmpOrganisationType] =
    Table[SubscriptionFlow, String, EtmpOrganisationType](
      ("Flow name", "Address Label", "orgType"),
      (IndividualSubscriptionFlow, "Is this the right contact address?", NA),
      (OrganisationSubscriptionFlow,
       "Is this the right contact address?",
       CorporateBody),
      (SoleTraderSubscriptionFlow, "Is this the right contact address?", NA)
    )

  val formModesGYE = Table(
    ("formMode", "showFormFunction"),
    (
      "create GetYourEORI",
      (flow: SubscriptionFlow, orgType: EtmpOrganisationType) =>
        showCreateForm(flow, orgType = orgType)(_)
    ),
    ("review GetYourEORI",
     (flow: SubscriptionFlow, orgType: EtmpOrganisationType) =>
       showReviewForm(flow)(_))
  )

  val formModesMigrate = Table(
    ("formMode", "showFormFunction"),
    (
      "create Migrate",
      (flow: SubscriptionFlow, orgType: EtmpOrganisationType) =>
        showCreateForm(flow, orgType = orgType, journey = Journey.Migrate)(_)
    ),
    (
      "review Migrate",
      (flow: SubscriptionFlow, orgType: EtmpOrganisationType) =>
        showReviewForm(flow, journey = Journey.Migrate)(_)
    )
  )

  forAll(orgTypeFlows) {
    case (flow, expectedLabel, orgType) =>
      s"redirect to next page in subscription flow $flow for mode create Migrate" in {
        setupMockSubscriptionFlowManager(
          ContactDetailsAddressSubscriptionFlowPageMigrate)
        when(mockRegistrationDetails.address).thenReturn(defaultAddress)

        orgType match {
          case CorporateBody =>
            when(
              mockSubscriptionDetailsHolderService.cachedCustomsId(
                any[HeaderCarrier]))
              .thenReturn(Future.successful(None))
            when(
              mockSubscriptionDetailsHolderService.cachedNameIdDetails(
                any[HeaderCarrier]))
              .thenReturn(Future.successful(
                Some(NameIdOrganisationMatchModel("Orgname", "SomeCustomsId"))))
          case _ =>
            when(
              mockSubscriptionDetailsHolderService.cachedCustomsId(
                any[HeaderCarrier]))
              .thenReturn(Future.successful(Some(Utr("SomeCustomsId"))))
            when(
              mockSubscriptionDetailsHolderService.cachedNameIdDetails(
                any[HeaderCarrier]))
              .thenReturn(Future.successful(None))
        }

        showCreateForm(flow, journey = Journey.Migrate, orgType = orgType) {
          result =>
            status(result) shouldBe SEE_OTHER
            result.header.headers(LOCATION) should endWith("next-page-url")
            verify(mockSubscriptionFlowManager, times(1))
              .stepInformation(any())(any[HeaderCarrier],
                                      any[Request[AnyContent]])
        }
      }

      s"fill fields with contact details if stored in cache (new address entered) in subscription flow $flow for Migrate" in {
        mockMigrate()
        when(
          mockSubscriptionBusinessService.cachedContactDetailsModel(
            any[HeaderCarrier]))
          .thenReturn(Some(contactDetailsModel))
        showCreateForm(flow, journey = Journey.Migrate, orgType = orgType) {
          result =>
            val page = CdsPage(bodyOf(result))
            page.getElementText(emailLabelXPath) shouldBe emailAddressFieldLabel
            page.getElementText(emailFieldXPath) shouldBe Email
            page.getElementText(fullNameLabelXPath) shouldBe "Full name"
            page.getElementText(telephoneLabelXPath) shouldBe "Telephone number Only enter numbers, for example 01632 960 001"
            page.getElementText(faxLabelXPath) shouldBe "Fax number (optional) Only enter numbers, for example 01632 960 001"
        }
      }
  }

  "Viewing the create form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.createForm(Journey.GetYourEORI))

    "display back link correctly" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "display the correct text in the heading and intro" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(headingXPath) shouldBe "Who can we contact?"
        page.getElementsText(introXPath) shouldBe "We will use these details to contact you about your EORI number. We will also use them to contact you if there are any issues with your customs activities."
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }

    "display the correct hint text for telephone and fax number field" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(hintTextTelephonXpath) shouldBe hintTextTelAndFax
        page.getElementsText(hintTextFaxXpath) shouldBe hintTextTelAndFax
      }
    }

    "fill fields with contact details if stored in cache (new address entered)" in {
      when(
        mockSubscriptionBusinessService.cachedContactDetailsModel(
          any[HeaderCarrier]))
        .thenReturn(Some(contactDetailsModel))
      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(fullNameFieldXPath) shouldBe FullName
        page.getElementText(emailFieldXPath) shouldBe Email
        page.getElementValue(telephoneFieldXPath) shouldBe Telephone
        page.getElementValue(faxFieldXPath) shouldBe Fax
      }
    }

    "restore state properly if registered address was used" in {
      when(
        mockSubscriptionBusinessService.cachedContactDetailsModel(
          any[HeaderCarrier]))
        .thenReturn(Some(contactDetailsModelWithRegisteredAddress))
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(emailLabelXPath) shouldBe emailAddressFieldLabel
        page.getElementText(emailFieldXPath) shouldBe Email
        page.getElementText(fullNameLabelXPath) shouldBe "Full name"
        page.getElementText(telephoneLabelXPath) shouldBe "Telephone number Only enter numbers, for example 01632 960 001"
        page.getElementText(faxLabelXPath) shouldBe "Fax number (optional) Only enter numbers, for example 01632 960 001"
      }
    }

    "leave fields empty if contact details weren't found in cache" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(fullNameFieldXPath) shouldBe empty
        page.getElementValue(emailFieldXPath) shouldBe empty
        page.getElementValue(telephoneFieldXPath) shouldBe empty
        page.getElementValue(faxFieldXPath) shouldBe empty
      }
    }
  }

  "Viewing the review form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.reviewForm(Journey.GetYourEORI))

    "display relevant data in form fields when subscription details exist in the cache" in {

      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(fullNameFieldXPath) shouldBe FullName
        page.getElementText(emailFieldXPath) shouldBe Email
        page.getElementValue(telephoneFieldXPath) shouldBe Telephone
        page.getElementValue(faxFieldXPath) shouldBe Fax
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

    "display the contact details stored in the cache under as 'subscription details'" in {
      mockFunctionWithRegistrationDetails(mockRegistrationDetails)
      when(mockSubscriptionBusinessService.cachedContactDetailsModel)
        .thenReturn(Some(revisedContactDetailsModel))
      showReviewForm(contactDetailsModel = revisedContactDetailsModel) {
        result =>
          val page = CdsPage(bodyOf(result))
          page.getElementValue(fullNameFieldXPath) shouldBe FullName
          page.getElementText(emailLabelXPath) shouldBe emailAddressFieldLabel
          page.getElementText(emailFieldXPath) shouldBe Email
          page.getElementValue(telephoneFieldXPath) shouldBe Telephone
          page.getElementValue(faxFieldXPath) shouldBe Fax
      }
    }

    "display the contact details stored in the cache under as 'subscription details' for Migrate" in {
      mockMigrate()
      mockFunctionWithRegistrationDetails(mockRegistrationDetails)
      when(mockSubscriptionBusinessService.cachedContactDetailsModel)
        .thenReturn(Some(revisedContactDetailsModel))
      showReviewForm(contactDetailsModel = revisedContactDetailsModel,
                     journey = Journey.Migrate) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(fullNameFieldXPath) shouldBe FullName
        page.getElementText(emailLabelXPath) shouldBe emailAddressFieldLabel
        page.getElementText(emailFieldXPath) shouldBe Email
        page.getElementValue(telephoneFieldXPath) shouldBe Telephone
        page.getElementValue(faxFieldXPath) shouldBe Fax
      }
    }
  }

  "submitting the form in Create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.GetYourEORI)
    )

    "save the details when user chooses to use Registered Address for GYE journey" in {
      submitFormInCreateMode(createFormPersonDetailsMap) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(
            any[HeaderCarrier])
      }
    }

    "produce validation error when full name is not submitted" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsMap + (fullNameFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your contact name"
        page.getElementsText(fullNameFieldLevelErrorXPath) shouldBe "Enter your contact name"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when full name more than 70 characters" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsMap + (fullNameFieldName -> oversizedString(
          70))) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The full name can be a maximum of 70 characters"
        page.getElementsText(fullNameFieldLevelErrorXPath) shouldBe "The full name can be a maximum of 70 characters"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when Telephone is not submitted" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsMap + (telephoneFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your contact telephone number"
        page.getElementsText(telephoneFieldLevelErrorXPath) shouldBe "Enter your contact telephone number"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when Telephone more than 24 characters" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsMap + (telephoneFieldName -> oversizedString(
          24))) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The telephone number must be 24 digits or less"
        page.getElementsText(telephoneFieldLevelErrorXPath) shouldBe "The telephone number must be 24 digits or less"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when Telephone contains invalid characters" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsMap + (telephoneFieldName -> "$£")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Please enter a valid telephone number"
        page.getElementsText(telephoneFieldLevelErrorXPath) shouldBe "Please enter a valid telephone number"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "Allow when Telephone contains plus character" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsMap + (telephoneFieldName -> "+")) { result =>
        status(result) shouldBe SEE_OTHER
      }
    }

    "produce validation error when Fax more than 24 characters" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsMap + (faxFieldName -> oversizedString(24))) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The fax number must be 24 digits or less"
          page.getElementsText(faxFieldLevelErrorXPath) shouldBe "The fax number must be 24 digits or less"
          page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when fax contains invalid characters" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsMap + (faxFieldName -> "$£")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Please enter a valid fax number"
        page.getElementsText(faxFieldLevelErrorXPath) shouldBe "Please enter a valid fax number"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "allow when fax contains plus character" in {
      submitFormInCreateMode(
        createFormMandatoryPersonDetailsFieldsMap + (faxFieldName -> "+")) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }

    "produce validation error when Use registered address is not selected" in {
      submitFormInCreateMode(
        createFormMandatoryPersonDetailsFieldsMap + (telephoneFieldName -> "")) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your contact telephone number"
          page.getElementsText("title") should startWith("Error: ")
      }
    }

    "display page level errors when nothing is entered" in {
      submitFormInCreateMode(createFormAllFieldsPersonDetailsEmptyMap) {
        result =>
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe
            "Enter your contact name " +
              "Enter your contact telephone number"
          page.getElementsText("title") should startWith("Error: ")
      }
    }

    "fail when system fails to create contact details" in {
      val unsupportedException =
        new UnsupportedOperationException("Emulation of service call failure")

      registerSaveContactDetailsMockFailure(unsupportedException)

      val caught = intercept[RuntimeException] {
        submitFormInCreateMode(createFormMandatoryFieldsMap) { result =>
          await(result)
        }
      }
      caught shouldBe unsupportedException
    }

    "allow resubmission in create mode when details are invalid" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap - fullNameFieldName)(
        verifyFormActionInCreateMode)
    }

    "redirect to next page when details are valid" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap)(
        verifyRedirectToNextPageInCreateMode)
    }

    "redirect to next page without validating contact address when 'Is this the right contact address' is Yes and country code is GB" in {
      val params = Map(
        fullNameFieldName -> FullName,
        emailFieldName -> Email,
        telephoneFieldName -> Telephone,
        useRegisteredAddressFlagFieldName -> "true",
        countryCodeFieldName -> "GB"
      )
      submitFormInCreateMode(params)(verifyRedirectToNextPageInCreateMode)
    }
  }

  private def mockMigrate() = {
    when(
      mockSubscriptionDetailsHolderService.cachedCustomsId(any[HeaderCarrier]))
      .thenReturn(Future.successful(None))
    when(
      mockSubscriptionDetailsHolderService.cachedNameIdDetails(
        any[HeaderCarrier]))
      .thenReturn(Future.successful(None))
    val cachedAddressDetails = Some(
      AddressViewModel(street = "Line 1 line 2",
                       city = "line 3",
                       postcode = Some("SW1A 2BQ"),
                       countryCode = "GB")
    )
    when(
      mockSubscriptionDetailsHolderService.cachedAddressDetails(
        any[HeaderCarrier]))
      .thenReturn(Future.successful(cachedAddressDetails))
  }

  private def mockFunctionWithRegistrationDetails(
      registrationDetails: RegistrationDetails) {
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(registrationDetails)
  }

  private def submitFormInCreateMode(
      form: Map[String, String],
      userId: String = defaultUserId,
      journey: Journey.Value = Journey.GetYourEORI
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .submit(isInReviewMode = false, journey)(
          SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  private def showCreateForm(
      subscriptionFlow: SubscriptionFlow = OrganisationSubscriptionFlow,
      journey: Journey.Value = Journey.GetYourEORI,
      orgType: EtmpOrganisationType = CorporateBody
  )(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    if (orgType == NA) {
      when(
        mockRequestSessionData.userSelectedOrganisationType(
          any[Request[AnyContent]]))
        .thenReturn(Some(CdsOrganisationType("sole-trader")))
    } else {
      when(
        mockRequestSessionData.userSelectedOrganisationType(
          any[Request[AnyContent]]))
        .thenReturn(Some(CdsOrganisationType("company")))
    }

    when(
      mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]],
                                    any[HeaderCarrier]))
      .thenReturn(Some(orgType))
    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]]))
      .thenReturn(subscriptionFlow)

    test(
      controller
        .createForm(journey)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def showReviewForm(
      subscriptionFlow: SubscriptionFlow = OrganisationSubscriptionFlow,
      contactDetailsModel: ContactDetailsModel = contactDetailsModel,
      journey: Journey.Value = Journey.GetYourEORI
  )(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]]))
      .thenReturn(subscriptionFlow)
    when(
      mockSubscriptionBusinessService.cachedContactDetailsModel(
        any[HeaderCarrier]))
      .thenReturn(Some(contactDetailsModel))

    test(
      controller
        .reviewForm(journey)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def registerSaveContactDetailsMockSuccess() {
    when(
      mockSubscriptionDetailsHolderService
        .cacheContactDetails(any[ContactDetailsModel])(
          any[HeaderCarrier])
    ).thenReturn(Future.successful(()))
  }

  private def registerSaveContactDetailsMockFailure(exception: Throwable) {
    when(
      mockSubscriptionDetailsHolderService
        .cacheContactDetails(any[ContactDetailsModel])(
          any[HeaderCarrier])
    ).thenReturn(Future.failed(exception))
  }
}
