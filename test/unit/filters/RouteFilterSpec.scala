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

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{RequestHeader, Result, Results}
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.rosmfrontend.CdsErrorHandler
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.filters.RouteFilter
import util.UnitSpec

import scala.concurrent.Future

class RouteFilterSpec extends UnitSpec with MockitoSugar {

  implicit val system = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  val errorHandler = mock[CdsErrorHandler]
  val config = mock[AppConfig]

  "RouteFilter" should {

    "ignore the filter when application is running in Dev mode" in {
      val filter = new RouteFilter(config, errorHandler)
      val request = FakeRequest("GET", "/some-url")

      val result: Result = await(filter.apply((r: RequestHeader) => Future.successful(Results.Ok))(request))

      status(result) shouldBe 200
    }
  }
}
