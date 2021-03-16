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

import common.pages.subscription.VatRegisterUKPage
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers.{LOCATION, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.{SubscriptionFlowManager, VatRegisteredUkController}
import uk.gov.hmrc.customs.rosmfrontend.domain.YesNo
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{
  SubscriptionDetails,
  SubscriptionFlow,
  SubscriptionFlowInfo,
  SubscriptionPage
}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.vat_registered_uk
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.YesNoFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VatRegisteredUkSubscriptionControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockSubscriptionBusinessService = mock[SubscriptionBusinessService]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockSubscriptionFlow = mock[SubscriptionFlow]
  private val mockRequestSession = mock[RequestSessionData]
  private val vatRegisteredUkView = app.injector.instanceOf[vat_registered_uk]

  override def beforeEach: Unit = {
    reset(
      mockAuthConnector,
      mockSubscriptionFlowManager,
      mockSubscriptionBusinessService,
      mockSubscriptionDetailsService,
      mockSubscriptionFlow,
      mockRequestSession
    )
    when(mockSubscriptionDetailsService.cacheVatRegisteredUk(any[YesNo])(any[HeaderCarrier]))
      .thenReturn(Future.successful({}))
  }

  private val controller = new VatRegisteredUkController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsService,
    mockRequestSession,
    mcc,
    vatRegisteredUkView
  )

  "Vat registered Uk Controller" should {
    "return OK when accessing page through createForm method" in {
      createForm() { result =>
        status(result) shouldBe OK
      }
    }
    "land on a correct location" in {
      createForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.title should include(VatRegisterUKPage.title)
      }
    }
    "return ok when accessed from review method" in {
      reviewForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(VatRegisterUKPage.title)
      }
    }
  }

  "Submitting Vat registered UK Controller in create mode" should {
    "return to the same location with bad request" in {
      submitForm(invalidRequest) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }
    "redirect to add vat group page for yes answer" in {
      val url = "register-for-cds/vat-group"
      val sd = SubscriptionDetails(vatRegisteredUk = Some(true))
      subscriptionFlowUrl(url)

      submitForm(ValidRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-group")
      }
    }

    "redirect to eu vat page for no answer" in {
      val url = "register-for-cds/vat-registered-eu"
      val sd = SubscriptionDetails(vatRegisteredUk = Some(false))

      subscriptionFlowUrl(url)

      submitForm(validRequestNo) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-registered-eu")
      }
    }
    "redirect to vat groups review page for yes answer and is in review mode" in {
      val sd = SubscriptionDetails(vatRegisteredUk = Some(true))

      submitForm(ValidRequest, isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/what-are-your-uk-vat-details/review")
      }
    }

    "redirect to check answers page for no answer and is in review mode" in {
      val sd = SubscriptionDetails(vatRegisteredUk = Some(false))

      submitForm(validRequestNo, isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("customs/register-for-cds/matching/review-determine")
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
    when(mockSubscriptionBusinessService.getCachedVatRegisteredUk(any[HeaderCarrier])).thenReturn(true)
    test(controller.reviewForm(journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def submitForm(form: Map[String, String], isInReviewMode: Boolean = false)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    mockIsIndividual()
    test(
      controller
        .submit(isInReviewMode: Boolean, Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithFormValues(form))
    )
  }

  private def mockIsIndividual(isIndividual: Boolean = false) = {
    when(mockSubscriptionFlowManager.currentSubscriptionFlow(any[Request[AnyContent]])).thenReturn(mockSubscriptionFlow)
    when(mockSubscriptionFlow.isIndividualFlow).thenReturn(isIndividual)
  }

  private def subscriptionFlowUrl(url: String) = {
    val mockSubscriptionPage = mock[SubscriptionPage]
    val mockSubscriptionFlowInfo = mock[SubscriptionFlowInfo]
    when(mockSubscriptionFlowManager.stepInformation(any())(any[HeaderCarrier], any[Request[AnyContent]]))
      .thenReturn(mockSubscriptionFlowInfo)
    when(mockSubscriptionFlowInfo.nextPage).thenReturn(mockSubscriptionPage)
    when(mockSubscriptionPage.url).thenReturn(url)
  }

}
