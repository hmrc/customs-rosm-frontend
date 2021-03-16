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

import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.customs.rosmfrontend.controllers.JourneyTypeFromUrl
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import util.UnitSpec

class JourneyTypeFromUrlSpec extends UnitSpec with MockitoSugar {

  private implicit val mockRequest: Request[AnyContent] = mock[Request[AnyContent]]
  private val journeyTypeTrait = new JourneyTypeFromUrl {}

  "Journey type" can {

    "be extracted from URL as a string" in {

      when(mockRequest.path).thenReturn("/path1/path2/path3/customs/some.journey/path4")
      journeyTypeTrait.journeyTypeFromUrl shouldBe "some.journey"

      when(mockRequest.path).thenReturn("/customs/another.journey/path1")
      journeyTypeTrait.journeyTypeFromUrl shouldBe "another.journey"

      when(mockRequest.path).thenReturn("/customs/yetAnotherJourney/")
      journeyTypeTrait.journeyTypeFromUrl shouldBe "yetAnotherJourney"
    }

    "be extracted from URL as a Journey Type" in {

      when(mockRequest.path).thenReturn("/path1/path2/path3/customs/register-for-cds/path4")
      journeyTypeTrait.journeyFromUrl shouldBe Journey.GetYourEORI

      when(mockRequest.path).thenReturn("/customs/subscribe-for-cds/path1")
      journeyTypeTrait.journeyFromUrl shouldBe Journey.Migrate

      when(mockRequest.path).thenReturn("/customs/register-for-cds/")
      journeyTypeTrait.journeyFromUrl shouldBe Journey.GetYourEORI
    }
  }
}
