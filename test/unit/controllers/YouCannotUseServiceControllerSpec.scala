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

import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.YouCannotUseServiceController
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.{unauthorized, you_cant_use_service}
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class YouCannotUseServiceControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val youCantUseService = app.injector.instanceOf[you_cant_use_service]
  private val unauthorisedView = app.injector.instanceOf[unauthorized]

  private val youCannotUseServiceController =
    new YouCannotUseServiceController(app, mockAuthConnector, youCantUseService, unauthorisedView, mcc)

  "YouCannotUseServiceController form" should {

    "display the form" in {
      displayPage() { result =>
        status(result) shouldBe UNAUTHORIZED
      }
    }

    "unauthorised form" in {
      unauthorisedPage() { result =>
        status(result) shouldBe UNAUTHORIZED
      }
    }
  }
  private def displayPage()(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      youCannotUseServiceController
        .page(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def unauthorisedPage()(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      youCannotUseServiceController
        .unauthorisedPage()
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

}
