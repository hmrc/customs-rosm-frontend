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

import org.scalatest.concurrent.ScalaFutures
import play.api.Configuration
import play.api.test.FakeRequest
import play.api.test.Helpers.LOCATION
import play.mvc.Http.Status._
import uk.gov.hmrc.customs.rosmfrontend.CdsErrorHandler
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionTimeOutException
import uk.gov.hmrc.customs.rosmfrontend.views.html.{client_error_template, error_template, notFound}
import util.ControllerSpec

class CdsErrorHandlerSpec extends ControllerSpec with ScalaFutures {
  val configuration = mock[Configuration]

  private val errorTemplateView = app.injector.instanceOf[error_template]
  private val clientErrorTemplateView = app.injector.instanceOf[client_error_template]
  private val notFoundView = app.injector.instanceOf[notFound]

  val cdsErrorHandler =
    new CdsErrorHandler(messagesApi, configuration, errorTemplateView, clientErrorTemplateView, notFoundView)

  private val mockRequest = FakeRequest()

  "Cds error handler" should {
    "redirect to correct page after receive 500 error" in {
      whenReady(cdsErrorHandler.onServerError(mockRequest, new Exception())) { result =>
        val page = CdsPage(bodyOf(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        page.title should startWith("Sorry, there is a problem with the service")
      }
    }

    "redirect to registration security sign out" in {
      val mockRegisterRequest = FakeRequest(method = "GET", path = "register-for-cds")

      whenReady(cdsErrorHandler.onServerError(mockRegisterRequest, SessionTimeOutException("xyz"))) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "/customs/register-for-cds/display-sign-out"
      }
    }

    "redirect to subscription security sign out" in {
      val mockRegisterRequest = FakeRequest(method = "GET", "subscribe-for-cds")

      whenReady(cdsErrorHandler.onServerError(mockRegisterRequest, SessionTimeOutException("xyz"))) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "/customs/subscribe-for-cds/display-sign-out"
      }
    }

    "Redirect to the notfound page on 404 error" in {
      whenReady(cdsErrorHandler.onClientError(mockRequest, statusCode = NOT_FOUND)) { result =>
        val page = CdsPage(bodyOf(result))

        result.header.status shouldBe NOT_FOUND
        page.title should startWith("Page not found")
      }
    }
  }
}
