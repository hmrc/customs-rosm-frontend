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

package unit.filters

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.customs.rosmfrontend.controllers.EoriTextDownloadController
import uk.gov.hmrc.customs.rosmfrontend.domain.SubscriptionCreateOutcome
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.eori_number_text_download
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.{AuthBuilder, SessionBuilder}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class UserTypeFilterSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockCache = mock[SessionCache]
  private val eoriNumberTextDownloadView = app.injector.instanceOf[eori_number_text_download]

  private val controller =
    new EoriTextDownloadController(app, mockAuthConnector, mockCache, eoriNumberTextDownloadView, mcc)

  override def beforeEach(): Unit =
    when(mockCache.subscriptionCreateOutcome(any[HeaderCarrier]))
      .thenReturn(Future.successful(SubscriptionCreateOutcome("20/01/2019", "John Doe", Some("GB123456789012"))))

  "User Type Filter verification" should {

    "return Redirect (303) when a agent attempts to access a route" in {
      AuthBuilder.withAuthorisedUser("user-1236213", mockAuthConnector, userAffinityGroup = AffinityGroup.Agent)

      val result = controller
        .download(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      status(result) shouldBe SEE_OTHER
    }

    "return Redirect (303) when an organisation with an assistant account attempts to access a route" in {
      AuthBuilder.withAuthorisedUser(
        "user-1236213",
        mockAuthConnector,
        userAffinityGroup = AffinityGroup.Organisation,
        userCredentialRole = Some(Assistant)
      )

      val result = controller
        .download(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      status(result) shouldBe SEE_OTHER
    }

    "return Redirect (303) when an organisation with an assistant account attempts to access a route the URL should contain you cannot use this service" in {
      AuthBuilder.withAuthorisedUser(
        "user-1236213",
        mockAuthConnector,
        userAffinityGroup = AffinityGroup.Organisation,
        userCredentialRole = Some(Assistant)
      )

      val result = controller
        .download(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      await(result).header.headers(LOCATION) should endWith("you-cannot-use-service")
    }

    "return OK (200) when an organisation with a standard account attempts to access a route" in {
      AuthBuilder.withAuthorisedUser(
        "user-1236213",
        mockAuthConnector,
        userAffinityGroup = AffinityGroup.Organisation,
        userCredentialRole = Some(User)
      )

      val result = controller
        .download(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      status(result) shouldBe OK
    }

    "return OK (200) when an organisation with an User account attempts to access a route" in {
      AuthBuilder.withAuthorisedUser(
        "user-1236213",
        mockAuthConnector,
        userAffinityGroup = AffinityGroup.Organisation,
        userCredentialRole = Some(User)
      )

      val result = controller
        .download(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs/subscribe-for-cds/", defaultUserId))

      status(result) shouldBe OK
    }
  }
}
