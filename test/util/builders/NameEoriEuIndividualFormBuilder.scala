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
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Individual

object NameEoriEuIndividualFormBuilder {

  val ValidFirstName = "John"
  val ValidMiddleName = "Midle"
  val ValidLastName = "Doe"
  val ValidDateOfBirthString = "2015-10-15"

  val ValidEoriId = TestData.Eori
  val ValidEori = Eori(ValidEoriId)

  val ValidEoriRequestWithoutMiddleName = Map(
    "yes-no-answer" -> true.toString,
    "registration-model.first-name" -> ValidFirstName,
    "registration-model.last-name" -> ValidLastName,
    "registration-model.eori" -> ValidEoriId,
    "registration-model.date-of-birth.day" -> "15",
    "registration-model.date-of-birth.month" -> "10",
    "registration-model.date-of-birth.year" -> "2015"
  )

  val ValidEoriRequestWithMiddleName = ValidEoriRequestWithoutMiddleName +
    "registration-model.middle-name" -> ValidMiddleName

  val ValidNoEoriRequest = Map("yes-no-answer" -> "false")

  val ValidEuIndividualWithoutMiddleName =
    Individual(ValidFirstName, middleName = None, ValidLastName, ValidDateOfBirthString)
}
