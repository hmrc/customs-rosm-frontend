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

package unit.playext.mappers

import org.joda.time.LocalDate
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.data.{FormError, Mapping}
import uk.gov.hmrc.customs.rosmfrontend.playext.mappers.DateTuple
import uk.gov.hmrc.play.mappers.DateFields._
import util.UnitSpec

class DateTupleSpec extends UnitSpec {

  private val customError = "some.custom.error.key"
  private val defaultDateTuple = DateTuple.dateTuple()
  private val customDateTuple = DateTuple.dateTuple(invalidDateError = customError)
  private val nonValidatingDateTuple = DateTuple.dateTuple(validate = false)

  private val d = "15"
  private val m = "7"
  private val y = "2010"
  private val validRequest = Map(day -> d, month -> m, year -> y)
  private val defaultDate = LocalDate.parse("2010-7-15")

  private val allMappings = Table(
    ("date mapping label", "date mapping instance"),
    ("default date mapping", defaultDateTuple),
    ("date mapping with custom error message", customDateTuple),
    ("not validating date mapping", nonValidatingDateTuple)
  )

  private val validatingMappings = Table(
    ("date mapping label", "date mapping instance", "error message"),
    ("default date mapping", defaultDateTuple, "cds.error.invalid.date.format"),
    ("date mapping with custom error message", customDateTuple, customError)
  )

  forAll(allMappings) { (label, dateMapping) =>
    implicit val dm = dateMapping

    label should {
      "accept a valid date" in {
        assertSuccessfulBinding(validRequest, Some(defaultDate))
      }

      "trim date elements" in {
        assertSuccessfulBinding(Map(day -> s"$d ", month -> s" $m", year -> s" $y "), Some(defaultDate))
      }

      "return None when all the fields are empty" in {
        assertSuccessfulBinding(Map(day -> "", month -> "", year -> ""), None)
      }
    }
  }

  forAll(validatingMappings) { (label, dateMapping, errorMessage) =>
    implicit val dm = dateMapping

    s"validating $label" should {

      "reject invalid date" in {
        assertErrorTriggered(validRequest + (day -> "32"), errorMessage)
      }

      "reject date without day" in {
        assertErrorTriggered(validRequest - day, errorMessage)
      }

      "reject date without month" in {
        assertErrorTriggered(validRequest - month, errorMessage)
      }

      "reject date without year" in {
        assertErrorTriggered(validRequest - year, errorMessage)
      }

      "reject day with characters instead of numbers" in {
        assertErrorTriggered(validRequest + (day -> "foo"), errorMessage)
      }

      "reject month with characters instead of numbers" in {
        assertErrorTriggered(validRequest + (month -> "foo"), errorMessage)
      }

      "reject year with characters instead of numbers" in {
        assertErrorTriggered(validRequest + (year -> "foo"), errorMessage)
      }

      "reject invalid month" in {
        assertErrorTriggered(validRequest + (month -> "13"), errorMessage)
      }

      "reject year with 1 digit length" in {
        assertErrorTriggered(validRequest + (year -> "9"), errorMessage)
      }

      "reject year with 2 digits length" in {
        assertErrorTriggered(validRequest + (year -> "73"), errorMessage)
      }

      "reject year with 3 digits length" in {
        assertErrorTriggered(validRequest + (year -> "832"), errorMessage)
      }

      "reject year with more than 4 digits length" in {
        assertErrorTriggered(validRequest + (year -> "12017"), errorMessage)
      }
    }
  }

  "non-validating date mapping" should {
    implicit val dm = nonValidatingDateTuple

    "ignore invalid date" in {
      assertSuccessfulBinding(validRequest + (day -> "32"), None)
    }

    "ignore date without day" in {
      assertSuccessfulBinding(validRequest - day, None)
    }

    "ignore date without month" in {
      assertSuccessfulBinding(validRequest - month, None)
    }

    "ignore date without year" in {
      assertSuccessfulBinding(validRequest - year, None)
    }

    "ignore day with characters instead of numbers" in {
      assertSuccessfulBinding(validRequest + (day -> "foo"), None)
    }

    "ignore month with characters instead of numbers" in {
      assertSuccessfulBinding(validRequest + (month -> "foo"), None)
    }

    "ignore year with characters instead of numbers" in {
      assertSuccessfulBinding(validRequest + (year -> "foo"), None)
    }

    "ignore invalid month" in {
      assertSuccessfulBinding(validRequest + (month -> "13"), None)
    }

    "accept year with 1 digit length" in {
      assertSuccessfulBinding(validRequest + (year -> "9"), Some(LocalDate.parse("9-7-15")))
    }

    "accept year with 2 digits length" in {
      assertSuccessfulBinding(validRequest + (year -> "73"), Some(LocalDate.parse("73-7-15")))
    }

    "accept year with 3 digits length" in {
      assertSuccessfulBinding(validRequest + (year -> "832"), Some(LocalDate.parse("832-7-15")))
    }

    "accept year with more than 4 digits length" in {
      assertSuccessfulBinding(validRequest + (year -> "12017"), Some(LocalDate.parse("12017-7-15")))
    }
  }

  private def assertSuccessfulBinding(request: Map[String, String], expectedResult: Option[LocalDate])(
    implicit dateMapping: Mapping[Option[LocalDate]]
  ) {
    dateMapping.bind(request) shouldBe Right(expectedResult)
  }

  private def assertErrorTriggered(
    request: Map[String, String],
    errorMessage: String = "cds.error.invalid.date.format"
  )(implicit dateMapping: Mapping[Option[LocalDate]]) {
    dateMapping.bind(request) shouldBe Left(Seq(FormError("", errorMessage)))
  }

}
