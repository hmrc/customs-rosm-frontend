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

import common.pages.GYEEUVATNumber
import common.pages.subscription.{DisclosePersonalDetailsConsentPage, SoleTraderEuVatDetailsPage}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers.{LOCATION, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.{SubscriptionFlowManager, VatRegisteredEuController}
import uk.gov.hmrc.customs.rosmfrontend.domain.YesNo
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{SubscriptionFlow, SubscriptionFlowInfo, SubscriptionPage}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.VatEUDetailsModel
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService,
  SubscriptionVatEUDetailsService
}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.vat_registered_eu
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.YesNoFormBuilder._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class VatRegisteredEuControllerSpec extends ControllerSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockSubscriptionVatEUDetailsService = mock[SubscriptionVatEUDetailsService]
  private val mockSubscriptionBusinessService = mock[SubscriptionBusinessService]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockSubscriptionFlow = mock[SubscriptionFlow]
  private val mockSubscriptionFlowInfo = mock[SubscriptionFlowInfo]
  private val mockSessionCache = mock[SessionCache]
  private val mockSubscriptionPage = mock[SubscriptionPage]
  private val mockRequestSession = mock[RequestSessionData]
  private val vatRegisteredEuView = app.injector.instanceOf[vat_registered_eu]

  private val emptyVatEuDetails: Seq[VatEUDetailsModel] = Seq.empty
  private val someVatEuDetails: Seq[VatEUDetailsModel] = Seq(VatEUDetailsModel("1234", "FR"))

  private val controller = new VatRegisteredEuController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockSubscriptionDetailsService,
    mockSubscriptionVatEUDetailsService,
    mockRequestSession,
    mcc,
    vatRegisteredEuView,
    mockSubscriptionFlowManager
  )

  "Vat registered Eu Controller" should {
    "return OK when accessing page through createForm method" in {
      createForm() { result =>
        status(result) shouldBe OK
      }
    }
    "land on a correct location" in {
      createForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.title should include(SoleTraderEuVatDetailsPage.title)
      }
    }
    "return ok when accessed from review method" in {
      reviewForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(SoleTraderEuVatDetailsPage.title)
      }
    }
  }

  "Submitting Vat registered Eu Controller in create mode" should {
    "return to the same location with bad request" in {
      submitForm(invalidRequest) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }
    "redirect to add vat details page for yes answer and no vat details in the cache" in {
      when(mockSubscriptionDetailsService.cacheVatRegisteredEu(any[YesNo])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(emptyVatEuDetails)
      when(mockSubscriptionFlowManager.stepInformation(any())(any[HeaderCarrier], any[Request[AnyContent]]))
        .thenReturn(mockSubscriptionFlowInfo)
      when(mockSubscriptionFlowInfo.nextPage).thenReturn(mockSubscriptionPage)
      when(mockSubscriptionPage.url).thenReturn(GYEEUVATNumber.url)
      submitForm(ValidRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu")
      }
    }

    "redirect to vat confirm page when vat details found in the cache" in {
      when(mockSubscriptionDetailsService.cacheVatRegisteredEu(any[YesNo])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(someVatEuDetails)
      submitForm(ValidRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu-confirm")
      }
    }

    "redirect to disclose page for no answer" in {
      when(mockSubscriptionDetailsService.cacheVatRegisteredEu(any[YesNo])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(emptyVatEuDetails)
      when(
        mockSubscriptionFlowManager.stepInformation(any())(any[HeaderCarrier], any[Request[AnyContent]]).nextPage.url
      ).thenReturn(DisclosePersonalDetailsConsentPage.url)
      submitForm(ValidRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("/register-for-cds/disclose-personal-details-consent")
      }
    }
  }

  "Submitting Vat registered Eu Controller in review mode" should {
    "redirect to add vat details page for yes answer and none vat details in the cache" in {
      when(mockSubscriptionDetailsService.cacheVatRegisteredEu(any[YesNo])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(emptyVatEuDetails)
      submitForm(ValidRequest, isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu/review")
      }
    }

    "redirect to add vat confirm page for yes answer and some vat details in the cache" in {
      when(mockSubscriptionDetailsService.cacheVatRegisteredEu(any[YesNo])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(someVatEuDetails)
      submitForm(ValidRequest, isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu-confirm/review")
      }
    }

    "redirect to review determine controller for no answer" in {
      when(mockSubscriptionDetailsService.cacheVatRegisteredEu(any[YesNo])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      when(mockSubscriptionVatEUDetailsService.saveOrUpdate(any[Seq[VatEUDetailsModel]])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      submitForm(validRequestNo, isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("/register-for-cds/matching/review-determine")
      }
    }
  }

  private def createForm(journey: Journey.Value = Journey.GetYourEORI)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    mockIsIndividual()
    test(controller.createForm(journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def reviewForm(journey: Journey.Value = Journey.GetYourEORI)(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    mockIsIndividual()
    when(mockSessionCache.subscriptionDetails).thenReturn(any)
    when(mockSubscriptionBusinessService.getCachedVatRegisteredEu).thenReturn(true)
    test(controller.reviewForm(journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def submitForm(form: Map[String, String], isInReviewMode: Boolean = false)(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    mockIsIndividual()
    test(
      controller
        .submit(isInReviewMode: Boolean, Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithFormValues(form))
    )
  }

  private def mockIsIndividual() = {
    when(mockSubscriptionFlowManager.currentSubscriptionFlow(any[Request[AnyContent]])).thenReturn(mockSubscriptionFlow)
    when(mockSubscriptionFlow.isIndividualFlow).thenReturn(true)
  }
}
