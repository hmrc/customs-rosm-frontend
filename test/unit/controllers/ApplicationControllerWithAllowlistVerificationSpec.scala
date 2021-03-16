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
import org.mockito.Mockito._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
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
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ApplicationControllerWithAllowlistVerificationSpec extends ControllerSpec {
  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false)
    .configure(Map("allowlistEnabled" -> true, "allowlist" -> "mister_allow@example.com,  bob@example.com"))
    .build()

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

  "Navigating to logout" should {
    "logout a non-allowlisted user" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector, userEmail = Some("not@example.com"))
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))

      controller.logout(Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(defaultUserId)) map { _ =>
        verify(mockSessionCache).remove(any[HeaderCarrier])
      }
    }
  }
}
