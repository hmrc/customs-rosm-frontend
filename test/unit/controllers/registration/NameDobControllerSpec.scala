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

import common.pages.matching.NameDateOfBirthPage
import common.pages.matching.NameDateOfBirthPage._
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.NameDobController
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_namedob
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import unit.controllers.subscription.SubscriptionFlowTestSupport
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.matching.IndividualIdFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NameDobControllerSpec extends ControllerSpec with BeforeAndAfterEach with SubscriptionFlowTestSupport {
  protected override val formId: String = NameDateOfBirthPage.formId
  val mockCdsFrontendDataCache: SessionCache = mock[SessionCache]

  private val matchNameDobView = app.injector.instanceOf[match_namedob]

  private def nameDobController =
    new NameDobController(app, mockAuthConnector, mcc, matchNameDobView, mockCdsFrontendDataCache)

  private val emulatedFailure = new UnsupportedOperationException("Emulation of service call failure")

  val defaultOrganisationType = "individual"

  private val InvalidDateError = InvalidDate
  private val FirstNameError = thereIsAProblemWithThe("first name")
  private val LastNameError = thereIsAProblemWithThe("last name")
  private val DateOfBirthError = "Tell us your date of birth"

  def maxLengthError(maxLength: Int, field: String): String =
    s"The $field name must be $maxLength characters or less"

  override def beforeEach: Unit =
    when(mockCdsFrontendDataCache.saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))

  "Viewing the Individual Matching with ID form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      nameDobController.form(defaultOrganisationType, Journey.GetYourEORI)
    )

    "display the form" in {
      showForm("sole-trader") { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.getElementsHtml(pageLevelErrorSummaryListXPath) shouldBe empty
        page.getElementsText(firstName) shouldBe empty
        page.getElementsText(lastName) shouldBe empty
      }
    }
  }

  "first name" should {
    "be mandatory" in {
      submitForm(form = ValidRequest + ("first-name" -> ""), "individual") { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your first name"
        page.getElementsText(fieldLevelErrorFirstName) shouldBe "Enter your first name"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    s"be restricted to 35 characters" in {
      val firstNameMaxLength = 35
      submitForm(ValidRequest + ("first-name" -> oversizedString(firstNameMaxLength)), "individual") { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe maxLengthError(firstNameMaxLength, "first")
        page.getElementsText(fieldLevelErrorFirstName) shouldBe maxLengthError(firstNameMaxLength, "first")
        page.getElementsText("title") should startWith("Error: ")
      }
    }
  }

  "middle name" should {
    "not be shown" in {
      showForm("sole-trader") { result =>
        val page = CdsPage(bodyOf(result))
        page.elementIsPresent(middleName) shouldBe false
      }
    }
  }

  "last name" should {

    "be mandatory" in {
      submitForm(ValidRequest + ("last-name" -> ""), "individual") { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your last name"
        page.getElementsText(fieldLevelErrorLastName) shouldBe "Enter your last name"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "be restricted to 35 characters" in {
      val lastNameMaxLength = 35
      submitForm(ValidRequest + ("last-name" -> oversizedString(lastNameMaxLength)), "sole-trader") { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe maxLengthError(lastNameMaxLength, "last")
        page.getElementsText(fieldLevelErrorLastName) shouldBe maxLengthError(lastNameMaxLength, "last")
        page.getElementsText("title") should startWith("Error: ")
      }
    }
  }

  "date of birth" should {

    "be mandatory" in {
      submitForm(
        ValidRequest + ("date-of-birth.day" -> "", "date-of-birth.month" -> "", "date-of-birth.year" -> ""),
        "individual"
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your date of birth"
        page.getElementsText(fieldLevelErrorDateOfBirth) shouldBe "Enter your date of birth"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "be a valid date" in {
      submitForm(ValidRequest + ("date-of-birth.day" -> "32"), "individual") { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter a date of birth in the right format"
        page.getElementsText(fieldLevelErrorDateOfBirth) shouldBe "Enter a date of birth in the right format"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "not be in the future" in {
      val tomorrow = LocalDate.now().plusDays(1)
      submitForm(
        ValidRequest + ("date-of-birth.day" -> tomorrow.getDayOfMonth.toString,
        "date-of-birth.month" -> tomorrow.getMonthOfYear.toString,
        "date-of-birth.year" -> tomorrow.getYear.toString),
        "sole-trader"
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe FutureDate
        page.getElementsText(fieldLevelErrorDateOfBirth) shouldBe FutureDate
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "display an appropriate message when letters are entered instead of numbers" in {
      submitForm(
        ValidRequest + ("date-of-birth.day" -> "a", "date-of-birth.month" -> "b", "date-of-birth.year" -> "c"),
        "individual"
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter a date of birth in the right format"
        page.getElementsText(fieldLevelErrorDateOfBirth) shouldBe "Enter a date of birth in the right format"
        page.getElementsText("title") should startWith("Error: ")
      }
    }
  }

  "Submitting the individual form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      nameDobController.submit(defaultOrganisationType, Journey.GetYourEORI)
    )

    "be successful when all mandatory fields filled" in {
      submitForm(ValidRequest, "individual") { result =>
        status(result) shouldBe SEE_OTHER
      }
    }

    "redirect to the confirm page when successful" in {
      submitForm(ValidRequest, "individual") { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/register-for-cds/matching/chooseid/individual")
      }
    }
  }

  def showForm(organisationType: String, userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    val result =
      nameDobController.form("individual", Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  def submitForm(form: Map[String, String], organisationType: String, userId: String = defaultUserId)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)

    val result = nameDobController
      .submit(organisationType, Journey.GetYourEORI)
      .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))

    test(result)
  }
}
