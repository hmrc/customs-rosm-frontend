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

package unit.services.countries

import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.customs.rosmfrontend.services.countries.{Countries, Country}

class CountriesSpec extends WordSpec with Matchers with GuiceOneAppPerSuite {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      Map(
        "countriesFilename" -> "test-countries.json",
        "mdgCountryCodesFilename" -> "test-mdg-country-codes.csv",
        "mdgNotIomCountryCodesFilename" -> "test-mdg-not-iom-country-codes.csv",
        "mdgEuCountryCodesFilename" -> "test-mdg-eu-country-codes.csv",
        "mdgIslandsCountryCodesFilename" -> "test-mdg-islands-country-codes.csv"
      )
    )
    .build()

  val countries = new Countries(app)

  "Countries" should {

    "give all countries with codes in alphabetical order of country name with filtering according to permitted MDG values" in {
      countries.all shouldBe List(
        Country("Afghanistan", "AF"),
        Country("Curaçao", "CW"),
        Country("Réunion", "RE"),
        Country("Zimbabwe", "ZW"),
        Country("Åland Islands", "AX")
      )
    }

    "give all eu countries with codes in alphabetical order of country name with filtering according to permitted MDG EU values" in {
      countries.eu shouldBe List(Country("France", "FR"), Country("Germany", "DE"))
    }
  }
}
