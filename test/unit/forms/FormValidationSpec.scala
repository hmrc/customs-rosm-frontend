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

package unit.forms

import play.api.data.Form
import uk.gov.hmrc.customs.rosmfrontend.domain.{IndividualNameAndDateOfBirth, NameDobMatchModel, NinoMatch}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms
import uk.gov.hmrc.domain.Generator
import util.UnitSpec

import scala.util.Random

class FormValidationSpec extends UnitSpec {

  def randomNino: String = new Generator(new Random()).nextNino.nino

  lazy val nameDobForm: Form[NameDobMatchModel] = MatchingForms.enterNameDobForm
  lazy val ninoForm: Form[NinoMatch] = MatchingForms.ninoForm
  lazy val thirdCountryIndividualNameDateOfBirthForm: Form[IndividualNameAndDateOfBirth] =
    MatchingForms.thirdCountryIndividualNameDateOfBirthForm

  val formData = Map(
    "first-name" -> "ff",
    "middle-name" -> "",
    "last-name" -> "ddd",
    "date-of-birth.day" -> "22",
    "date-of-birth.month" -> "10",
    "date-of-birth.year" -> "2019"
  )
  val formDataNino = Map(
    "first-name" -> "ff",
    "last-name" -> "ddd",
    "nino" -> randomNino,
    "date-of-birth.day" -> "22",
    "date-of-birth.month" -> "10",
    "date-of-birth.year" -> "2019"
  )
  val formDataRow = Map(
    "given-name" -> "ff",
    "middle-name" -> "",
    "family-name" -> "ddd",
    "date-of-birth.day" -> "22",
    "date-of-birth.month" -> "10",
    "date-of-birth.year" -> "2019"
  )

  "NameDobForm" should {

    "only accept valid form" in {
      val data = formData
      val res = nameDobForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept first-name with ' apostrophe" in {
      val data = formData.updated("first-name", "apos'trophe")
      val res = nameDobForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept last-name with ' apostrophe" in {
      val data = formData.updated("last-name", "apos'trophe")
      val res = nameDobForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept middle-name with ' apostrophe" in {
      val data = formData.updated("middle-name", "apos'trophe")
      val res = nameDobForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "fail when a First Name is invalid" in {
      val data = formData.updated("first-name", "")
      val res = nameDobForm.bind(data)
      assert(res.errors.nonEmpty)
    }

    "fail when a Last Name is invalid" in {
      val data = formData.updated("last-name", "")
      val res = nameDobForm.bind(data)
      assert(res.errors.nonEmpty)
    }

  }

  "NinoForm" should {

    "only accept valid form" in {
      val data = formDataNino
      val res = ninoForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept first-name with ' apostrophe" in {
      val data = formDataNino.updated("first-name", "apos'trophe")
      val res = ninoForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept last-name with ' apostrophe" in {
      val data = formDataNino.updated("last-name", "apos'trophe")
      val res = ninoForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "fail when a First Name is invalid" in {
      val data = formDataNino.updated("first-name", "")
      val res = ninoForm.bind(data)
      assert(res.errors.nonEmpty)
    }

    "fail when a Last Name is invalid" in {
      val data = formDataNino.updated("last-name", "")
      val res = ninoForm.bind(data)
      assert(res.errors.nonEmpty)
    }

  }

  "RowIndividualForm" should {

    "only accept valid form" in {
      val data = formDataRow
      val res = thirdCountryIndividualNameDateOfBirthForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept Given name with ' apostrophe" in {
      val data = formDataRow.updated("given-name", "apos'trophe")
      val res = thirdCountryIndividualNameDateOfBirthForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept Family name with ' apostrophe" in {
      val data = formDataRow.updated("family-name", "apos'trophe")
      val res = thirdCountryIndividualNameDateOfBirthForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept middle name with ' apostrophe" in {
      val data = formDataRow.updated("middle-name", "apos'trophe")
      val res = thirdCountryIndividualNameDateOfBirthForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "fail when a Given Name is invalid" in {
      val data = formDataRow.updated("given-name", "")
      val res = thirdCountryIndividualNameDateOfBirthForm.bind(data)
      assert(res.errors.nonEmpty)
    }

    "fail when a Family Name is invalid" in {
      val data = formDataRow.updated("family-name", "")
      val res = thirdCountryIndividualNameDateOfBirthForm.bind(data)
      assert(res.errors.nonEmpty)
    }

  }

}
