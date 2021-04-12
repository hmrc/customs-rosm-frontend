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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.ReturnUserController
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.return_user
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnUserControllerSpec extends ControllerSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val return_user = app.injector.instanceOf[return_user]

  val controller = new ReturnUserController(app, mockAuthConnector, return_user, mcc)

  "ReturnUserController" should {
    "return OK and display correct page title" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(Some(Company))
      createForm(Journey.Migrate) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include("Checking the status of your application")
      }
    }
  }

  private def createForm(journey: Journey.Value)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(test(controller.show().apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }
}
