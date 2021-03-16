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

package uk.gov.hmrc.customs.rosmfrontend.playext.mappers

import org.joda.time.LocalDate
import play.api.data.Forms.{optional, text, tuple}
import play.api.data.Mapping
import uk.gov.hmrc.play.mappers.DateFields._

import scala.util.Try

object DateTuple {

  def dateTuple(
    validate: Boolean = true,
    invalidDateError: String = "cds.error.invalid.date.format"
  ): Mapping[Option[LocalDate]] = {
    def tuple2Date(tuple: (Option[String], Option[String], Option[String])) = tuple match {
      case (Some(y), Some(m), Some(d)) =>
        try {
          Some(new LocalDate(y.trim.toInt, m.trim.toInt, d.trim.toInt))
        } catch {
          case e: Exception if validate => throw e
          case _: Throwable             => None
        }

      case _ => None
    }

    def date2Tuple(maybeDate: Option[LocalDate]) = maybeDate match {
      case Some(d) => (Some(d.getYear.toString), Some(d.getMonthOfYear.toString), Some(d.getDayOfMonth.toString))
      case _       => (None, None, None)
    }

    dateTupleMapping
      .verifying(
        invalidDateError,
        _ match {
          case (None, None, None) => true

          case (yearOption, monthOption, dayOption) if validate =>
            Try({
              val y = yearOption.getOrElse(throw new Exception("Year missing")).trim.toInt
              if (!(1000 to 9999 contains y)) throw new Exception("Year must be 4 digits")

              val m = monthOption.getOrElse(throw new Exception("Month missing"))
              val d = dayOption.getOrElse(throw new Exception("Day missing"))
              new LocalDate(y, m.trim.toInt, d.trim.toInt)
            }).isSuccess

          case _ => true
        }
      )
      .transform[Option[LocalDate]](tuple2Date, date2Tuple)
  }

  private val dateTupleMapping =
    tuple(year -> optional(text), month -> optional(text), day -> optional(text))

}
