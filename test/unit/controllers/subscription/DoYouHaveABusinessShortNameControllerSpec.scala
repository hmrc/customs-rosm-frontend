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

import common.pages.subscription.{ShortNamePage, SubscriptionAmendCompanyDetailsPage}
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.DoYouHaveABusinessShortNameController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{BusinessShortName, BusinessShortNameSubscriptionFlowYesNoPage}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.business_short_name_yes_no
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.SubscriptionAmendCompanyDetailsFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DoYouHaveABusinessShortNameControllerSpec
    extends SubscriptionFlowCreateModeTestSupport with BeforeAndAfterEach {

  protected override val formId: String = ShortNamePage.formId

  protected override def submitInCreateModeUrl: String =
    DoYouHaveABusinessShortNameController.submit(Journey.GetYourEORI).url

  private val mockOrgTypeLookup = mock[OrgTypeLookup]
  private val mockRequestSession = mock[RequestSessionData]
  private val businessShortName = app.injector.instanceOf[business_short_name_yes_no]

  val allFieldsMap = Map("use-short-name" -> withShortName, "short-name" -> ShortName)

  val allShortNameFieldsAsShortName = BusinessShortName(allShortNameFields.shortName)

  private val controller = new DoYouHaveABusinessShortNameController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockSubscriptionDetailsHolderService,
    mockSubscriptionFlowManager,
    mockRequestSession,
    mcc,
    businessShortName,
    mockOrgTypeLookup
  )

  private val emulatedFailure = new UnsupportedOperationException("Emulation of service call failure")
  private val useShortNameError = "Tell us if your organisation uses a shortened name"
  private val useShortNameWithError = "Error:Tell us if your organisation uses a shortened name"
  private val partnershipUseShortNameError = "Tell us if your partnership uses a shortened name"
  private val shortNameError = "Enter your organisation's shortened name"
  private val partnershipShortNameError = "Enter your partnership's shortened name"

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockSubscriptionFlowManager,
      mockOrgTypeLookup,
      mockSubscriptionDetailsHolderService
    )
    when(mockSubscriptionBusinessService.companyShortName(any[HeaderCarrier])).thenReturn(None)
    registerSaveDetailsMockSuccess()
    setupMockSubscriptionFlowManager(BusinessShortNameSubscriptionFlowYesNoPage)
  }

  "Displaying the form in create mode" should {
    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.GetYourEORI))

    "display title as 'Does your organisation use a shortened name?' for non partnership org type" in {
      showCreateForm(orgType = CorporateBody) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Does your organisation use a shortened name?")
      }
    }

    "display heading as 'Does your organisation use a shortened name?' for non partnership org type" in {
      showCreateForm(orgType = CorporateBody) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(ShortNamePage.headingXpath) shouldBe "Does your organisation use a shortened name?"
      }
    }

    "display title as 'Does your partnership use a shortened name?' for org type of Partnership" in {
      showCreateForm(orgType = Partnership) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Does your partnership use a shortened name?")
      }
    }

    "display heading as 'Does your partnership use a shortened name?' for org type of Partnership" in {
      showCreateForm(orgType = Partnership) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(ShortNamePage.headingXpath) shouldBe "Does your partnership use a shortened name?"
      }
    }

    "display title as 'Does your partnership use a shortened name?' for org type of Limited Liability Partnership" in {
      showCreateForm(orgType = LLP) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Does your partnership use a shortened name?")
      }
    }

    "display heading as 'Does your partnership use a shortened name?' for org type of Limited Liability Partnership" in {
      showCreateForm(orgType = LLP) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(ShortNamePage.headingXpath) shouldBe "Does your partnership use a shortened name?"
      }
    }

    "submit to correct url " in {
      showCreateForm()(verifyFormActionInCreateMode)
    }

    "display correct back link" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "display the correct text for the continue button" in {
      showCreateForm()({ result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(ShortNamePage.continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      })
    }

  }

  "page level error summary" should {

    "display errors in the same order as the fields appear on the page when 'use short name' is not answered" in {
      submitFormInCreateMode(emptyShortNameFieldsMap) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe useShortNameError
      }
    }

    "display partnership specific errors when 'use short name' is not answered" in {
      when(mockRequestSession.isPartnership(any())).thenReturn(true)
      submitFormInCreateMode(emptyShortNameFieldsMap) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe partnershipUseShortNameError
      }
    }
  }

  "'does your company use a shortened name' question" should {

    "be mandatory" in {
      when(mockRequestSession.isPartnership(any())).thenReturn(false)

      submitFormInCreateMode(emptyShortNameFieldsMap) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe useShortNameError
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.useShortNameFieldLevelErrorXpath) shouldBe useShortNameWithError
      }
    }

    val values = Table(
      ("value", "state", "response"),
      ("true", "valid", SEE_OTHER),
      ("false", "valid", SEE_OTHER),
      ("anything else", "invalid", BAD_REQUEST)
    )

    forAll(values) { (value, state, response) =>
      s"be $state when value is $value" in {
        submitFormInCreateMode(allFieldsMap + ("use-short-name" -> value)) { result =>
          status(result) shouldBe response
        }
      }
    }
  }

  "short name" should {

    "can be blank when 'does your company use a shortened name' is answered no" in {
      submitFormInCreateMode(allShortNameFieldsMap + ("use-short-name" -> withoutShortName, "short-name" -> "")) {
        result =>
          status(result) shouldBe SEE_OTHER
          val page = CdsPage(bodyOf(result))
          page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldEqual ""
          page.getElementsText(SubscriptionAmendCompanyDetailsPage.shortNameFieldLevelErrorXpath) shouldEqual ""
      }
    }

    "be mandatory when 'does your company use a shortened name' is answered yes" in {
      when(mockRequestSession.isPartnership(any())).thenReturn(false)

      submitFormInCreateMode(Map()) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe useShortNameError
      }
    }
  }

  private def submitFormInCreateMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(orgType))

    test(
      controller.submit(isInReviewMode = false, Journey.GetYourEORI)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def submitFormInReviewMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(orgType))

    test(
      controller.submit(isInReviewMode = true, Journey.GetYourEORI)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def registerSaveDetailsMockSuccess() {
    when(mockSubscriptionDetailsHolderService.cacheCompanyShortName(any[BusinessShortName])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
  }

  private def registerSaveDetailsMockFailure(exception: Throwable) {
    when(mockSubscriptionDetailsHolderService.cacheCompanyShortName(any[BusinessShortName])(any[HeaderCarrier]))
      .thenReturn(Future.failed(exception))
  }

  private def showCreateForm(
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody,
    journey: Journey.Value = Journey.GetYourEORI
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(orgType))

    test(controller.createForm(journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyShortNameFieldExistAndPopulatedCorrectly(page: CdsPage, testData: BusinessShortName): Unit =
    Some(page.getElementValueForLabel(SubscriptionAmendCompanyDetailsPage.shortNameLabelXpath)) shouldBe testData.shortName

  private def verifyShortNameFieldExistWithNoData(page: CdsPage): Unit =
    page.getElementValueForLabel(ShortNamePage.shortNameLabelXpath) shouldBe empty
}
