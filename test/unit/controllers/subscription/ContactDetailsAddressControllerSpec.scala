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
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.{
  ContactDetailsAddressController,
  SubscriptionFlowManager
}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.{
  AddressViewModel,
  ContactDetailsModel
}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{
  RequestSessionData,
  SessionCache
}
import uk.gov.hmrc.customs.rosmfrontend.services.countries.{Countries, Country}
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.contact_address
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.SubscriptionContactDetailsFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ContactDetailsAddressControllerSpec
    extends SubscriptionFlowSpec
    with ControllerSpec
    with BeforeAndAfterEach {

  protected override val mockSubscriptionFlowManager: SubscriptionFlowManager =
    mock[SubscriptionFlowManager]
  protected override val formId: String = SubscriptionContactDetailsPage.formId
  protected override val submitInCreateModeUrl: String =
    ContactDetailsAddressController
      .submit(isInReviewMode = false, Journey.GetYourEORI)
      .url
  protected override val submitInReviewModeUrl: String =
    ContactDetailsAddressController
      .submit(isInReviewMode = true, Journey.GetYourEORI)
      .url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockRegistrationDetails =
    mock[RegistrationDetails](RETURNS_DEEP_STUBS)
  private val mockSubscriptionDetails =
    mock[SubscriptionDetails](RETURNS_DEEP_STUBS)
  private val mockRegistrationDetailsService = mock[RegistrationDetailsService]
  private val mockCountries = mock[Countries]

  private val mockCdsFrontendDataCache = mock[SessionCache]
  private val mockOrgTypeLookup = mock[OrgTypeLookup]
  private val contactDetailsView = app.injector.instanceOf[contact_address]

  private val controller = new ContactDetailsAddressController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockRequestSessionData,
    mockCdsFrontendDataCache,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsHolderService,
    mockCountries,
    mcc,
    contactDetailsView
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

  "Viewing the create form " should {
    val headingXpath = "//h1"

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.createForm(Journey.GetYourEORI))

    "display back link correctly" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "display the correct text in the heading and intro" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(headingXpath) shouldBe "Enter the company's contact address"
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }

    "display the correct  text for Address  field" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText("//*[@id='street-outer']") shouldBe "Address line 1"
        page.getElementsText("//*[@id='city-outer']") shouldBe "Town or city"
        page.getElementsText("//*[@id='postcode-outer']") shouldBe "Postcode"
        page.getElementsText("//*[@id='country-outer']") should startWith(
          "Country")
      }
    }

    "fill fields with contact details if stored in cache (new address entered)" in {
      when(
        mockSubscriptionBusinessService.cachedContactDetailsModel(
          any[HeaderCarrier]))
        .thenReturn(Some(contactDetailsModel))
      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(streetFieldXPath) shouldBe "Line 1"
        page.getElementValue(cityFieldXPath) shouldBe "city name"
        page.getElementValue(postcodeFieldXPath) shouldBe "SW1A 2BQ"
        page.getElementValue(countryCodeSelectedOptionXPath) shouldBe "FR"
      }
    }

    "leave fields empty if contact details weren't found in cache" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(streetFieldXPath) shouldBe empty
        page.getElementValue(cityFieldXPath) shouldBe empty
        page.getElementValue(postcodeFieldXPath) shouldBe empty
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
        page.getElementValue(streetFieldXPath) shouldBe "Line 1"
        page.getElementValue(cityFieldXPath) shouldBe "city name"
        page.getElementValue(postcodeFieldXPath) shouldBe "SW1A 2BQ"
        page.getElementValue(countryCodeSelectedOptionXPath) shouldBe "FR"
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

    "save the details when user chooses not to use Registered Address for GYE journey" in {
      setupMockSubscriptionFlowManager(
        ContactDetailsAddressSubscriptionFlowPageGetEori)

      submitFormInCreateMode(createFormAddressMap) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(any[ContactDetailsModel])(
            any[HeaderCarrier])
      }
    }

    "save the details when user chooses not to use Registered Address for Migrate journey" in {
      setupMockSubscriptionFlowManager(
        ContactDetailsAddressSubscriptionFlowPageMigrate)

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

      when(
        mockRegistrationDetailsService.cacheAddress(any())(
          any[HeaderCarrier]())).thenReturn(Future.successful(true))
      setupMockSubscriptionFlowManager(
        ContactDetailsSubscriptionFlowPageMigrate)

      submitFormInCreateMode(createFormAddressMap, journey = Journey.Migrate) {
        result =>
          await(result)
          verify(mockSubscriptionDetailsHolderService)
            .cacheContactDetails(any[ContactDetailsModel])(
              any[HeaderCarrier])
      }
    }

    "produce validation error when Street is not submitted" in {
      submitFormInCreateMode(createFormAddressMap + (streetFieldName -> "")) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter the first line of your address"
          page.getElementsText(streetFieldLevelErrorXPath) shouldBe "Enter the first line of your address"
          page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when city is not submitted" in {
      submitFormInCreateMode(createFormAddressMap + (cityFieldName -> "")) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your town or city"
          page.getElementsText(cityFieldLevelErrorXPath) shouldBe "Enter your town or city"
          page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when postcode is not submitted" in {
      submitFormInCreateMode(
        createFormAddressMap + (postcodeFieldName -> "") + (countryCodeFieldName -> "GB")) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter a valid postcode"
          page.getElementsText(postcodeFieldLevelErrorXPath) shouldBe "Enter a valid postcode"
          page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when country is not selected" in {
      submitFormInCreateMode(
        createFormAddressMap + (countryCodeFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your country"
        page.getElementsText(countryFieldLevelErrorXPath) shouldBe "Enter your country"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "display page level errors when nothing is entered" in {
      submitFormInCreateMode(createFormAddressEmptyFormMap) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe
          "Enter the first line of your address Enter your town or city Enter your country"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "redirect to next page when details are valid" in {
      setupMockSubscriptionFlowManager(
        ContactDetailsAddressSubscriptionFlowPageGetEori)
      submitFormInCreateMode(createFormAddressMap)(
        verifyRedirectToNextPageInCreateMode)
    }

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

}
