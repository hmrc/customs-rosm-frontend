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

import common.pages.matching.{SubscriptionRowCompanyUtr, SubscriptionRowIndividualsUtr}
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.HaveUtrSubscriptionController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{SubscriptionFlowInfo, SubscriptionPage}
import uk.gov.hmrc.customs.rosmfrontend.domain.{CustomsId, NameOrganisationMatchModel}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.match_utr_subscription
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.matching.OrganisationUtrFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WhatIsYourUtrSubscriptionControllerSpec extends ControllerSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]

  private val matchUtrSubscriptionView = app.injector.instanceOf[match_utr_subscription]

  val controller = new HaveUtrSubscriptionController(
    app,
    mockAuthConnector,
    mockRequestSessionData,
    mockSubscriptionFlowManager,
    mcc,
    matchUtrSubscriptionView,
    mockSubscriptionDetailsService
  )

  val utrLabelXPath = "//*[@id='utr-outer']//label"

  "HaveUtrSubscriptionController createForm" should {
    "return OK and display correct page when orgType is Company" in {

      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(Company))
      createForm(Journey.Migrate) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(SubscriptionRowCompanyUtr.title)
        page.getElementText(utrLabelXPath) shouldBe "What is your Corporation Tax Unique Taxpayer Reference? This is 10 numbers, for example 1234567890." +
          " It will be on tax returns and other letters about Corporation Tax. It may be called ‘reference’, ‘UTR’ or ‘official use’. You can find a lost UTR number."
      }
    }

    "return OK and display correct page when orgType is Sole Trader" in {

      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(SoleTrader))
      createForm(Journey.Migrate) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(SubscriptionRowIndividualsUtr.title)
        page.getElementText(utrLabelXPath) shouldBe "What is your Self Assessment Unique Taxpayer Reference? This is 10 numbers, for example 1234567890." +
          " It will be on tax returns and other letters about Self Assessment. It may be called ‘reference’, ‘UTR’ or ‘official use’. You can find a lost UTR number."

      }
    }

    "return OK and display correct page when orgType is Individual" in {

      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(Individual))
      createForm(Journey.Migrate) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(SubscriptionRowIndividualsUtr.title)
      }
    }

    "throws an exception if orgType is not found" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
      intercept[IllegalStateException] {
        createForm(Journey.Migrate)(result => status(result))
      }.getMessage shouldBe "No organisation type selected by user"
    }
  }

  "HaveUtrSubscriptionController Submit" should {
    "throws an exception if orgType is not found" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
      intercept[IllegalStateException] {
        submit(Journey.Migrate, ValidUtrRequest)(result => status(result))
      }.getMessage shouldBe "No organisation type selected by user"
    }

    "return BadRequest when no option selected" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(Company))
      submit(Journey.Migrate, Map.empty[String, String]) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }

    "return BadRequest when invalidUtr provided" in {
      val invalidUtr = "0123456789"
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(Company))
      submit(Journey.Migrate, ValidUtrRequest + ("utr" -> invalidUtr)) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }

    "cache UTR and redirect to Address Page of the flow when rest of world and Company" in {
      reset(mockSubscriptionDetailsService)
      val nameOrganisationMatchModel = NameOrganisationMatchModel("orgName")
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(Company))
      when(mockSubscriptionDetailsService.cacheCustomsId(any[CustomsId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockSubscriptionDetailsService.cachedNameDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(nameOrganisationMatchModel)))
      submit(Journey.Migrate, ValidUtrRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "/customs/subscribe-for-cds/address"
      }
      verify(mockSubscriptionDetailsService, times(1)).cacheNameIdAndCustomsId(meq("orgName"), meq(ValidUtrId))(
        any[HeaderCarrier]
      )
    }

    "cache UTR and redirect to Address Page of the flow when rest of world other than Company" in {
      reset(mockSubscriptionDetailsService)
      val nameOrganisationMatchModel = NameOrganisationMatchModel("orgName")
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(SoleTrader))
      when(mockSubscriptionDetailsService.cacheCustomsId(any[CustomsId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockSubscriptionDetailsService.cachedNameDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(nameOrganisationMatchModel)))
      submit(Journey.Migrate, ValidUtrRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "/customs/subscribe-for-cds/address"
      }
      verify(mockSubscriptionDetailsService, times(1)).cacheCustomsId(any[CustomsId])(any[HeaderCarrier])
    }

    "throws an exception with the orgType is Company and No business name or CustomsId cached" in {
      reset(mockSubscriptionDetailsService)
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(Company))
      when(mockSubscriptionDetailsService.cacheCustomsId(any[CustomsId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockSubscriptionDetailsService.cachedNameDetails(any[HeaderCarrier])).thenReturn(Future.successful(None))
      intercept[IllegalStateException] {
        submit(Journey.Migrate, ValidUtrRequest)(result => status(result))
      }.getMessage shouldBe "No business name or CustomsId cached"
    }
  }

  private def createForm(journey: Journey.Value)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(test(controller.createForm(journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

  private def submit(journey: Journey.Value, form: Map[String, String])(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(
      test(controller.submit(journey).apply(SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, form)))
    )
  }
}
