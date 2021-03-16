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

package util.builders

import common.support.testdata.TestData
import uk.gov.hmrc.customs.rosmfrontend.domain.Eori
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching.Organisation
import org.joda.time.LocalDate

object NameEoriEuOrganisationFormBuilder {

  val ValidName = "John Doe"

  val ValidEoriId = TestData.Eori
  val ValidEori = Eori(ValidEoriId)
  val ValidDateEstablished: LocalDate = LocalDate.parse("2015-10-15")

  val ValidNameEoriRequest = Map(
    "yes-no-answer" -> true.toString,
    "registration-model.name" -> ValidName,
    "registration-model.eori" -> ValidEoriId,
    "registration-model.date-established.day" -> "15",
    "registration-model.date-established.month" -> "10",
    "registration-model.date-established.year" -> "2015"
  )

  val ValidNoNameEoriRequest = Map("yes-no-answer" -> "false")

  val ValidEuOrganisation = Organisation(ValidName, "Unincorporated Body")

}
