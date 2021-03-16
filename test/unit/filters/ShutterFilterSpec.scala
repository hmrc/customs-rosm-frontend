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
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{RequestHeader, Result, Results}
import play.api.test.FakeRequest
import play.mvc.Http.Status._
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.filters.ShutterFilter
import util.UnitSpec

import scala.concurrent.Future

class ShutterFilterSpec extends UnitSpec with MockitoSugar {

  implicit val system = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  val config = mock[AppConfig]

  "ShutterFilter" should {
    "block the get access route" in {
      when(config.isShuttered).thenReturn(true)
      val filter = new ShutterFilter(config)
      val request = FakeRequest("GET", "/customs/subscribe-for-cds/")
      val result: Result = await(filter.apply((r: RequestHeader) => Future.successful(Results.Ok))(request))

      status(result) shouldBe SEE_OTHER
    }

    "allow the get access route" in {
      when(config.isShuttered).thenReturn(false)
      val filter = new ShutterFilter(config)
      val request = FakeRequest("GET", "/customs/subscribe-for-cds/")
      val result: Result = await(filter.apply((r: RequestHeader) => Future.successful(Results.Ok))(request))
      status(result) shouldBe OK
    }

    "allow the get an eori route isShuttered true" in {
      when(config.isShuttered).thenReturn(true)
      val filter = new ShutterFilter(config)
      val request = FakeRequest("GET", "/customs/register-for-cds/")
      val result: Result = await(filter.apply((r: RequestHeader) => Future.successful(Results.Ok))(request))
      status(result) shouldBe OK
    }

    "allow the get an eori route isShuttered false" in {
      when(config.isShuttered).thenReturn(false)
      val filter = new ShutterFilter(config)
      val request = FakeRequest("GET", "/customs/register-for-cds/")
      val result: Result = await(filter.apply((r: RequestHeader) => Future.successful(Results.Ok))(request))
      status(result) shouldBe OK
    }
  }

}
