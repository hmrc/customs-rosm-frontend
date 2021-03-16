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

package unit.controllers

import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Result
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.HowCanWeIdentifyYouController
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.how_can_we_identify_you
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TrackingConsentSpec extends ControllerSpec with GuiceOneAppPerSuite with MockitoSugar {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSubscriptionBusinessService = mock[SubscriptionBusinessService]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockSubscriptionDetailsHolderService = mock[SubscriptionDetailsService]
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

  "Tracking consent" should {
    "include the javascript file in the header" in {
      showForm(Map.empty) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementAttribute("//head/script[1]", "src") should endWith("tracking.js")
      }
    }
  }

  def showForm(form: Map[String, String], userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller.createForm(Journey.Migrate).apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }
}
