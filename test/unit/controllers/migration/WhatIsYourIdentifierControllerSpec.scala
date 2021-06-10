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

package unit.controllers.migration

import common.pages.RegisterHowCanWeIdentifyYouPage
import common.pages.matching.DoYouHaveNinoPage._
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.WhatIsYourIdentifierController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Individual
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{AddressDetailsSubscriptionFlowPage, SubscriptionDetails, SubscriptionFlowInfo, WhatIsYourIdentifierControllerFlowPage}
import uk.gov.hmrc.customs.rosmfrontend.domain.{CustomsId, Nino, Utr}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.{what_is_your_nino, what_is_your_utr}
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
  private val mockSubscriptionBusinessService = mock[SubscriptionBusinessService]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockSubscriptionDetailsHolderService = mock[SubscriptionDetailsService]

  private val whatIsYourIdentifierController = new WhatIsYourIdentifierController(app, mockAuthConnector, mcc, whatIsYourNino,whatIsYourUtr, mockSessionCache, mockSubscriptionFlowManager, mockSubscriptionBusinessService, mockSubscriptionDetailsHolderService)


  override def beforeEach: Unit = {
    when(mockSubscriptionDetailsHolderService.cacheCustomsId(any[CustomsId])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
    when(mockSubscriptionBusinessService.getCachedCustomsId(any[HeaderCarrier]))
      .thenReturn(Future.successful(None))
    when(
      mockSubscriptionFlowManager.stepInformation(ArgumentMatchers.eq(WhatIsYourIdentifierControllerFlowPage))(
        any[HeaderCarrier],
        any[Request[AnyContent]]
      )
    ).thenReturn(SubscriptionFlowInfo(4, 5, AddressDetailsSubscriptionFlowPage))

    when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
      Future.successful(Some(true))
    )

  }




  "WhatIsYourIdentifierController" should {


    "while Viewing the form" should {
      assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
        mockAuthConnector,
        whatIsYourIdentifierController.form(Journey.Migrate)
      )
    }

    "display the form for Nino" in {
      displayForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe empty
        page.getElementsText(fieldLevelErrorNino) shouldBe empty
      }
    }

    "display the reviewForm for Nino" in {
      displayReviewForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe empty
        page.getElementsText(fieldLevelErrorNino) shouldBe empty
      }
    }

    "display the form for UTR" in {
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(false))
      )

      displayForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe empty
        page.getElementsText(fieldLevelErrorNino) shouldBe empty
      }
    }

    "display the form for UTR in review mode" in {
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(false))
      )

      displayReviewForm() { result =>
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

  "Submitting the form in review mode" should {

    val utr = "1111111111"
    val nino = "AB123456C"


    "redirect to 'Address Details' page when National insurance is submitted " in {

      val ninoForm = Map("id" -> nino)
      submitForm(ninoForm, true) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/subscribe-for-cds/matching/review-determine")
      }
    }

    "redirect to 'Address Details' Self Assessment Unique Taxpayer is UTR is submitted" in {
      val utrForm = Map("id" -> utr)
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(false))
      )
      submitForm(utrForm, true) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/subscribe-for-cds/matching/review-determine")
      }
    }

  }

  "Submitting the form" should {

    val utr = "1111111111"
    val nino = "AB123456C"


    "redirect to 'Address Details' page when National insurance is submitted " in {

      val ninoForm = Map("id" -> nino)
      submitForm(ninoForm) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/subscribe-for-cds/address")
      }
    }

    "redirect to 'Address Details' Self Assessment Unique Taxpayer is UTR is submitted" in {
      val utrForm = Map("id" -> utr)
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(false))
      )
      submitForm(utrForm) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/subscribe-for-cds/address")
      }
    }

    "redirect to 'What is your Self Assessment Unique Taxpayer Reference' page when  Utr is invalid" in {
      val utrForm = Map("id" -> "InvalidUtr")
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(false))
      )


      submitForm(utrForm) { result =>
        await(result)
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(RegisterHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a Unique Taxpayer Reference in the correct format"
      }
    }

    "redirect to 'What is your National insurance number' page when nino is invalid" in {
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

    "redirect to 'What is your National insurance number' page when when nino is invalid number " in {
      val ninoForm = Map("id" -> "InvalidNino")
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(true))
      )

      submitForm(ninoForm) { result =>
        await(result)
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(RegisterHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a National Insurance number in the right format"
      }
    }

    "redirect to 'What is your Self Assessment Unique Taxpayer Reference' page when utr is lest then 10 number" in {
      val utrForm = Map("id" -> "1111111")
      when(mockSessionCache.hasNino(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(false))
      )

      submitForm(utrForm) { result =>
        await(result)
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(RegisterHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Enter a Unique Taxpayer Reference in the correct format"
      }
    }

  }

  private def displayForm()(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      whatIsYourIdentifierController
        .form( Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def displayReviewForm()(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      whatIsYourIdentifierController
        .reviewForm( Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def submitForm(form: Map[String, String], isInReviewMode: Boolean= false)(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      whatIsYourIdentifierController
        .submit(isInReviewMode, Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, form))
    )
  }
}
