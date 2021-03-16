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
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.{ContactDetailsController, SubscriptionFlowManager}
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Address
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
import util.builders.RegistrationDetailsBuilder.{defaultAddress, defaultAddressWithMandatoryValuesOnly, defaultCountryName}
import util.builders.SessionBuilder
import util.builders.SubscriptionContactDetailsFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ContactDetailsControllerSpec extends SubscriptionFlowSpec with ControllerSpec with BeforeAndAfterEach {

  protected override val mockSubscriptionFlowManager: SubscriptionFlowManager = mock[SubscriptionFlowManager]
  protected override val formId: String = SubscriptionContactDetailsPage.formId
  protected override val submitInCreateModeUrl: String =
    ContactDetailsController.submit(isInReviewMode = false, Journey.GetYourEORI).url
  protected override val submitInReviewModeUrl: String =
    ContactDetailsController.submit(isInReviewMode = true, Journey.GetYourEORI).url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockRegistrationDetails = mock[RegistrationDetails](RETURNS_DEEP_STUBS)
  private val mockSubscriptionDetails = mock[SubscriptionDetails](RETURNS_DEEP_STUBS)
  private val mockRegistrationDetailsService = mock[RegistrationDetailsService]
  private val mockRegistrationDetailsCreator = mock[RegistrationDetailsCreator]

  private val hintTextTelAndFax = "Only enter numbers, for example 01632 960 001"

  private val mockCdsFrontendDataCache = mock[SessionCache]
  private val mockCountries = mock[Countries]
  private val mockOrgTypeLookup = mock[OrgTypeLookup]
  private val contactDetailsView = app.injector.instanceOf[contact_details]

  private val controller = new ContactDetailsController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockRequestSessionData,
    mockCdsFrontendDataCache,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsHolderService,
    mockCountries,
    mockOrgTypeLookup,
    mockRegistrationDetailsService,
    mcc,
    contactDetailsView,
    mockRegistrationDetailsCreator
  )

  private val aFewCountries =
    List(Country("France", "FR"), Country("Germany", "DE"), Country("Italy", "IT"), Country("Albania", "AL"))

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockCdsFrontendDataCache,
      mockSubscriptionFlowManager,
      mockSubscriptionDetailsHolderService
    )
    when(mockSubscriptionBusinessService.cachedContactDetailsModel(any[HeaderCarrier])).thenReturn(None)
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(mockSubscriptionDetails)
    registerSaveContactDetailsMockSuccess()
    mockFunctionWithRegistrationDetails(mockRegistrationDetails)
    setupMockSubscriptionFlowManager(ContactDetailsSubscriptionFlowPageGetEori)
    when(mockCountries.all).thenReturn(aFewCountries)
    when(mockCdsFrontendDataCache.email(any[HeaderCarrier])).thenReturn(Future.successful(Email))
    when(mockCdsFrontendDataCache.mayBeEmail(any[HeaderCarrier])).thenReturn(Future.successful(Some(Email)))

  }

  val orgTypeFlows: TableFor3[SubscriptionFlow, String, EtmpOrganisationType] =
    Table[SubscriptionFlow, String, EtmpOrganisationType](
      ("Flow name", "Address Label", "orgType"),
      (IndividualSubscriptionFlow, "Is this the right contact address?", NA),
      (OrganisationSubscriptionFlow, "Is this the right contact address?", CorporateBody),
      (SoleTraderSubscriptionFlow, "Is this the right contact address?", NA)
    )

  val formModesGYE = Table(
    ("formMode", "showFormFunction"),
    (
      "create GetYourEORI",
      (flow: SubscriptionFlow, orgType: EtmpOrganisationType) => showCreateForm(flow, orgType = orgType)(_)
    ),
    ("review GetYourEORI", (flow: SubscriptionFlow, orgType: EtmpOrganisationType) => showReviewForm(flow)(_))
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
      (flow: SubscriptionFlow, orgType: EtmpOrganisationType) => showReviewForm(flow, journey = Journey.Migrate)(_)
    )
  )

  forAll(formModesGYE) { (formMode, showFormFunction) =>
    s"The registration address when viewing the $formMode form" should {
      forAll(orgTypeFlows) {
        case (flow, expectedLabel, orgType) =>
          s"display appropriate label for address question in subscription flow $flow for mode $formMode" in {
            when(mockRegistrationDetails.address).thenReturn(defaultAddress)
            showFormFunction(flow, orgType) { result =>
              val page = CdsPage(bodyOf(result))
              page.getElementsText(registeredAddressQuestionXPath) shouldBe expectedLabel
            }
          }
      }

      s"display address correctly when all fields are populated for mode $formMode" in {
        when(mockRegistrationDetails.address).thenReturn(defaultAddress)
        showFormFunction(OrganisationSubscriptionFlow, CorporateBody) { result =>
          val page = CdsPage(bodyOf(result))
          val expectedAddress =
            s"${defaultAddress.addressLine1} ${defaultAddress.addressLine2.get}<br>${defaultAddress.addressLine3.get}<br>${defaultAddress.postalCode.get}<br>$defaultCountryName"
          page.getElementsHtml(registeredAddressParaXPath) shouldBe expectedAddress
        }
      }

      s"display address correctly when only mandatory fields are populated for mode $formMode" in {
        when(mockRegistrationDetails.address).thenReturn(defaultAddressWithMandatoryValuesOnly)
        showFormFunction(OrganisationSubscriptionFlow, CorporateBody) { result =>
          val page = CdsPage(bodyOf(result))
          val expectedAddress = s"${defaultAddressWithMandatoryValuesOnly.addressLine1}<br>$defaultCountryName"
          page.getElementsHtml(registeredAddressParaXPath) shouldBe expectedAddress
        }
      }

      s"display country correctly when EU address is used for mode $formMode" in {
        val addressFirstLine = "euAddressFirstLine"
        val euAddress = Address(addressFirstLine, None, None, None, None, "PL")
        when(mockRegistrationDetails.address).thenReturn(euAddress)
        showFormFunction(OrganisationSubscriptionFlow, CorporateBody) { result =>
          val page = CdsPage(bodyOf(result))
          val expectedAddress = s"$addressFirstLine<br>Poland"
          page.getElementsHtml(registeredAddressParaXPath) shouldBe expectedAddress
        }
      }

      s"display country correctly when non-EU address is used for mode $formMode" in {
        val addressFirstLine = "nonEuAddressFirstLine"
        val nonEuAddress = Address(addressFirstLine, None, None, None, None, "CA")
        when(mockRegistrationDetails.address).thenReturn(nonEuAddress)
        showFormFunction(OrganisationSubscriptionFlow, CorporateBody) { result =>
          val page = CdsPage(bodyOf(result))
          val expectedAddress = s"$addressFirstLine<br>Canada"
          page.getElementsHtml(registeredAddressParaXPath) shouldBe expectedAddress
        }
      }
    }
  }

  forAll(formModesMigrate) { (formMode, showFormFunction) =>
    s"The registration address when viewing the $formMode form" should {
      forAll(orgTypeFlows) {
        case (flow, expectedLabel, orgType) =>
          s"display appropriate label for address question in subscription flow $flow for mode $formMode" in {
            setupMockSubscriptionFlowManager(ContactDetailsSubscriptionFlowPageMigrate)
            when(mockRegistrationDetails.address).thenReturn(defaultAddress)
            mockMigrate()
            showFormFunction(flow, orgType) { result =>
              val page = CdsPage(bodyOf(result))
              page.getElementsText(registeredAddressQuestionXPath) shouldBe expectedLabel
            }
          }
      }

      s"display address correctly when all fields are populated for mode $formMode" in {
        mockMigrate()
        showFormFunction(OrganisationSubscriptionFlow, CorporateBody) { result =>
          val page = CdsPage(bodyOf(result))
          val expectedAddress =
            s"${defaultAddress.addressLine1} ${defaultAddress.addressLine2.get}<br>${defaultAddress.addressLine3.get}<br>${defaultAddress.postalCode.get}<br>$defaultCountryName"
          page.getElementsHtml(registeredAddressParaXPath) shouldBe expectedAddress
        }
      }

      s"display address correctly when only mandatory fields are populated for mode $formMode" in {
        mockMigrate()
        val cachedAddressDetails =
          Some(AddressViewModel(street = "Line 1", city = "", postcode = None, countryCode = "GB"))
        when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier]))
          .thenReturn(Future.successful(cachedAddressDetails))
        showFormFunction(OrganisationSubscriptionFlow, CorporateBody) { result =>
          val page = CdsPage(bodyOf(result))
          val expectedAddress = s"${defaultAddressWithMandatoryValuesOnly.addressLine1}<br>$defaultCountryName"
          page.getElementsHtml(registeredAddressParaXPath) shouldBe expectedAddress
        }
      }

      s"display country correctly when EU address is used for mode $formMode" in {
        mockMigrate()
        val cachedAddressDetails =
          Some(AddressViewModel(street = "euAddressFirstLine", city = "", postcode = None, countryCode = "PL"))
        when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier]))
          .thenReturn(Future.successful(cachedAddressDetails))
        showFormFunction(OrganisationSubscriptionFlow, CorporateBody) { result =>
          val page = CdsPage(bodyOf(result))
          val expectedAddress = s"euAddressFirstLine<br>Poland"
          page.getElementsHtml(registeredAddressParaXPath) shouldBe expectedAddress
        }
      }

      s"display country correctly when non-EU address is used for mode $formMode" in {
        mockMigrate()
        val cachedAddressDetails =
          Some(AddressViewModel(street = "nonEuAddressFirstLine", city = "", postcode = None, countryCode = "CA"))
        when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier]))
          .thenReturn(Future.successful(cachedAddressDetails))
        showFormFunction(OrganisationSubscriptionFlow, CorporateBody) { result =>
          val page = CdsPage(bodyOf(result))
          val expectedAddress = s"nonEuAddressFirstLine<br>Canada"
          page.getElementsHtml(registeredAddressParaXPath) shouldBe expectedAddress
        }
      }
    }
  }

  forAll(orgTypeFlows) {
    case (flow, expectedLabel, orgType) =>
      s"throw IllegalStateException when no address details cached in subscription flow $flow for mode create Migrate" in {
        setupMockSubscriptionFlowManager(ContactDetailsSubscriptionFlowPageMigrate)
        when(mockRegistrationDetails.address).thenReturn(defaultAddress)
        when(mockSubscriptionDetailsHolderService.cachedCustomsId(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))
        when(mockSubscriptionDetailsHolderService.cachedNameIdDetails(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))
        when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))

        showCreateForm(flow, journey = Journey.Migrate, orgType = orgType) { result =>
          val caught = intercept[IllegalStateException] {
            await(result)
          }
          caught.getMessage shouldBe "No addressViewModel details found in cache"
        }
      }

      s"redirect to next page in subscription flow $flow for mode create Migrate" in {
        setupMockSubscriptionFlowManager(ContactDetailsSubscriptionFlowPageMigrate)
        when(mockRegistrationDetails.address).thenReturn(defaultAddress)

        orgType match {
          case CorporateBody =>
            when(mockSubscriptionDetailsHolderService.cachedCustomsId(any[HeaderCarrier]))
              .thenReturn(Future.successful(None))
            when(mockSubscriptionDetailsHolderService.cachedNameIdDetails(any[HeaderCarrier]))
              .thenReturn(Future.successful(Some(NameIdOrganisationMatchModel("Orgname", "SomeCustomsId"))))
          case _ =>
            when(mockSubscriptionDetailsHolderService.cachedCustomsId(any[HeaderCarrier]))
              .thenReturn(Future.successful(Some(Utr("SomeCustomsId"))))
            when(mockSubscriptionDetailsHolderService.cachedNameIdDetails(any[HeaderCarrier]))
              .thenReturn(Future.successful(None))
        }

        showCreateForm(flow, journey = Journey.Migrate, orgType = orgType) { result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) should endWith("next-page-url")
          verify(mockSubscriptionFlowManager, times(1))
            .stepInformation(any())(any[HeaderCarrier], any[Request[AnyContent]])
        }
      }

      s"fill fields with contact details if stored in cache (new address entered) in subscription flow $flow for Migrate" in {
        mockMigrate()
        when(mockSubscriptionBusinessService.cachedContactDetailsModel(any[HeaderCarrier]))
          .thenReturn(Some(contactDetailsModel))
        showCreateForm(flow, journey = Journey.Migrate, orgType = orgType) { result =>
          val page = CdsPage(bodyOf(result))
          page.getElementText(emailLabelXPath) shouldBe emailAddressFieldLabel
          page.getElementText(emailFieldXPath) shouldBe Email
          page.getElementValue(fullNameFieldXPath) shouldBe FullName
          page.getElementValue(telephoneFieldXPath) shouldBe Telephone
          page.getElementValue(faxFieldXPath) shouldBe Fax
          page.radioButtonIsChecked(useRegisteredAddressYesRadioButtonXPath) shouldBe false
          page.radioButtonIsChecked(useRegisteredAddressNoRadioButtonXPath) shouldBe true
          page.getElementValue(streetFieldXPath) shouldBe Street
          page.getElementValue(cityFieldXPath) shouldBe City
          page.getElementValue(postcodeFieldXPath) shouldBe Postcode
          page.getElementValue(countryCodeSelectedOptionXPath) shouldBe CountryCode
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
      when(mockSubscriptionBusinessService.cachedContactDetailsModel(any[HeaderCarrier]))
        .thenReturn(Some(contactDetailsModel))
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(fullNameFieldXPath) shouldBe FullName
        page.getElementText(emailFieldXPath) shouldBe Email
        page.getElementValue(telephoneFieldXPath) shouldBe Telephone
        page.getElementValue(faxFieldXPath) shouldBe Fax
        page.radioButtonIsChecked(useRegisteredAddressYesRadioButtonXPath) shouldBe false
        page.radioButtonIsChecked(useRegisteredAddressNoRadioButtonXPath) shouldBe true
        page.getElementValue(streetFieldXPath) shouldBe Street
        page.getElementValue(cityFieldXPath) shouldBe City
        page.getElementValue(postcodeFieldXPath) shouldBe Postcode
        page.getElementValue(countryCodeSelectedOptionXPath) shouldBe CountryCode
      }
    }

    "restore state properly if registered address was used" in {
      when(mockSubscriptionBusinessService.cachedContactDetailsModel(any[HeaderCarrier]))
        .thenReturn(Some(contactDetailsModelWithRegisteredAddress))
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(fullNameFieldXPath) shouldBe FullName
        page.getElementText(emailFieldXPath) shouldBe Email
        page.getElementValue(telephoneFieldXPath) shouldBe Telephone
        page.getElementValue(faxFieldXPath) shouldBe Fax
        page.radioButtonIsChecked(useRegisteredAddressYesRadioButtonXPath) shouldBe true
        page.radioButtonIsChecked(useRegisteredAddressNoRadioButtonXPath) shouldBe false
        page.getElementValue(streetFieldXPath) shouldBe empty
        page.getElementValue(cityFieldXPath) shouldBe empty
        page.getElementValue(postcodeFieldXPath) shouldBe empty
        page.elementIsPresent(countryCodeSelectedOptionXPath) shouldBe false
      }
    }

    "leave fields empty if contact details weren't found in cache" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(fullNameFieldXPath) shouldBe empty
        page.getElementValue(emailFieldXPath) shouldBe empty
        page.getElementValue(telephoneFieldXPath) shouldBe empty
        page.getElementValue(faxFieldXPath) shouldBe empty
        page.getElementValue(streetFieldXPath) shouldBe empty
        page.getElementValue(cityFieldXPath) shouldBe empty
        page.getElementValue(postcodeFieldXPath) shouldBe empty
        page.elementIsPresent(countryCodeSelectedOptionXPath) shouldBe false
      }
    }
  }

  "Viewing the review form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.reviewForm(Journey.GetYourEORI))

    "display relevant data in form fields when subscription details exist in the cache" in {

      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(fullNameFieldXPath) shouldBe FullName
        page.getElementText(emailFieldXPath) shouldBe Email
        page.getElementValue(telephoneFieldXPath) shouldBe Telephone
        page.getElementValue(faxFieldXPath) shouldBe Fax
        page.getElementValue(streetFieldXPath) shouldBe Street
        page.getElementValue(cityFieldXPath) shouldBe City
        page.getElementValue(postcodeFieldXPath) shouldBe Postcode
        page.getElementValue(countryCodeSelectedOptionXPath) shouldBe CountryCode
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
      when(mockSubscriptionBusinessService.cachedContactDetailsModel).thenReturn(Some(revisedContactDetailsModel))
      showReviewForm(contactDetailsModel = revisedContactDetailsModel) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(fullNameFieldXPath) shouldBe FullName
        page.getElementText(emailLabelXPath) shouldBe emailAddressFieldLabel
        page.getElementText(emailFieldXPath) shouldBe Email
        page.getElementValue(telephoneFieldXPath) shouldBe Telephone
        page.getElementValue(faxFieldXPath) shouldBe Fax
        page.getElementValue(streetFieldXPath) shouldBe Street
        page.getElementValue(cityFieldXPath) shouldBe City
        page.getElementValue(postcodeFieldXPath) shouldBe Postcode
        page.getElementValue(countryCodeSelectedOptionXPath) shouldBe RevisedCountryCode
      }
    }

    "display the contact details stored in the cache under as 'subscription details' for Migrate" in {
      mockMigrate()
      mockFunctionWithRegistrationDetails(mockRegistrationDetails)
      when(mockSubscriptionBusinessService.cachedContactDetailsModel).thenReturn(Some(revisedContactDetailsModel))
      showReviewForm(contactDetailsModel = revisedContactDetailsModel, journey = Journey.Migrate) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(fullNameFieldXPath) shouldBe FullName
        page.getElementText(emailLabelXPath) shouldBe emailAddressFieldLabel
        page.getElementText(emailFieldXPath) shouldBe Email
        page.getElementValue(telephoneFieldXPath) shouldBe Telephone
        page.getElementValue(faxFieldXPath) shouldBe Fax
        page.getElementValue(streetFieldXPath) shouldBe Street
        page.getElementValue(cityFieldXPath) shouldBe City
        page.getElementValue(postcodeFieldXPath) shouldBe Postcode
        page.getElementValue(countryCodeSelectedOptionXPath) shouldBe RevisedCountryCode
      }
    }
  }

  "submitting the form in Create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.GetYourEORI)
    )

    "save the details when user chooses to use Registered Address for GYE journey" in {
      submitFormInCreateMode(createFormAllFieldsWhenUseRegAddressMap) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(meq(createContactDetailsViewModelWhenUseRegAddress), meq(false))(any[HeaderCarrier])
      }
    }

    "save the details when user chooses to use Registered Address for Migrate journey" in {
      val cachedAddressDetails = Some(
        AddressViewModel(street = "Line 1 line 2", city = "line 3", postcode = Some("SW1A 2BQ"), countryCode = "GB")
      )
      when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(cachedAddressDetails))

      when(mockRegistrationDetailsService.cacheAddress(any())(any[HeaderCarrier]())).thenReturn(Future.successful(true))
      setupMockSubscriptionFlowManager(ContactDetailsSubscriptionFlowPageMigrate)

      submitFormInCreateMode(createFormAllFieldsWhenUseRegAddressMap, journey = Journey.Migrate) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService).cachedAddressDetails(any[HeaderCarrier])
        verify(mockRegistrationDetailsService).cacheAddress(any[Address])(any[HeaderCarrier])
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(meq(createContactDetailsViewModelWhenUseRegAddress), meq(false))(any[HeaderCarrier])
      }
    }

    "save the details when user chooses not to use Registered Address for GYE journey" in {
      submitFormInCreateMode(createFormAllFieldsWhenNotUsingRegAddressMap) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(meq(createContactDetailsViewModelWhenNotUsingRegAddress), meq(false))(any[HeaderCarrier])
      }
    }

    "save the details when user chooses not to use Registered Address for Migrate journey" in {
      val cachedAddressDetails = Some(
        AddressViewModel(street = "Line 1 line 2", city = "line 3", postcode = Some("SW1A 2BQ"), countryCode = "GB")
      )
      when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(cachedAddressDetails))

      when(mockRegistrationDetailsService.cacheAddress(any())(any[HeaderCarrier]())).thenReturn(Future.successful(true))
      setupMockSubscriptionFlowManager(ContactDetailsSubscriptionFlowPageMigrate)

      submitFormInCreateMode(createFormAllFieldsWhenNotUsingRegAddressMap, journey = Journey.Migrate) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService)
          .cacheContactDetails(meq(createContactDetailsViewModelWhenNotUsingRegAddress), meq(false))(any[HeaderCarrier])
      }
    }

    "produce validation error when full name is not submitted" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap + (fullNameFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your contact name"
        page.getElementsText(fullNameFieldLevelErrorXPath) shouldBe "Enter your contact name"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when full name more than 70 characters" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap + (fullNameFieldName -> oversizedString(70))) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The full name can be a maximum of 70 characters"
        page.getElementsText(fullNameFieldLevelErrorXPath) shouldBe "The full name can be a maximum of 70 characters"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when Telephone is not submitted" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap + (telephoneFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your contact telephone number"
        page.getElementsText(telephoneFieldLevelErrorXPath) shouldBe "Enter your contact telephone number"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when Telephone more than 24 characters" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap + (telephoneFieldName -> oversizedString(24))) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The telephone number must be 24 digits or less"
        page.getElementsText(telephoneFieldLevelErrorXPath) shouldBe "The telephone number must be 24 digits or less"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when Telephone contains invalid characters" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap + (telephoneFieldName -> "$£")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Please enter a valid telephone number"
        page.getElementsText(telephoneFieldLevelErrorXPath) shouldBe "Please enter a valid telephone number"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "Allow when Telephone contains plus character" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap + (telephoneFieldName -> "+")) { result =>
        status(result) shouldBe SEE_OTHER
      }
    }

    "produce validation error when Fax more than 24 characters" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap + (faxFieldName -> oversizedString(24))) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The fax number must be 24 digits or less"
        page.getElementsText(faxFieldLevelErrorXPath) shouldBe "The fax number must be 24 digits or less"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when fax contains invalid characters" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap + (faxFieldName -> "$£")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Please enter a valid fax number"
        page.getElementsText(faxFieldLevelErrorXPath) shouldBe "Please enter a valid fax number"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "allow when fax contains plus character" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap + (faxFieldName -> "+")) { result =>
        status(result) shouldBe SEE_OTHER
      }
    }

    "produce validation error when Use registered address is not selected" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap - useRegisteredAddressFlagFieldName) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Tell us if the contact address is right"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "Select Yes when validation fails and use registered address flag Yes was submitted" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap - fullNameFieldName) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementAttribute(useRegisteredAddressYesRadioButtonXPath, "checked") shouldBe "checked"
        page.getElementAttribute(useRegisteredAddressNoRadioButtonXPath, "checked") shouldBe ""
      }
    }

    "Select No when validation fails and use registered address flag No was submitted" in {
      submitFormInCreateMode(createFormMandatoryFieldsWhenNotUsingRegAddressMap - fullNameFieldName) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementAttribute(useRegisteredAddressNoRadioButtonXPath, "checked") shouldBe "checked"
        page.getElementAttribute(useRegisteredAddressYesRadioButtonXPath, "checked") shouldBe ""
      }
    }

    "require Street when user does not want to use registered address as contact address" in {
      submitFormInCreateMode(createFormMandatoryFieldsWhenNotUsingRegAddressMap + (streetFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter the first line of your address"
        page.getElementsText(streetFieldLevelErrorXPath) shouldBe "Enter the first line of your address"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when Street more than 70 characters" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsWhenNotUsingRegAddressMap + (streetFieldName -> oversizedString(70))
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The street must be 70 characters or less"
        page.getElementsText(streetFieldLevelErrorXPath) shouldBe "The street must be 70 characters or less"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "require City when user does not want to use registered address as contact address" in {
      submitFormInCreateMode(createFormMandatoryFieldsWhenNotUsingRegAddressMap + (cityFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your town or city"
        page.getElementsText(cityFieldLevelErrorXPath) shouldBe "Enter your town or city"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when City more than 35 characters" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsWhenNotUsingRegAddressMap + (cityFieldName -> oversizedString(35))
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The town or city must be 35 characters or less"
        page.getElementsText(cityFieldLevelErrorXPath) shouldBe "The town or city must be 35 characters or less"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when Country is not selected" in {
      submitFormInCreateMode(createFormMandatoryFieldsWhenNotUsingRegAddressMap + (countryCodeFieldName -> "")) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter a valid country name"
          page.getElementsText(countryFieldLevelErrorXPath) shouldBe "Enter a valid country name"
          page.getElementsText("title") should startWith("Error: ")
      }
    }

    "require Postcode when user does not want to use registered address as contact address and Country is GB" in {
      submitFormInCreateMode(createFormMandatoryFieldsWhenNotUsingRegAddressMap + (countryCodeFieldName -> "GB")) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter a valid postcode"
          page.getElementsText(postcodeFieldLevelErrorXPath) shouldBe "Enter a valid postcode"
          page.getElementsText("title") should startWith("Error: ")
      }
    }

    "not require postcode when user does not want to use registered address as contact address and Country is not GB" in {
      submitFormInCreateMode(createFormMandatoryFieldsWhenNotUsingRegAddressMap + (countryCodeFieldName -> "FR"))(
        verifyRedirectToNextPageInCreateMode
      )
    }

    "produce validation error when postcode more than 9 characters and country is not GB" in {
      submitFormInCreateMode(
        createFormMandatoryFieldsWhenNotUsingRegAddressMap + (countryCodeFieldName -> "DE") + (postcodeFieldName -> oversizedString(
          9
        ))
      ) { result =>
        assertCustomPageLevelError(result, pageLevelErrorSummaryListXPath, "The postcode must be 9 characters or less")
        assertFieldLevelError(
          result,
          postcodeFieldLabel,
          postcodeFieldLevelErrorXPath,
          "The postcode must be 9 characters or less"
        )
      }
    }

    "display page level errors when nothing is entered" in {
      submitFormInCreateMode(createFormAllFieldsEmptyMap) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe
          "Enter your contact name " +
            "Enter your contact telephone number " +
            "Tell us if the contact address is right"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "fail when system fails to create contact details" in {
      val unsupportedException = new UnsupportedOperationException("Emulation of service call failure")

      registerSaveContactDetailsMockFailure(unsupportedException)

      val caught = intercept[RuntimeException] {
        submitFormInCreateMode(createFormMandatoryFieldsMap) { result =>
          await(result)
        }
      }
      caught shouldBe unsupportedException
    }

    "allow resubmission in create mode when details are invalid" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap - fullNameFieldName)(verifyFormActionInCreateMode)
    }

    "redirect to next page when details are valid" in {
      submitFormInCreateMode(createFormMandatoryFieldsMap)(verifyRedirectToNextPageInCreateMode)
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
    when(mockSubscriptionDetailsHolderService.cachedCustomsId(any[HeaderCarrier])).thenReturn(Future.successful(None))
    when(mockSubscriptionDetailsHolderService.cachedNameIdDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(None))
    val cachedAddressDetails = Some(
      AddressViewModel(street = "Line 1 line 2", city = "line 3", postcode = Some("SW1A 2BQ"), countryCode = "GB")
    )
    when(mockSubscriptionDetailsHolderService.cachedAddressDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(cachedAddressDetails))
  }

  private def mockFunctionWithRegistrationDetails(registrationDetails: RegistrationDetails) {
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier])).thenReturn(registrationDetails)
  }

  private def submitFormInCreateMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    journey: Journey.Value = Journey.GetYourEORI
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .submit(isInReviewMode = false, journey)(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  private def submitFormInReviewMode(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller.submit(isInReviewMode = true, Journey.GetYourEORI)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def showCreateForm(
    subscriptionFlow: SubscriptionFlow = OrganisationSubscriptionFlow,
    journey: Journey.Value = Journey.GetYourEORI,
    orgType: EtmpOrganisationType = CorporateBody
  )(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

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
        .cacheContactDetails(any[ContactDetailsModel], any[Boolean])(any[HeaderCarrier])
    ).thenReturn(Future.successful(()))
  }

  private def registerSaveContactDetailsMockFailure(exception: Throwable) {
    when(
      mockSubscriptionDetailsHolderService
        .cacheContactDetails(any[ContactDetailsModel], any[Boolean])(any[HeaderCarrier])
    ).thenReturn(Future.failed(exception))
  }
}
