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
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.{ContactDetailsIsRightAddressController, SubscriptionFlowManager}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Address
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.{AddressViewModel, ContactDetailsModel}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.countries.{Countries, Country}
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.contact_is_right_address
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SubscriptionContactDetailsFormBuilder._
import util.builders.{SessionBuilder, YesNoFormBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ContactDetailsIsThisRighAddressControllerSpec extends SubscriptionFlowSpec with ControllerSpec with BeforeAndAfterEach {

  protected override val mockSubscriptionFlowManager: SubscriptionFlowManager = mock[SubscriptionFlowManager]
  protected override val formId: String = "isThisRightContactAddressForm"
  protected override val submitInCreateModeUrl: String =
    ContactDetailsIsRightAddressController.submit(false, Journey.GetYourEORI).url
  protected override val submitInReviewModeUrl: String =
    ContactDetailsIsRightAddressController.submit(true, Journey.GetYourEORI).url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockRegistrationDetails = mock[RegistrationDetails](RETURNS_DEEP_STUBS)
  private val mockSubscriptionDetails = mock[SubscriptionDetails](RETURNS_DEEP_STUBS)
  val address = Address("add1", Some("add2"), Some("add3"), Some("add4"), Some("postcode"), "GB")
  private val mockCdsFrontendDataCache = mock[SessionCache]
  private val mockCountries = mock[Countries]
  private val mockOrgTypeLookup = mock[OrgTypeLookup]
  private val contactDetailsIsThisRightAddressView = app.injector.instanceOf[contact_is_right_address]

  private val controller = new ContactDetailsIsRightAddressController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockCdsFrontendDataCache,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsHolderService,
    mcc,
    contactDetailsIsThisRightAddressView
  )

  private val aFewCountries =
    List(Country("France", "FR"), Country("Germany", "DE"), Country("Italy", "IT"), Country("Albania", "AL"))
  val cachedAddressDetails = Some(
    AddressViewModel(street = "Line 1 line 2",
      city = "line 3",
      postcode = Some("SW1A 2BQ"),
      countryCode = "GB")
  )

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockCdsFrontendDataCache,
      mockSubscriptionFlowManager,
      mockSubscriptionDetailsHolderService
    )
    when(mockSubscriptionBusinessService.cachedContactDetailsModel(any[HeaderCarrier])).thenReturn(None)
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(mockSubscriptionDetails)
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier])).thenReturn(mockRegistrationDetails)
    when(mockRegistrationDetails.address).thenReturn(address)
    registerSaveContactDetailsMockSuccess()
    mockFunctionWithRegistrationDetails(mockRegistrationDetails)
    setupMockSubscriptionFlowManager(ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori)
    when(mockCountries.all).thenReturn(aFewCountries)
    when(mockCdsFrontendDataCache.email(any[HeaderCarrier])).thenReturn(Future.successful(Email))
    when(mockCdsFrontendDataCache.mayBeEmail(any[HeaderCarrier])).thenReturn(Future.successful(Some(Email)))
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(CdsOrganisationType("company")))
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(mockSubscriptionDetails)
    when(mockSubscriptionDetails.addressDetails).thenReturn(cachedAddressDetails)
    when(mockSubscriptionDetailsHolderService.cachedAddressDetails).thenReturn(cachedAddressDetails)

  }
  val headingXpath = "//h1"

  "Loading the page in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.GetYourEORI))

    "display the form" in {
      showCreateForm() { result =>
      val page = CdsPage(bodyOf(result))
        page.formAction(formId) shouldBe submitInCreateModeUrl
        page.getElementsText(addressParaXPath) shouldBe "add1 add2 add3 postcode United Kingdom"
      }
    }
  }

  "Viewing the create form " should {
    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.GetYourEORI))

    "display back link correctly" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "display the correct text in the heading and intro" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(headingXpath) shouldBe "Is this the contact address you want to use?"
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }
  }

  "Viewing the create form for subscription " should {
    val cachedAddressDetails = Some(
      AddressViewModel(street = "Line 1 line 2",
        city = "line 3",
        postcode = Some("SW1A 2BQ"),
        countryCode = "GB")
    )
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(mockSubscriptionDetails)
    when(mockSubscriptionDetails.addressDetails).thenReturn(cachedAddressDetails)

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.Migrate))

    "display back link correctly" in {
      showCreateForm(journey = Journey.Migrate)(verifyBackLinkInCreateModeRegister)
    }

    "display the correct text in the heading and intro" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(headingXpath) shouldBe "Is this the contact address you want to use?"
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }
  }

  "Viewing the review form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.reviewForm(Journey.GetYourEORI))


    "display back link correctly" in {
      showReviewForm()(verifyBackLinkInCreateModeRegister)
    }

    "display the correct text in the heading and intro" in {
      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(headingXpath) shouldBe "Is this the contact address you want to use?"
      }
    }

    "display the correct text for the continue button" in {
      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      }
    }
  }

  "submitting the form in Create mode for Subscription" should {
    val contactDetailsModel = ContactDetailsModel(
      fullName = FullName,
      emailAddress = Email,
      telephone = Telephone,
      fax = Some(Fax),
      street = Some(Street),
      city = Some(City),
      postcode = Some(Postcode),
      countryCode = Some(CountryCode),
      useAddressFromRegistrationDetails = Some(false)
    )

    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(mockSubscriptionDetails)
    when(mockSubscriptionDetails.addressDetails).thenReturn(cachedAddressDetails)

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.Migrate)
    )

    "save the details when user chooses to use Registered Address having contact details for Subscription journey" in {
      when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier])).thenReturn(cachedAddressDetails)
      when(mockSubscriptionDetails.contactDetails).thenReturn(Some(contactDetailsModel))

      setupMockSubscriptionFlowManager(ContactDetailsAddressSubscriptionFlowPageMigrate)

      submitFormInCreateMode(YesNoFormBuilder.ValidRequest,journey = Journey.Migrate) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(any[HeaderCarrier])
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "next-page-url"
      }
    }

    "save the details when user chooses to use Registered Address for Subscription journey" in {
     when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier])).thenReturn(cachedAddressDetails)

      setupMockSubscriptionFlowManager(ContactDetailsAddressSubscriptionFlowPageMigrate)

      submitFormInCreateMode(YesNoFormBuilder.ValidRequest,journey = Journey.Migrate) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(any[HeaderCarrier])
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "next-page-url"
      }
    }

    "save the details when user chooses not to use Registered Address for Subscription journey" in {
      when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier])).thenReturn(cachedAddressDetails)

      setupMockSubscriptionFlowManager(ContactDetailsIsThisRightAddressSubscriptionFlowPageMigrate)

      submitFormInCreateMode(YesNoFormBuilder.validRequestNo, journey = Journey.Migrate) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(any[HeaderCarrier])
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "next-page-url"
      }
    }

    "produce validation error is this right address is not selected  and  submitted" in {
      when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier])).thenReturn(cachedAddressDetails)

      val fieldLevelErrorYesNoAnswer: String = "//*[@id='yes-no-answer-field']//span[@class='error-message']"

      submitFormInCreateMode(YesNoFormBuilder.invalidRequest, journey = Journey.Migrate) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Tell us if the contact address is right"
        page.getElementsText(fieldLevelErrorYesNoAnswer) shouldBe "Error:Tell us if the contact address is right"
        page.getElementsText("title") should startWith("Error: ")
      }
    }
  }

  "submitting the form in review mode for Subscription" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = true, Journey.Migrate)
    )

    "save the details when user chooses to use Registered Address for Subscription journey" in {
      when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier])).thenReturn(cachedAddressDetails)

      setupMockSubscriptionFlowManager(ContactDetailsAddressSubscriptionFlowPageMigrate)
      submitFormInCreateMode(YesNoFormBuilder.ValidRequest, journey = Journey.Migrate) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(any[HeaderCarrier])
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "next-page-url"
      }
    }



    "save the details when user chooses not to use Registered Address for Subscription journey" in {
      when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier])).thenReturn(cachedAddressDetails)

      setupMockSubscriptionFlowManager(ContactDetailsIsThisRightAddressSubscriptionFlowPageMigrate)

      submitFormInCreateMode(YesNoFormBuilder.validRequestNo, journey = Journey.Migrate,isInReviewMode = true) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(any[HeaderCarrier])
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "next-page-url/review"
      }
    }

    "produce validation error is this right address is not selected  and  submitted" in {

      val fieldLevelErrorYesNoAnswer: String = "//*[@id='yes-no-answer-field']//span[@class='error-message']"
      when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier])).thenReturn(cachedAddressDetails)

      submitFormInCreateMode(YesNoFormBuilder.invalidRequest,journey = Journey.Migrate) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Tell us if the contact address is right"
        page.getElementsText(fieldLevelErrorYesNoAnswer) shouldBe "Error:Tell us if the contact address is right"
        page.getElementsText("title") should startWith("Error: ")
      }
    }
  }

  "submitting the form in Create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.GetYourEORI)
    )

    "save the details when user chooses to use Registered Address for GYE journey" in {
      setupMockSubscriptionFlowManager(ContactDetailsAddressSubscriptionFlowPageGetEori)

      submitFormInCreateMode(YesNoFormBuilder.ValidRequest) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(any[HeaderCarrier])
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "next-page-url"
      }
    }

    "save the details when user chooses not to use Registered Address for GYE journey" in {
      setupMockSubscriptionFlowManager(ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori)

      submitFormInCreateMode(YesNoFormBuilder.validRequestNo) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(any[HeaderCarrier])
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "next-page-url"
      }
    }

    "produce validation error is this right address is not selected  and  submitted" in {
      val fieldLevelErrorYesNoAnswer: String = "//*[@id='yes-no-answer-field']//span[@class='error-message']"

      submitFormInCreateMode(YesNoFormBuilder.invalidRequest) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Tell us if the contact address is right"
        page.getElementsText(fieldLevelErrorYesNoAnswer) shouldBe "Error:Tell us if the contact address is right"
        page.getElementsText("title") should startWith("Error: ")
      }
    }
  }

  "submitting the form in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = true, Journey.GetYourEORI)
    )

    "save the details when user chooses to use Registered Address for GYE journey" in {
      setupMockSubscriptionFlowManager(ContactDetailsAddressSubscriptionFlowPageGetEori)

      submitFormInCreateMode(YesNoFormBuilder.ValidRequest) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(any[HeaderCarrier])
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "next-page-url"
      }
    }


    "save the details when user chooses not to use Registered Address for GYE journey" in {
      setupMockSubscriptionFlowManager(ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori)

      submitFormInCreateMode(YesNoFormBuilder.validRequestNo,isInReviewMode = true) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(any[HeaderCarrier])
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "next-page-url/review"
      }
    }

    "produce validation error is this right address is not selected  and  submitted" in {
       val fieldLevelErrorYesNoAnswer: String = "//*[@id='yes-no-answer-field']//span[@class='error-message']"

      submitFormInCreateMode(YesNoFormBuilder.invalidRequest) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Tell us if the contact address is right"
        page.getElementsText(fieldLevelErrorYesNoAnswer) shouldBe "Error:Tell us if the contact address is right"
        page.getElementsText("title") should startWith("Error: ")
      }
    }
  }


  private def mockFunctionWithRegistrationDetails(registrationDetails: RegistrationDetails) {
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier])).thenReturn(registrationDetails)
  }

  private def submitFormInCreateMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    journey: Journey.Value = Journey.GetYourEORI,
    isInReviewMode:Boolean = false
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .submit(isInReviewMode = isInReviewMode, journey)(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  private def showCreateForm(
    subscriptionFlow: SubscriptionFlow = OrganisationSubscriptionFlow,
    journey: Journey.Value = Journey.GetYourEORI,
    orgType: EtmpOrganisationType = CorporateBody
  )(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    if (orgType == NA) {
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
        .thenReturn(Some(CdsOrganisationType("sole-trader")))
    } else {
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
        .thenReturn(Some(CdsOrganisationType("company")))
    }


    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(orgType))
    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)

    test(controller.createForm(journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def showReviewForm(
    subscriptionFlow: SubscriptionFlow = OrganisationSubscriptionFlow,
    contactDetailsModel: ContactDetailsModel = contactDetailsModel,
    journey: Journey.Value = Journey.GetYourEORI
  )(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)
    when(mockSubscriptionBusinessService.cachedContactDetailsModel(any[HeaderCarrier]))
      .thenReturn(Some(contactDetailsModel))

    test(controller.reviewForm(journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def registerSaveContactDetailsMockSuccess() {
    when(
      mockSubscriptionDetailsHolderService
        .cacheContactDetails(any[ContactDetailsModel])(any[HeaderCarrier])
    ).thenReturn(Future.successful(()))
  }

}
