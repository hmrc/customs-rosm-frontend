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

package unit.controllers.registration

import common.pages.matching.OrganisationUtrPage._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.connector.MatchingServiceConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.DoYouHaveAUtrNumberYesNoController
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching.{MatchingRequestHolder, MatchingResponse}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_organisation_utr_yes_no
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.matching.OrganisationUtrFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DoYouHaveAUtrNumberYesNoControllerSpec extends ControllerSpec with MockitoSugar with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockMatchingService = mock[MatchingService]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val matchOrganisationUtrView = app.injector.instanceOf[match_organisation_utr_yes_no]

  private val controller = new DoYouHaveAUtrNumberYesNoController(
    app,
    mockAuthConnector,
    mockMatchingService,
    mcc,
    matchOrganisationUtrView,
    mockSubscriptionDetailsService
  )

  "Viewing the Utr Organisation Matching form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.form(CdsOrganisationType.CharityPublicBodyNotForProfitId, Journey.GetYourEORI)
    )

    "display the form" in {
      showForm(CdsOrganisationType.CharityPublicBodyNotForProfitId) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe empty
        page.getElementsText(fieldLevelErrorUtr) shouldBe empty

      }
    }
  }

  "Submitting the form for Organisation Types that have a UTR" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(CdsOrganisationType.CharityPublicBodyNotForProfitId, Journey.GetYourEORI)
    )
  }

  "submitting the form for a charity without a utr" should {

    "direct the user to the Are You VAT Registered in the UK? page" in {
      submitForm(NoUtrRequest, CdsOrganisationType.CharityPublicBodyNotForProfitId) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/register-for-cds/are-you-vat-registered-in-uk")
      }
    }
  }

  "display the form for ROW organisation" should {

    "when ThirdCountryOrganisationId is passed" in {
      showForm(CdsOrganisationType.ThirdCountryOrganisationId) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith(
          "Does your organisation have a Corporation Tax Unique Taxpayer Reference (UTR)?"
        )
        page.h1 shouldBe "Does your organisation have a Corporation Tax Unique Taxpayer Reference (UTR)?"

        page.getElementsText("//*[@class='form-hint']") shouldBe "Your organisation will have a Corporation Tax UTR number if it pays corporation tax. It is on tax returns and other letters from HMRC."
      }
    }
  }

  "submitting the form for ROW organisation" should {
    "redirect to enter UTR page based on YES answer" in {
      submitForm(form = YesUtrYesNoRequest, CdsOrganisationType.ThirdCountryOrganisationId) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/register-for-cds/matching/utr/third-country-organisation")
      }
    }

    "redirect to Confirm Details page based on NO answer" in {
      submitForm(form = NoUtrRequest, CdsOrganisationType.ThirdCountryOrganisationId) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith(
          s"register-for-cds/matching/address/${CdsOrganisationType.ThirdCountryOrganisationId}"
        )
      }
    }

    "redirect to Review page while on review mode" in {
      submitForm(form = NoUtrRequest, CdsOrganisationType.ThirdCountryOrganisationId, isInReviewMode = true) { result =>
        status(await(result)) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("register-for-cds/matching/review-determine")
      }
    }
  }

  "display the form for ROW" should {
    "contain a proper content for sole traders" in {
      showForm(CdsOrganisationType.ThirdCountrySoleTraderId, defaultUserId) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith(
          "Do you have a Self Assessment Unique Taxpayer Reference issued in the UK?"
        )
        page.h1 shouldBe "Do you have a Self Assessment Unique Taxpayer Reference issued in the UK?"
        page.getElementsText("//*[@class='form-hint']") shouldBe "You will have a Self Assessment Unique Taxpayer Reference if you registered for Self Assessment in the UK."
      }
    }
    "contain a proper content for individuals" in {
      showForm(CdsOrganisationType.ThirdCountryIndividualId, defaultUserId) { result =>
        val page = CdsPage(bodyOf(result))
        page.title should startWith(
          "Do you have a Self Assessment Unique Taxpayer Reference issued in the UK?"
        )
        page.h1 shouldBe "Do you have a Self Assessment Unique Taxpayer Reference issued in the UK?"
        page.getElementsText("//*[@class='form-hint']") shouldBe "You will have a Self Assessment Unique Taxpayer Reference if you registered for Self Assessment in the UK."
      }
    }
  }

  "submitting the form for ROW" should {
    "redirect to Confirm Details page based on YES answer and organisation type sole trader" in {
      submitForm(form = YesUtrYesNoRequest, CdsOrganisationType.ThirdCountrySoleTraderId) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/register-for-cds/matching/utr/third-country-sole-trader")
      }
    }

    "redirect to Nino page based on NO answer" in {
      submitForm(form = NoUtrRequest, CdsOrganisationType.ThirdCountrySoleTraderId) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("register-for-cds/matching/row/nino")
      }
    }
  }

  def showForm(organisationType: String, userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    val result =
      controller.form(organisationType, Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  def submitForm(
    form: Map[String, String],
    organisationType: String,
    userId: String = defaultUserId,
    isInReviewMode: Boolean = false
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller
      .submit(organisationType, Journey.GetYourEORI, isInReviewMode)
      .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    test(result)
  }
}
