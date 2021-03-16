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

package unit.models

import org.scalatest.{EitherValues, MustMatchers, OptionValues, WordSpec}
import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey

class JourneySpec extends WordSpec with MustMatchers with EitherValues with OptionValues {

  "Journey" must {

    val pathBindable = implicitly[PathBindable[Journey.Value]]
    val queryBindable = implicitly[QueryStringBindable[Journey.Value]]

    "bind to `GetYourEORI` from path" in {

      val result =
        pathBindable.bind("key", "register-for-cds").right.value

      result mustEqual Journey.GetYourEORI
    }

    "bind to `Subscription` from path" in {

      val result =
        pathBindable.bind("key", "subscribe-for-cds").right.value

      result mustEqual Journey.Migrate
    }

    "fail to bind anything else from path" in {

      val result =
        pathBindable.bind("key", "foobar").left.value

      result mustEqual "invalid journey"
    }

    "unbind from `GetYourEORI` to path" in {

      val result =
        pathBindable.unbind("key", Journey.GetYourEORI)

      result mustEqual "register-for-cds"
    }

    "unbind from `Subscription` to path" in {

      val result =
        pathBindable.unbind("key", Journey.Migrate)

      result mustEqual "subscribe-for-cds"
    }

    "bind to `GetYourEORI` from query" in {

      val result =
        queryBindable.bind("key", Map("key" -> Seq("register-for-cds"))).value.right.value

      result mustEqual Journey.GetYourEORI
    }

    "bind to `Subscription` from query" in {

      val result =
        queryBindable.bind("key", Map("key" -> Seq("subscribe-for-cds"))).value.right.value

      result mustEqual Journey.Migrate
    }

    "fail to bind anything else from query" in {

      val result =
        queryBindable.bind("key", Map("key" -> Seq("foobar"))).value.left.value

      result mustEqual "invalid journey"
    }

    "unbind from `GetYourEORI` to query" in {

      val result =
        queryBindable.unbind("key", Journey.GetYourEORI)

      result mustEqual "register-for-cds"
    }

    "unbind from `Subscription` to query" in {

      val result =
        queryBindable.unbind("key", Journey.Migrate)

      result mustEqual "subscribe-for-cds"
    }
  }
}
