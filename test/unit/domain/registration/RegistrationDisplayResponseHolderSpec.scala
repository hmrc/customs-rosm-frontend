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

package unit.domain.registration

import play.api.libs.json.Json
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Address
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching.{ContactResponse, IndividualResponse}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.registration.{
  RegistrationDisplayResponse,
  RegistrationDisplayResponseHolder,
  ResponseCommon,
  ResponseDetail
}
import util.UnitSpec

class RegistrationDisplayResponseHolderSpec extends UnitSpec {

  private val expectedJson = Json.parse("""{
      |"registrationDisplayResponse":
      | {
      |   "responseCommon":
      |   {
      |     "status": "OK",
      |     "processingDate": "2016-09-02T09:30:47Z",
      |     "taxPayerID": "1111111111"
      |   },
      |   "responseDetail":
      |   {
      |     "SAFEID": "XY0001111111111",
      |     "isEditable": true,
      |     "isAnAgent": false,
      |     "isAnIndividual": true,
      |     "individual":
      |     {
      |       "firstName": "fname",
      |       "lastName": "lname",
      |       "dateOfBirth": "1989-01-01"
      |     },
      |     "address":
      |     {
      |       "addressLine1": "Line1",
      |       "addressLine2": "Line2",
      |       "postalCode": "postcode",
      |       "countryCode": "GB"
      |     },
      |     "contactDetails":
      |     {
      |       "phoneNumber": "01234567890",
      |       "emailAddress": "test@example.com"
      |     }
      |   }
      |}}
    """.stripMargin)

  val responseCommon = ResponseCommon("OK", None, "2016-09-02T09:30:47Z", None, Some("1111111111"))

  val individualResponse = IndividualResponse("fname", None, "lname", Some("1989-01-01"))

  val address = Address("Line1", Some("Line2"), None, None, Some("postcode"), "GB")

  val contactDetails = ContactResponse(Some("01234567890"), None, None, Some("test@example.com"))

  val responseDetail = ResponseDetail(
    "XY0001111111111",
    None,
    None,
    true,
    false,
    true,
    Some(individualResponse),
    None,
    address,
    contactDetails
  )

  val responseHolder = RegistrationDisplayResponseHolder(
    RegistrationDisplayResponse(responseCommon, Some(responseDetail))
  )

  "RegistrationDisplayResponseHolder" should {
    "read from json to case class" in {
      Json.fromJson[RegistrationDisplayResponseHolder](expectedJson).get shouldBe responseHolder
    }

    "write case class to json" in {
      Json.toJson(responseHolder) shouldBe expectedJson
    }
  }
}
