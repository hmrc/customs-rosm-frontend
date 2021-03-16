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

package unit.allowlist

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.EoriTextDownloadController
import uk.gov.hmrc.customs.rosmfrontend.domain.SubscriptionCreateOutcome
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.eori_number_text_download
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.{AuthBuilder, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AllowlistVerificationSpec extends ControllerSpec with BeforeAndAfterEach {
  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false)
    .configure(Map("allowlistEnabled" -> true, "allowlist" -> "mister_allow@example.com,  bob@example.com"))
    .build()

  private val mockAuthConnector = mock[AuthConnector]
  private val mockCache = mock[SessionCache]

  private val eoriNumberTextDownloadView = app.injector.instanceOf[eori_number_text_download]

  private val controller =
    new EoriTextDownloadController(app, mockAuthConnector, mockCache, eoriNumberTextDownloadView, mcc)

  override def beforeEach(): Unit =
    when(mockCache.subscriptionCreateOutcome(any[HeaderCarrier]))
      .thenReturn(Future.successful(SubscriptionCreateOutcome("20/01/2019", "John Doe", Some("GB123456789012"))))

  "Allowlist verification" should {

    "return Unauthorized (401) when a non-allowlisted user attempts to access a route" in {
      AuthBuilder.withAuthorisedUser("user-1236213", mockAuthConnector, userEmail = Some("not@example.com"))

      val result = controller
        .download(Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/customs/subscribe-for-cds/unauthorised")
    }

    "return Unauthorized (401) when a user with no email address attempts to access a route" in {
      AuthBuilder.withAuthorisedUser("user-1236213", mockAuthConnector, userEmail = None)

      val result = controller
        .download(Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/customs/subscribe-for-cds/unauthorised")
    }

    "return OK (200) when a allowlisted user attempts to access a route" in {
      AuthBuilder.withAuthorisedUser("user-2300121", mockAuthConnector, userEmail = Some("mister_allow@example.com"))

      val result = controller
        .download(Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      status(result) shouldBe OK
    }

    "return OK (200) when the session is allowlisted" in {
      AuthBuilder.withAuthorisedUser("user-2300121", mockAuthConnector, userEmail = None)
      val request = SessionBuilder
        .buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId)
        .withSession("allowlisted" -> "true")

      val result = controller.download(Journey.Migrate).apply(request)

      status(result) shouldBe OK
    }

    "return OK (200) when a allowlisted user attempts to access a route with email address in a different case" in {
      AuthBuilder.withAuthorisedUser("user-2300121", mockAuthConnector, userEmail = Some("BoB@example.com"))

      val result = controller
        .download(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      status(result) shouldBe OK
    }

    "not apply to Get Your EORI journey" in {
      AuthBuilder.withAuthorisedUser("user-1236213", mockAuthConnector, userEmail = Some("not@example.com"))

      val result = controller
        .download(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/register-for-cds/", defaultUserId))

      status(result) shouldBe OK
    }
  }
}
