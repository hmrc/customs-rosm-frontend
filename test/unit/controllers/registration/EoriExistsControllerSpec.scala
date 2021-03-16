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

package unit.controllers.registration

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.EoriExistsController
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.recovery_registration_exists
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import unit.controllers.CdsPage
import scala.concurrent.Future
import scala.util.Random

class EoriExistsControllerSpec extends ControllerSpec {
  private val mockAuthConnector = mock[AuthConnector]
  private val mockSessionCache = mock[SessionCache]
  private val recoveryRegistrationexists = app.injector.instanceOf[recovery_registration_exists]

  private val controller =
    new EoriExistsController(app, mockAuthConnector, mockSessionCache, mcc, recoveryRegistrationexists)

  "EoriExistsController" should {
    "return Ok 200 when eoriExist method is requested" in {
      val eori = s"GB${Random.nextInt(1000000000)}"
      when(mockSessionCache.eori(any[HeaderCarrier])).thenReturn(Future.successful(Some(eori)))
      when(mockSessionCache.name(any[HeaderCarrier])).thenReturn(Future.successful(Some("OrgName")))
      eoriExistPage(Journey.GetYourEORI) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should startWith("You already have an EORI - Get access to CDS - GOV.UK")
      }
    }
  }

  private def eoriExistPage(journey: Journey.Value)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(test(controller.eoriExist(journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

}
