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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.ApplicationController
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.migration_start
import uk.gov.hmrc.customs.rosmfrontend.views.html.{
  accessibility_statement,
  accessibility_statement_get_access_cds,
  start
}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ApplicationControllerSpec extends ControllerSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSessionCache = mock[SessionCache]

  private val startView = app.injector.instanceOf[start]
  private val migrationStartView = app.injector.instanceOf[migration_start]
  private val accessibilityStatementView = app.injector.instanceOf[accessibility_statement]
  private val accessibilityStatementGetAccessCdsView = app.injector.instanceOf[accessibility_statement_get_access_cds]

  val controller = new ApplicationController(
    app,
    mockAuthConnector,
    mockRequestSessionData,
    mcc,
    startView,
    migrationStartView,
    accessibilityStatementView,
    accessibilityStatementGetAccessCdsView,
    mockSessionCache,
    appConfig
  )

  "Navigating to start" should {

    "allow unauthenticated users to access the start page" in {
      invokeStartFormWithUnauthenticatedUser() { result =>
        status(result) shouldBe OK
        CdsPage(bodyOf(result)).title should startWith("Get an EORI number")
      }
    }

    "allow unauthenticated users to access the start with subscription page" in {
      invokeStartFormSubscriptionWithUnauthenticatedUser() { result =>
        status(result) shouldBe OK
        CdsPage(bodyOf(result)).title should startWith("Get access to the Customs Declaration Service (CDS)")
      }
    }
  }

  "Navigating to logout" should {
    "logout an authenticated user" in {
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))

      invokeLogoutWithAuthenticatedUser() { result =>
        session(result).get(SessionKeys.sessionId) shouldBe None
      }
    }
  }

  "Navigating to keepAlive" should {
    "return a status of OK" in {
      invokeKeepAliveWithUnauthenticatedUser() { result =>
        status(result) shouldBe OK
      }
    }
  }

  def invokeLogoutWithAuthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(controller.logout(Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  def invokeStartFormWithUnauthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    test(controller.start.apply(SessionBuilder.buildRequestWithSessionNoUser))
  }

  def invokeStartFormSubscriptionWithUnauthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    test(controller.startSubscription.apply(SessionBuilder.buildRequestWithSessionNoUser))
  }

  def invokeKeepAliveWithUnauthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    test(controller.keepAlive(Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSessionNoUser))
  }
}
