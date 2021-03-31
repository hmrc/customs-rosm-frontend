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

import common.pages.SubscribeHowCanWeIdentifyYouPage
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfter
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.HowCanWeIdentifyYouController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType.SoleTraderId
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{
  AddressDetailsSubscriptionFlowPage,
  HowCanWeIdentifyYouSubscriptionFlowPage,
  SubscriptionFlowInfo
}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.how_can_we_identify_you
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HowCanWeIdentifyYouControllerSpec extends ControllerSpec with BeforeAndAfter {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSubscriptionBusinessService = mock[SubscriptionBusinessService]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockSubscriptionDetailsHolderService = mock[SubscriptionDetailsService]
  private val mockRequestSessionData = mock[RequestSessionData]

  private val howCanWeIdentifyYouView = app.injector.instanceOf[how_can_we_identify_you]

  private val controller = new HowCanWeIdentifyYouController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockSubscriptionFlowManager,
    mcc,
    howCanWeIdentifyYouView,
    mockSubscriptionDetailsHolderService
  )

  "Loading the page" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.createForm(Journey.Migrate))

    "show the form without errors" in {
      showForm(Map.empty) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe empty
      }
    }
  }

  "Submitting the form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Journey.Migrate)
    )

    "give a page level error when neither utr or nino are provided" in {
      submitForm(Map.empty) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Tell us how we can identify you"
      }
    }

    "only validate nino field when nino radio button is selected" in {
      submitForm(Map("nino" -> "TOOSHORT", "utr" -> "12345678901", "ninoOrUtrRadio" -> "nino")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "The National Insurance number must be 9 characters"
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorNino) shouldBe "The National Insurance number must be 9 characters"
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorUtr) shouldBe empty
      }
    }

    "only validate utr field when utr radio button is selected" in {
      submitForm(Map("nino" -> "TOOSHORT", "utr" -> "12345678901", "ninoOrUtrRadio" -> "utr")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "The Unique Taxpayer Reference number must be 10 numbers"
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorUtr) shouldBe "The Unique Taxpayer Reference number must be 10 numbers"
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorNino) shouldBe empty
      }
    }

    "give a page level error when neither radio button is selected and the nino and utr are provided" in {
      submitForm(Map("nino" -> "TOOSHORT", "utr" -> "12345678901", "ninoOrUtrRadio" -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Tell us how we can identify you"
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorUtr) shouldBe empty
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorNino) shouldBe empty
      }
    }

    "give a page and field level error when a nino of the wrong length is provided" in {
      submitForm(Map("nino" -> "TOOSHORT", "ninoOrUtrRadio" -> "nino")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "The National Insurance number must be 9 characters"
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorNino) shouldBe "The National Insurance number must be 9 characters"
      }
    }

    "give a page and field level error when an invalid nino is provided" in {
      submitForm(Map("nino" -> "123456789", "ninoOrUtrRadio" -> "nino")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a National Insurance number in the right format"
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorNino) shouldBe "Enter a National Insurance number in the right format"
      }
    }

    "give a page and field level error when an invalid utr is provided" in {
      submitForm(Map("utr" -> "ABCDE12345", "ninoOrUtrRadio" -> "utr")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a valid Unique Taxpayer Reference number"
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorUtr) shouldBe "Enter a valid Unique Taxpayer Reference number"
      }
    }

    "redirect to the 'Enter your business address' page when a valid nino is provided" in {
      when(mockSubscriptionDetailsHolderService.cacheCustomsId(any[CustomsId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(
        mockSubscriptionFlowManager.stepInformation(ArgumentMatchers.eq(HowCanWeIdentifyYouSubscriptionFlowPage))(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(SubscriptionFlowInfo(3, 5, AddressDetailsSubscriptionFlowPage))
      submitForm(Map("nino" -> "AB123456C", "ninoOrUtrRadio" -> "nino")) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") shouldBe "/customs/subscribe-for-cds/address"
      }
    }

    "redirect to the 'Enter your business address' page when a valid utr is provided" in {
      when(mockSubscriptionDetailsHolderService.cacheCustomsId(any[CustomsId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(
        mockSubscriptionFlowManager.stepInformation(ArgumentMatchers.eq(HowCanWeIdentifyYouSubscriptionFlowPage))(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(SubscriptionFlowInfo(3, 5, AddressDetailsSubscriptionFlowPage))
      submitForm(Map("utr" -> "1111111111", "ninoOrUtrRadio" -> "utr")) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") shouldBe "/customs/subscribe-for-cds/address"
      }
    }

    "throw an IllegalArgument exception when Nino or Utr are not provided in the request" in {
      val error = intercept[IllegalArgumentException] {
        submitForm(Map("utr" -> "1111111111111", "ninoOrUtrRadio" -> "noNinoUtr")) { result =>
          await(result)
        }
      }
      error.getMessage shouldBe "Expected only nino or utr to be populated but got: NinoOrUtr(None,None,Some(noNinoUtr))"
    }

    "redirect to 'Check your details' page when valid Nino/ Utr is provided" in {
      submitFormInReviewMode(Map("utr" -> "1111111111", "ninoOrUtrRadio" -> "utr")) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") shouldBe "/customs/subscribe-for-cds/matching/review-determine"
      }
    }

    "redirect to 'What information can we use to confirm your identity' page when Utr is needed to be changed" in {
      reviewForm(Map("utr" -> "1111111111", "ninoOrUtrRadio" -> "utr"), customsId = Utr("id")) { result => //TOOD change utr
        status(result) shouldBe OK
      }
    }

    "redirect to 'What information can we use to confirm your identity' page when Nino is needed to be changed" in {
      reviewForm(Map("nino" -> "SM1111111A", "ninoOrUtrRadio" -> "nino"), customsId = Nino("id")) { result =>
        status(result) shouldBe OK
      }
    }

    "throw an IllegalState exception when Nino or Utr are not provided in the request" in {

      val error = intercept[IllegalStateException] {
        reviewForm(Map("nino" -> "SM1111111A", "ninoOrUtrRadio" -> "nino"), customsId = SafeId("id")) { result =>
          await(result)
        }
      }
      error.getMessage shouldBe "Expected a Nino or UTR from the cached customs Id but got: SafeId(id)"
    }
  }

  def showForm(form: Map[String, String], userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller.createForm(Journey.Migrate).apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  def submitForm(form: Map[String, String], userId: String = defaultUserId, isInReviewMode: Boolean = false)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .submit(isInReviewMode, Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  def submitFormInReviewMode(form: Map[String, String], userId: String = defaultUserId, isInReviewMode: Boolean = true)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(CdsOrganisationType(SoleTraderId)))
    test(
      controller
        .submit(isInReviewMode, Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  def reviewForm(form: Map[String, String], userId: String = defaultUserId, customsId: CustomsId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(CdsOrganisationType(SoleTraderId)))
    when(mockSubscriptionBusinessService.getCachedCustomsId(any[HeaderCarrier]))
      .thenReturn(Future.successful(customsId))

    test(
      controller.reviewForm(Journey.Migrate).apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }
}
