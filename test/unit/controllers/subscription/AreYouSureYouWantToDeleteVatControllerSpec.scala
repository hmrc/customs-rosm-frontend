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

import common.pages.RemoveVatDetails
import common.pages.subscription.{SubscriptionCreateEUVatDetailsPage, VatDetailsEuConfirmPage}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.mvc.Result
import play.api.test.Helpers.{LOCATION, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.{
  AreYouSureYouWantToDeleteVatController,
  SubscriptionFlowManager
}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.VatEUDetailsModel
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionVatEUDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.are_you_sure_remove_vat
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.YesNoFormBuilder._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AreYouSureYouWantToDeleteVatControllerSpec extends ControllerSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSubscriptionVatEUDetailsService = mock[SubscriptionVatEUDetailsService]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]

  private val areYouSureRemoveVatView = app.injector.instanceOf[are_you_sure_remove_vat]

  val controller = new AreYouSureYouWantToDeleteVatController(
    app,
    mockAuthConnector,
    mockSubscriptionVatEUDetailsService,
    mcc,
    areYouSureRemoveVatView,
    mockSubscriptionFlowManager
  )
  private val testIndex = 12345
  private val someVatEuDetailsModel = VatEUDetailsModel("12334", "FR")

  private val emptyVatEuDetails: Seq[VatEUDetailsModel] = Seq.empty
  private val someVatEuDetails: Seq[VatEUDetailsModel] = Seq(VatEUDetailsModel("1234", "FR"))

  "Are you sure you want to delete these vat details page" should {
    "return ok and display correct form when passed index is correct" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      createForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(RemoveVatDetails.title)
      }
    }

    "redirect to confirm page in review mode when passed index was not found" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      reviewForm() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "/customs/register-for-cds/vat-details-eu-confirm/review"
      }
    }

    "redirect to confirm page in create mode when passed index was not found" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      createForm() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "/customs/register-for-cds/vat-details-eu-confirm"
      }
    }
  }

  "Submitting the form in create mode" should {
    "return bad request when no option selected" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      submit(invalidRequest) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }

    "redirect to vat eu registered page when answering yes and  no vat details found in cache" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      when(mockSubscriptionVatEUDetailsService.removeSingleEuVatDetails(any[VatEUDetailsModel])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(emptyVatEuDetails)
      submit(ValidRequest) { result =>
        status(result) shouldBe SEE_OTHER
        SubscriptionCreateEUVatDetailsPage.url should endWith(result.header.headers(LOCATION))
      }
    }

    "redirect to vat confirm page when answering yes and vat details found in cache" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      when(mockSubscriptionVatEUDetailsService.removeSingleEuVatDetails(any[VatEUDetailsModel])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(someVatEuDetails)
      submit(ValidRequest) { result =>
        status(result) shouldBe SEE_OTHER
        s"${VatDetailsEuConfirmPage.url}" should endWith(result.header.headers(LOCATION))
      }
    }

    "redirect to vat eu registered page when answering no and  no vat details found in cache" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(emptyVatEuDetails)
      submit(validRequestNo) { result =>
        status(result) shouldBe SEE_OTHER
        SubscriptionCreateEUVatDetailsPage.url should endWith(result.header.headers(LOCATION))
      }
    }

    "redirect to vat confirm page when answering no and vat details found in cache" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(someVatEuDetails)
      submit(validRequestNo) { result =>
        status(result) shouldBe SEE_OTHER
        s"${VatDetailsEuConfirmPage.url}" should endWith(result.header.headers(LOCATION))
      }
    }
  }

  "Submitting the form in review mode" should {
    "return bad request when no option selected" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      submit(invalidRequest, isInReviewMode = true) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }

    "redirect to vat eu registered page when answering yes and  no vat details found in cache" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      when(mockSubscriptionVatEUDetailsService.removeSingleEuVatDetails(any[VatEUDetailsModel])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(emptyVatEuDetails)
      submit(ValidRequest, isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("/customs/register-for-cds/vat-registered-eu/review")
      }
    }

    "redirect to vat confirm page when answering yes and vat details found in cache" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      when(mockSubscriptionVatEUDetailsService.removeSingleEuVatDetails(any[VatEUDetailsModel])(any[HeaderCarrier]))
        .thenReturn(Future.successful[Unit](()))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(someVatEuDetails)
      submit(ValidRequest, isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        s"${VatDetailsEuConfirmPage.url}/review" should endWith(result.header.headers(LOCATION))
      }
    }

    "redirect to vat eu registered page when answering no and  no vat details found in cache" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(emptyVatEuDetails)
      submit(validRequestNo, isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        s"${SubscriptionCreateEUVatDetailsPage.url}/review" should endWith(result.header.headers(LOCATION))
      }
    }

    "redirect to vat confirm page when answering no and vat details found in cache" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someVatEuDetailsModel)))
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier])).thenReturn(someVatEuDetails)
      submit(validRequestNo, isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        s"${VatDetailsEuConfirmPage.url}/review" should endWith(result.header.headers(LOCATION))
      }
    }
  }

  private def createForm()(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      controller
        .createForm(testIndex, journey = Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def reviewForm()(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      controller
        .reviewForm(testIndex, journey = Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def submit(form: Map[String, String], isInReviewMode: Boolean = false)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      controller
        .submit(testIndex, Journey.GetYourEORI, isInReviewMode: Boolean)
        .apply(SessionBuilder.buildRequestWithFormValues(form))
    )
  }
}
