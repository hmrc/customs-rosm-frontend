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
import uk.gov.hmrc.customs.rosmfrontend.controllers.{EnrolmentExistsAgainstGroupIdController, ExistingApplicationInProgressController}
import uk.gov.hmrc.customs.rosmfrontend.domain.SubscriptionCreateOutcome
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.enrolment_exists_against_group_id
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.existing_eori_application_processing
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.Future

class ExistingApplicationInProgressControllerSpec extends ControllerSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSessionCache = mock[SessionCache]
  private val existingApplicationIsInProgressView = app.injector.instanceOf[existing_eori_application_processing]
  private val subscriptionCreateOutcome = SubscriptionCreateOutcome("testDate", "testFullName", Some("EoriTest"))

  private val controller = new ExistingApplicationInProgressController(
    app,
    mockAuthConnector,
    mcc,
    mockSessionCache,
    existingApplicationIsInProgressView
  )

  "ExistingApplicationInProgressController" should {
    "return OK and redirect to the ExistingApplicationInProgress page" in {
      when(mockSessionCache.mayBeSubscriptionCreateOutcome(any[HeaderCarrier])).thenReturn(Future.successful(Some(subscriptionCreateOutcome)))

      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      displayPage(Journey.GetYourEORI) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should startWith("The EORI application is being processed")
      }
    }
  }

  private def displayPage(journey: Journey.Value)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(test(controller.show(journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }
}
