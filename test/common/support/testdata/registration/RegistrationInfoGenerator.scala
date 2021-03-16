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

package common.support.testdata.registration

import org.joda.time.LocalDate
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.NonUKIdentification
import uk.gov.hmrc.customs.rosmfrontend.domain.{IndividualRegistrationInfo, OrgRegistrationInfo, TaxPayerId}

object RegistrationInfoGenerator {

  val individualRegistrationInfoWithAllOptionalValues = IndividualRegistrationInfo(
    firstName = "John",
    middleName = Some("Mark"),
    lastName = "Doe",
    dateOfBirth = Some(new LocalDate("1981-02-28")),
    taxPayerId = TaxPayerId("someTaxPayerId"),
    lineOne = "Line 1",
    lineTwo = Some("line 2"),
    lineThree = Some("line 3"),
    lineFour = Some("line 4"),
    postcode = Some("SW1A 2BQ"),
    country = "ZZ",
    phoneNumber = Some("01632961234"),
    email = Some("john.doe@example.com"),
    nonUKIdentification = Some(NonUKIdentification("id-number", "issuing-Institution", "issuing-CountryCode")),
    isAnAgent = false
  )

  val organisationRegistrationInfoWithAllOptionalValues = OrgRegistrationInfo(
    name = "Test Business Name not really a Ltd",
    taxPayerId = TaxPayerId("someTaxPayerId"),
    lineOne = "Line 1",
    lineTwo = Some("line 2"),
    lineThree = Some("line 3"),
    lineFour = Some("line 4"),
    postcode = Some("SW1A 2BQ"),
    country = "ZZ",
    phoneNumber = Some("01632961234"),
    email = Some("john.doe@example.com"),
    organisationType = Some("company"),
    nonUKIdentification = Some(NonUKIdentification("id-number", "issuing-Institution", "issuing-CountryCode")),
    isAnAgent = false,
    isAGroup = false
  )
}
