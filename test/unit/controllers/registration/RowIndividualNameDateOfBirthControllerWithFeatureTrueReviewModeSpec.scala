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

import common.pages.matching.{IndividualNameAndDateOfBirthPage, ThirdCountryIndividualNameAndDateOfBirthPage, ThirdCountrySoleTraderNameAndDateOfBirthPage}
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalacheck.Prop
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.Checkers
import play.api.Application
import play.api.data.Form
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.RowIndividualNameDateOfBirthController
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.RowIndividualNameDateOfBirthController
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.row_individual_name_dob
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.scalacheck.TestDataGenerators

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RowIndividualNameDateOfBirthControllerWithFeatureTrueReviewModeSpec
    extends ControllerSpec with Checkers with TestDataGenerators with BeforeAndAfterEach {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder().configure("features.rowHaveUtrEnabled" -> true).build()

  class ControllerFixture(organisationType: String, form: Form[IndividualNameAndDateOfBirth])
      extends AbstractControllerFixture[RowIndividualNameDateOfBirthController] {
    val mockSessionCache: SessionCache = mock[SessionCache]
    val mockRequestSessionData = mock[RequestSessionData]
    val mockRegistrationInfo = mock[IndividualRegistrationInfo]
    val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]

    private val rowIndividualNameDob = app.injector.instanceOf[row_individual_name_dob]

    override val controller = new RowIndividualNameDateOfBirthController(
      app,
      mockAuthConnector,
      mockSessionCache,
      mockSubscriptionDetailsService,
      mcc,
      rowIndividualNameDob,
      mockRequestSessionData,
      appConfig
    )

    protected def show(с: RowIndividualNameDateOfBirthController): Action[AnyContent] =
      с.reviewForm(organisationType, Journey.GetYourEORI)

    protected def submit(c: RowIndividualNameDateOfBirthController): Action[AnyContent] =
      c.submit(true, organisationType, Journey.GetYourEORI)

    def formData(thirdCountryIndividual: IndividualNameAndDateOfBirth): Map[String, String] =
      form.mapping.unbind(thirdCountryIndividual)
  }

  abstract class IndividualNameAndDateOfBirthBehaviour(
    webPage: IndividualNameAndDateOfBirthPage,
    form: Form[IndividualNameAndDateOfBirth],
    val validFormModelGens: IndividualGens[LocalDate]
  ) {

    protected val organisationType: String = webPage.organisationType

    "loading the page in review mode" should {

      withControllerFixture { controllerFixture =>
        assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
          controllerFixture.mockAuthConnector,
          controllerFixture.controller.reviewForm(organisationType, Journey.GetYourEORI)
        )
      }

      "show the form in review mode without errors, the input fields are prepopulated from the cache" in withControllerFixture {
        controllerFixture =>
          import controllerFixture._
          when(mockSubscriptionDetailsService.cachedNameDobDetails(any[HeaderCarrier]))
            .thenReturn(
              Future.successful(
                Some(NameDobMatchModel("firstName", Some("middleName"), "lastName", new LocalDate(1980, 3, 31)))
              )
            )

          controllerFixture.showForm { result =>
            status(result) shouldBe OK
            val page = CdsPage(bodyOf(result))
            page.getElementsText(webPage.pageLevelErrorSummaryListXPath) shouldBe empty

            val assertPresentOnPage = controllerFixture.assertPresentOnPage(page) _

            assertPresentOnPage(webPage.givenNameElement)
            assertPresentOnPage(webPage.middleNameElement)
            assertPresentOnPage(webPage.familyNameElement)
            assertPresentOnPage(webPage.dateOfBirthElement)
            page.getElementAttributeAction(webPage.formElement) shouldBe RowIndividualNameDateOfBirthController
              .reviewForm(organisationType, Journey.GetYourEORI)
              .url

            page.getElementValue(webPage.givenNameElement) shouldBe "firstName"
            page.getElementValue(webPage.middleNameElement) shouldBe "middleName"
            page.getElementValue(webPage.familyNameElement) shouldBe "lastName"
            page.getElementValue(webPage.dobDayElement) shouldBe "31"
            page.getElementValue(webPage.dobMonthElement) shouldBe "3"
            page.getElementValue(webPage.dobYearElement) shouldBe "1980"
          }
      }

      "should redirect to sign out page if cachedNameDobDetails not found" in withControllerFixture {
        controllerFixture =>
          import controllerFixture._
          when(mockSubscriptionDetailsService.cachedNameDobDetails(any[HeaderCarrier])).thenReturn(None)

          controllerFixture.showForm { result =>
            status(result) shouldBe SEE_OTHER
            result.header.headers(LOCATION) shouldBe "/customs/register-for-cds/sign-out"
          }
      }
    }

    "submitting a valid form when review mode is true" should {

      withControllerFixture { controllerFixture =>
        assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
          controllerFixture.mockAuthConnector,
          controllerFixture.controller.submit(true, organisationType, Journey.GetYourEORI)
        )
      }

      "redirect to 'review' page" in testControllerWithModel(validFormModelGens) {
        (controllerFixture, individualNameAndDateOfBirth) =>
          import controllerFixture._
          when(mockSubscriptionDetailsService.cacheNameDobDetails(any[NameDobMatchModel])(any[HeaderCarrier]))
            .thenReturn(Future.successful(()))

          submitForm(formData(individualNameAndDateOfBirth)) { result =>
            CdsPage(contentAsString(result)).getElementsHtml(webPage.pageLevelErrorSummaryListXPath) shouldBe empty
            status(result) shouldBe SEE_OTHER
            result.header.headers(LOCATION) shouldBe "/customs/register-for-cds/matching/review-determine"
            verify(mockSubscriptionDetailsService).cacheNameDobDetails(any())(any())
          }
      }
    }

    protected def withControllerFixture[TestResult](body: ControllerFixture => TestResult): TestResult =
      body(new ControllerFixture(organisationType, form))

    protected def testControllerWithModel(
      formModelGens: IndividualGens[LocalDate]
    )(test: (ControllerFixture, IndividualNameAndDateOfBirth) => Unit): Unit =
      check(Prop.forAllNoShrink(individualNameAndDateOfBirthGenerator(formModelGens))({ individualNameAndDateOfBirth =>
        withControllerFixture { controllerFixture =>
          test.apply(controllerFixture, individualNameAndDateOfBirth)
          Prop.proved
        }
      }))
  }

  abstract class ThirdCountryIndividualBehaviour(webPage: IndividualNameAndDateOfBirthPage)
      extends IndividualNameAndDateOfBirthBehaviour(
        webPage,
        form = MatchingForms.thirdCountryIndividualNameDateOfBirthForm,
        validFormModelGens = individualNameAndDateOfBirthGens()
      ) {}

  case object ThirdCountrySoleTraderBehavior
      extends ThirdCountryIndividualBehaviour(ThirdCountrySoleTraderNameAndDateOfBirthPage)

  case object ThirdCountryIndividualBehavior
      extends ThirdCountryIndividualBehaviour(ThirdCountryIndividualNameAndDateOfBirthPage)

  "The third country sole trader case" when (behave like ThirdCountrySoleTraderBehavior)

  "The third country individual case" when (behave like ThirdCountryIndividualBehavior)
}
