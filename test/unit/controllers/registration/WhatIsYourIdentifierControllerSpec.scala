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
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.WhatIsYourIdentifierController
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Individual
import uk.gov.hmrc.customs.rosmfrontend.domain.{CdsOrganisationType, NameDobMatchModel, Nino, Utr}
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.ninoIdentityForm
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.{what_is_your_nino, what_is_your_utr}
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WhatIsYourIdentifierControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val whatIsYourNino = app.injector.instanceOf[what_is_your_nino]
  private val whatIsYourUtr = app.injector.instanceOf[what_is_your_utr]
  private val mockSessionCache = mock[SessionCache]
  private val mockMatchingService = mock[MatchingService]

  private val whatIsYourIdentifierController = new WhatIsYourIdentifierController(app, mockAuthConnector,mockMatchingService, mcc, whatIsYourNino,whatIsYourUtr, mockSessionCache)

  "WhatIsYourIdentifierController" should {
    when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
      Future.successful(Some(true))
    )

    "while Viewing the form " should {
      assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
        mockAuthConnector,
        whatIsYourIdentifierController.form(CdsOrganisationType.IndividualId, Journey.GetYourEORI)
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

    val utr = "1111111111"
    val nino = "AB123456C"

    when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
      Future.successful(Some(true))
    )
    when(mockSessionCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(
      Future.successful(
        SubscriptionDetails(nameDobDetails = Some(NameDobMatchModel("test", None, "user", LocalDate.now)))
      )
    )

    when(
      mockMatchingService
        .matchIndividualWithId(ArgumentMatchers.eq(Utr(utr)), any[Individual], any())(any[HeaderCarrier])
    ).thenReturn(Future.successful(true))

    when(
      mockMatchingService
        .matchIndividualWithId(ArgumentMatchers.eq(Nino(nino)), any[Individual], any())(any[HeaderCarrier])
    ).thenReturn(Future.successful(true))


    "redirect to 'What is your National insurance number' page when National insurance is selected " in {

      val ninoForm = Map("id" -> nino)
      submitForm(ninoForm) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("customs/register-for-cds/matching/confirm")
      }
    }

    "redirect to 'What is your Self Assessment Unique Taxpayer Reference' page when No is selected" in {
      val utrForm = Map("id" -> utr)
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(false))
      )
      submitForm(utrForm) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("customs/register-for-cds/matching/confirm")
      }
    }

    "redirect to 'What is your Self Assessment Unique Taxpayer Reference' page when No is selected Utr doesn't match in backend" in {
      val utrForm = Map("id" -> utr)
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(false))
      )
      when(
        mockMatchingService
          .matchIndividualWithId(ArgumentMatchers.eq(Utr(utr)), any[Individual], any())(any[HeaderCarrier])
      ).thenReturn(Future.successful(false))

      submitForm(utrForm) { result =>
        await(result)
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(RegisterHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Your details have not been found. Check that your details are correct and then try again."
      }
    }

    "redirect to 'What is your National insurance number' page when yes option is selected and nino doesn't match in backend" in {
      val ninoForm = Map("id" -> nino)
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(true))
      )
      when(
        mockMatchingService
          .matchIndividualWithId(ArgumentMatchers.eq(Nino(nino)), any[Individual], any())(any[HeaderCarrier])
      ).thenReturn(Future.successful(false))

      submitForm(ninoForm) { result =>
        await(result)
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(RegisterHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Your details have not been found. Check that your details are correct and then try again."
      }
    }

    "redirect to 'What is your National insurance number' page when yes option is selected " in {
      val ninoForm = Map("id" -> "")
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(true))
      )

      submitForm(ninoForm) { result =>
        await(result)
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(RegisterHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Enter your National Insurance number"
      }
    }

    "redirect to 'What is your Self Assessment Unique Taxpayer Reference' page when no option is selected " in {
      val utrForm = Map("id" -> "")
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(false))
      )

      submitForm(utrForm) { result =>
        await(result)
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(RegisterHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Enter your Unique Taxpayer Reference"
      }
    }

  }

  private def displayForm()(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      whatIsYourIdentifierController
        .form(CdsOrganisationType.IndividualId, Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def submitForm(form: Map[String, String])(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      whatIsYourIdentifierController
        .submit(CdsOrganisationType.IndividualId, Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, form))
    )
  }
}
