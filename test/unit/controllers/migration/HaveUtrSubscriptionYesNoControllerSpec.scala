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

import common.pages.matching.{SubscriptionRowCompanyUtrYesNo, SubscriptionRowIndividualsUtrYesNo}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.HaveUtrSubscriptionYesNoController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType._
import uk.gov.hmrc.customs.rosmfrontend.domain.NameOrganisationMatchModel
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{SubscriptionFlowInfo, SubscriptionPage}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.match_utr_subscription_yes_no
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.matching.OrganisationUtrFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HaveUtrSubscriptionYesNoControllerSpec extends ControllerSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockSubscriptionFlowInfo = mock[SubscriptionFlowInfo]
  private val mockSubscriptionPage = mock[SubscriptionPage]

  private val matchUtrSubscriptionYesNoView = app.injector.instanceOf[match_utr_subscription_yes_no]

  private val nextPageFlowUrl = "/customs/subscribe-for-cds/row-utr"
  private val addressPageFlowUrl = "/customs/subscribe-for-cds/address"

  val controller = new HaveUtrSubscriptionYesNoController(
    app,
    mockAuthConnector,
    mockRequestSessionData,
    mockSubscriptionFlowManager,
    mcc,
    matchUtrSubscriptionYesNoView,
    mockSubscriptionDetailsService
  )

  val utrLabelXPath = "//*[@id='utr-outer']//label"

  "HaveUtrSubscriptionYesNoController createForm" should {
    "return OK and display correct page when orgType is Company" in {

      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(Company))
      createForm(Journey.Migrate) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(SubscriptionRowCompanyUtrYesNo.title)
      }
    }

    "return OK and display correct page when orgType is Sole Trader" in {

      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(SoleTrader))
      createForm(Journey.Migrate) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(SubscriptionRowIndividualsUtrYesNo.title)

      }
    }

    "return OK and display correct page when orgType is Individual" in {

      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(Individual))
      createForm(Journey.Migrate) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(SubscriptionRowIndividualsUtrYesNo.title)
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

    "cache UTR and redirect to Address Page of the flow when rest of world and Company" in {
      reset(mockSubscriptionDetailsService)
      val nameOrganisationMatchModel = NameOrganisationMatchModel("orgName")
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(Company))
      mockSubscriptionFlow(addressPageFlowUrl)
      submit(Journey.Migrate, NoUtrYesNoRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe addressPageFlowUrl
      }
    }

    "cache UTR and redirect to Address Page of the flow when rest of world other than Company" in {
      reset(mockSubscriptionDetailsService)
      val nameOrganisationMatchModel = NameOrganisationMatchModel("orgName")
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(SoleTrader))
      mockSubscriptionFlow(addressPageFlowUrl)
      submit(Journey.Migrate, NoUtrYesNoRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe addressPageFlowUrl
      }
    }

    "redirect to next page in the flow when 'No' UTR selected" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(SoleTrader))
      mockSubscriptionFlow(nextPageFlowUrl)
      submit(Journey.Migrate, NoUtrYesNoRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe addressPageFlowUrl
      }
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

  private def mockSubscriptionFlow(url: String) = {
    when(mockSubscriptionFlowManager.stepInformation(any())(any[HeaderCarrier], any[Request[AnyContent]]))
      .thenReturn(mockSubscriptionFlowInfo)
    when(mockSubscriptionFlowInfo.nextPage).thenReturn(mockSubscriptionPage)
    when(mockSubscriptionPage.url).thenReturn(url)
  }
}
