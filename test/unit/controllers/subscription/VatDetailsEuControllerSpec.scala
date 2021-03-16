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

import common.pages.{SubscriptionEUVATDetailsPage, VatDetailsEuPage}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.Checkers
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.{SubscriptionFlowManager, VatDetailsEuController}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.VatEUDetailsModel
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.countries.Countries
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionVatEUDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.vat_details_eu
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder._
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VatDetailsEuControllerSpec extends ControllerSpec with Checkers with BeforeAndAfterEach {

  private val mockCountries = mock[Countries]
  private val mockAuthConnector = mock[AuthConnector]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockSubscriptionVatEUDetailsService = mock[SubscriptionVatEUDetailsService]

  private val vatDetailsEuView = app.injector.instanceOf[vat_details_eu]

  private val controller = new VatDetailsEuController(
    app,
    mockAuthConnector,
    mockSubscriptionVatEUDetailsService,
    mockSubscriptionFlowManager,
    mcc,
    vatDetailsEuView,
    mockCountries
  )

  val countries = new Countries(app)

  private val duplicateVatNumber = "You have already entered this VAT number. Enter a different VAT number"
  private val fieldDuplicateVatNumber = "Error: You have already entered this VAT number. Enter a different VAT number"

  private val validVatIdMap = Map("vatCountry" -> "FR", "vatNumber" -> "XX123456789")

  private val vatEuDetailsOnLimit: Seq[VatEUDetailsModel] = Seq(
    VatEUDetailsModel("FR", "12345"),
    VatEUDetailsModel("DE", "22222"),
    VatEUDetailsModel("ES", "33333"),
    VatEUDetailsModel("DK", "44444"),
    VatEUDetailsModel("PL", "55555")
  )

  private val vatEuDetails: Seq[VatEUDetailsModel] = List(VatEUDetailsModel("FR", "12345"))

  override def beforeEach: Unit = {
    reset(mockSubscriptionVatEUDetailsService)
    when(mockCountries.eu).thenReturn(countries.eu)
  }

  "VatDetailEuController show createForm" should {
    "return ok and display correct form when accessing with vatDetails under limit in the cache" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetails))
      createForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(VatDetailsEuPage.title)
      }
    }

    "return 303 and display correct form when accessing with vatDetails under limit in the cache in create mode" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetailsOnLimit))
      createForm() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu-confirm")
      }
    }

    "return 303 and display correct form when accessing with vatDetails under limit in the cache in review mode" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetailsOnLimit))
      reviewForm() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu-confirm/review")
      }
    }
  }

  "Submitting VatDetailEuController" should {
    "fail validation when vat number already exists in cache" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetailsOnLimit))

      submit(validVatIdMap + ("vatNumber" -> "12345")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionEUVATDetailsPage.pageLevelErrorSummaryListXPath) shouldBe duplicateVatNumber
        page.getElementsText(SubscriptionEUVATDetailsPage.fieldLevelErrorEUVATNumberInput) shouldBe fieldDuplicateVatNumber
      }
    }

    "redirect to confirm vat page when form is valid in create mode" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetails))
      when(mockSubscriptionVatEUDetailsService.saveOrUpdate(any[VatEUDetailsModel])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      submit(validVatIdMap + ("vatNumber" -> "AAAA1234")) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu-confirm")
      }
    }

    "redirect to confirm vat page when form is valid in review mode" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetails))
      when(mockSubscriptionVatEUDetailsService.saveOrUpdate(any[VatEUDetailsModel])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      submit(validVatIdMap + ("vatNumber" -> "AAAA1234"), isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu-confirm/review")
      }
    }
  }

  "Submitting update on VatDetailEuController" should {
    "fail validation when updated value of vat number already exists in cache" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetailsOnLimit))
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(VatEUDetailsModel("DE", "22222"))))

      submitUpdate(validVatIdMap + ("vatNumber" -> "12345"), index = 12345) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionEUVATDetailsPage.pageLevelErrorSummaryListXPath) shouldBe duplicateVatNumber
        page.getElementsText(SubscriptionEUVATDetailsPage.fieldLevelErrorEUVATNumberInput) shouldBe fieldDuplicateVatNumber
      }
    }

    "pass validation when updated value of vat number already exists in cache but is the same as the value it is replacing" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetailsOnLimit))
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(VatEUDetailsModel("FR", "12345"))))
      when(
        mockSubscriptionVatEUDetailsService
          .updateVatEuDetailsModel(any[VatEUDetailsModel], any[VatEUDetailsModel])(any[HeaderCarrier])
      ).thenReturn(Future.successful(Seq(VatEUDetailsModel("12345", "FR"))))
      when(mockSubscriptionVatEUDetailsService.saveOrUpdate(any[Seq[VatEUDetailsModel]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

      submitUpdate(validVatIdMap + ("vatNumber" -> "12345"), index = 12345) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu-confirm")
      }
    }

    "fail validation when passed vat details with given index were not found" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetailsOnLimit))
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      intercept[IllegalStateException] {
        await(submitUpdate(validVatIdMap + ("vatNumber" -> "12345"), index = 12345) { result =>
          status(result)
        })
      }.getMessage shouldBe "Vat for update not found"
    }

    "update details and redirect to eu vat confirm page in create mode" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetails))
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(VatEUDetailsModel("DK", "54321"))))

      when(
        mockSubscriptionVatEUDetailsService
          .updateVatEuDetailsModel(any[VatEUDetailsModel], any[VatEUDetailsModel])(any[HeaderCarrier])
      ).thenReturn(Future.successful(vatEuDetailsOnLimit))
      when(mockSubscriptionVatEUDetailsService.saveOrUpdate(any[Seq[VatEUDetailsModel]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

      submitUpdate(validVatIdMap + ("vatNumber" -> "AAAA1234"), index = 12345) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu-confirm")
      }

      val requestCaptor1: ArgumentCaptor[VatEUDetailsModel] = ArgumentCaptor.forClass(classOf[VatEUDetailsModel])
      val requestCaptor2: ArgumentCaptor[VatEUDetailsModel] = ArgumentCaptor.forClass(classOf[VatEUDetailsModel])

      verify(mockSubscriptionVatEUDetailsService).updateVatEuDetailsModel(
        requestCaptor1.capture(),
        requestCaptor2.capture()
      )(ArgumentMatchers.any[HeaderCarrier])
      requestCaptor1.getValue should equal(VatEUDetailsModel("DK", "54321"))
      requestCaptor2.getValue should equal(VatEUDetailsModel("FR", "AAAA1234"))
    }

    "update details and redirect to eu vat confirm page in review mode" in {
      when(mockSubscriptionVatEUDetailsService.cachedEUVatDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(vatEuDetails))
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(VatEUDetailsModel("DK", "54321"))))

      when(
        mockSubscriptionVatEUDetailsService
          .updateVatEuDetailsModel(any[VatEUDetailsModel], any[VatEUDetailsModel])(any[HeaderCarrier])
      ).thenReturn(Future.successful(vatEuDetailsOnLimit))
      when(mockSubscriptionVatEUDetailsService.saveOrUpdate(any[Seq[VatEUDetailsModel]])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

      submitUpdate(validVatIdMap + ("vatNumber" -> "AAAA1234"), index = 12345, isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu-confirm/review")
      }

      val requestCaptor1: ArgumentCaptor[VatEUDetailsModel] = ArgumentCaptor.forClass(classOf[VatEUDetailsModel])
      val requestCaptor2: ArgumentCaptor[VatEUDetailsModel] = ArgumentCaptor.forClass(classOf[VatEUDetailsModel])

      verify(mockSubscriptionVatEUDetailsService).updateVatEuDetailsModel(
        requestCaptor1.capture(),
        requestCaptor2.capture()
      )(ArgumentMatchers.any[HeaderCarrier])
      requestCaptor1.getValue should equal(VatEUDetailsModel("DK", "54321"))
      requestCaptor2.getValue should equal(VatEUDetailsModel("FR", "AAAA1234"))
    }
  }

  "Updating Form through VatDetailEuController" should {
    "redirect to confirm vat page when vat detals of given index was not found" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      updateForm(index = 12345) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) should endWith("register-for-cds/vat-details-eu-confirm")
      }
    }

    "display vat details page when details for given index were not found" in {
      when(mockSubscriptionVatEUDetailsService.vatEuDetails(any[Int])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(VatEUDetailsModel("54321", "DK"))))
      updateForm(index = 12345) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(VatDetailsEuPage.title)
      }
    }
  }

  private def createForm()(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(test(controller.createForm(Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

  private def reviewForm()(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(test(controller.reviewForm(Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

  private def submit(form: Map[String, String], isInReviewMode: Boolean = false)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(
      test(
        controller
          .submit(Journey.GetYourEORI, isInReviewMode: Boolean)
          .apply(SessionBuilder.buildRequestWithFormValues(form))
      )
    )
  }

  private def submitUpdate(form: Map[String, String], isInReviewMode: Boolean = false, index: Int)(
    test: Future[Result] => Any
  ) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(
      test(
        controller
          .submitUpdate(index, Journey.GetYourEORI, isInReviewMode: Boolean)
          .apply(SessionBuilder.buildRequestWithFormValues(form))
      )
    )
  }

  private def updateForm(index: Int)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(
      test(
        controller.updateForm(index, Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(defaultUserId))
      )
    )
  }

  private def reviewUpdateForm(index: Int)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(
      test(
        controller
          .reviewUpdateForm(index, Journey.GetYourEORI)
          .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
      )
    )
  }
}
