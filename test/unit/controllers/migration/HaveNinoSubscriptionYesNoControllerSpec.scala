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

import common.pages.matching.SubscriptionNinoYesNoPage
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.HaveNinoSubscriptionYesNoController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{SubscriptionFlowInfo, SubscriptionPage}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.match_nino_subscription_yes_no
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HaveNinoSubscriptionYesNoControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockSubscriptionFlowInfo = mock[SubscriptionFlowInfo]
  private val mockSubscriptionPage = mock[SubscriptionPage]

  private val matchNinoSubscriptionView = app.injector.instanceOf[match_nino_subscription_yes_no]

  private val ValidNinoNoRequest = Map("have-nino" -> "false")
  private val ValidNinoYesRequest = Map("have-nino" -> "true")

  private val nextPageFlowUrl = "/customs/subscribe-for-cds/row-nino-yes-no"

  override protected def beforeEach: Unit = reset(mockSubscriptionDetailsService)

  val controller = new HaveNinoSubscriptionYesNoController(
    app,
    mockAuthConnector,
    mockSubscriptionFlowManager,
    mcc,
    matchNinoSubscriptionView,
    mockSubscriptionDetailsService
  )

  "HaveNinoSubscriptionYesNoController createForm" should {
    "return OK and display correct page" in {
      createForm(Journey.Migrate) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(SubscriptionNinoYesNoPage.title)
      }
    }
  }

  "HaveNinoSubscriptionYesNoController submit" should {
    "return BadRequest when no option selected" in {
      submit(Journey.Migrate, Map.empty[String, String]) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }
    "send to enter nino page when Y selected" in {
      mockSubscriptionFlow(nextPageFlowUrl)
      submit(Journey.Migrate, ValidNinoYesRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "/customs/subscribe-for-cds/row-nino"
      }
      verifyNoInteractions(mockSubscriptionDetailsService)
    }
    "cache None for CustomsId and redirect to Address Page of the flow" in {
      when(mockSubscriptionDetailsService.clearCachedCustomsId(any[HeaderCarrier])).thenReturn(Future.successful(()))
      mockSubscriptionFlow(nextPageFlowUrl)
      submit(Journey.Migrate, ValidNinoNoRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "/customs/subscribe-for-cds/address"
      }
      verify(mockSubscriptionDetailsService).clearCachedCustomsId(any[HeaderCarrier])
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
