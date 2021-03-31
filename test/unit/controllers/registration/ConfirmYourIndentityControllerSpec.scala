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

import common.pages.RegisterHowCanWeIdentifyYouPage
import common.pages.matching.DoYouHaveNinoPage._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.{ConfirmYourIdentityController, IsleOfManController}
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.{confirm_your_identity, isle_of_man}
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConfirmYourIndentityControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val confirm_your_identity = app.injector.instanceOf[confirm_your_identity]
  private val mockSessionCache = mock[SessionCache]

  private val confirmYourIdentityController = new ConfirmYourIdentityController(app, mockAuthConnector, mcc, confirm_your_identity, mockSessionCache)

  "ConfirmYourIdentityController" should {
    when(mockSessionCache.saveHasNino(any[Boolean])(any[HeaderCarrier])).thenReturn(
      Future.successful(true)
    )

      "while Viewing the form " should {
        assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
          mockAuthConnector,
          confirmYourIdentityController.form(CdsOrganisationType.IndividualId, Journey.GetYourEORI)
        )
      }

    "display the form" in {
      displayForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe empty
        page.getElementsText(fieldLevelErrorNino) shouldBe empty
      }
    }

    "ensure the correct isle of man form" in {
      displayForm() { result =>
        status(result) shouldBe OK
      }
    }
  }

  "Submitting the form" should {
    "redirect to 'What is your National insurance number' page when National insurance is selected " in {
      val yesNoForm = Map("yes-no-answer" -> "true")
      submitForm(yesNoForm) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("customs/register-for-cds/matching/individual/confirm")
      }
    }

    "redirect to 'What is your Self Assessment Unique Taxpayer Reference' page when No is selected" in {
      val yesNoForm = Map("yes-no-answer" -> "false")
      submitForm(yesNoForm) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("customs/register-for-cds/matching/individual/confirm")
      }
    }

    "redirect to 'What is your National insurance number' page when no option is selected " in {
      val yesNoForm = Map("yes-no-answer" -> "")
      submitForm(yesNoForm) { result =>
        await(result)
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(RegisterHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Tell us how we can identify you"
      }
    }
  }

  private def displayForm()(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      confirmYourIdentityController
        .form(CdsOrganisationType.IndividualId, Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def submitForm(form: Map[String, String])(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      confirmYourIdentityController
        .submit(CdsOrganisationType.IndividualId, Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, form))
    )
  }
}
