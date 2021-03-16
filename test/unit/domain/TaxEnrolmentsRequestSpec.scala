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

package unit.domain

import org.joda.time.LocalDate
import play.api.libs.json.Json
import uk.gov.hmrc.customs.rosmfrontend.domain.TaxEnrolmentsRequest._
import uk.gov.hmrc.customs.rosmfrontend.domain.{KeyValue, TaxEnrolmentsRequest}
import util.UnitSpec
class TaxEnrolmentsRequestSpec extends UnitSpec {
  val expectedTaxEnrolmentsRequestJson = Json.parse("""{
                                   |    "serviceName": "HMRC-CUS-ORG",
                                   |    "identifiers": [
                                   |        {
                                   |            "key": "EORINUMBER",
                                   |            "value": "GB9999999999"
                                   |        }
                                   |    ],
                                   |    "verifiers": [
                                   |        {
                                   |            "key": "DATEOFESTABLISHMENT",
                                   |            "value": "28/04/2010"
                                   |        }
                                   |    ],
                                   |    "subscriptionState": "SUCCEEDED"
                                   |
                                   |}""".stripMargin)

  val expectedTaxEnrolmentsNoDOERequestJson = Json.parse("""{
                                                     |    "serviceName": "HMRC-CUS-ORG",
                                                     |    "identifiers": [
                                                     |        {
                                                     |            "key": "EORINUMBER",
                                                     |            "value": "GB9999999999"
                                                     |        }
                                                     |    ],
                                                     |    "subscriptionState": "SUCCEEDED"
                                                     |
                                                     |}""".stripMargin)

  "TaxEnrolmentsRequest" should {
    "transform to valid format Json" in {
      val date = LocalDate.parse("2010-04-28")
      val identifiers = List(KeyValue(key = "EORINUMBER", value = "GB9999999999"))
      val verifiers = List(KeyValue(key = "DATEOFESTABLISHMENT", value = date.toString(pattern)))
      val taxEnrolmentsRequest = TaxEnrolmentsRequest(identifiers = identifiers, verifiers = Some(verifiers))
      val taxEnrolmentsRequestJson = Json.toJson[TaxEnrolmentsRequest](taxEnrolmentsRequest)
      Json.prettyPrint(taxEnrolmentsRequestJson) shouldBe Json.prettyPrint(expectedTaxEnrolmentsRequestJson)
    }

    "transform to valid format Json when DATEOFESTABLISHMENT isa not present" in {
      val identifiers = List(KeyValue(key = "EORINUMBER", value = "GB9999999999"))
      val verifiers = None
      val taxEnrolmentsRequest = TaxEnrolmentsRequest(identifiers = identifiers, verifiers = verifiers)
      val taxEnrolmentsRequestJson = Json.toJson[TaxEnrolmentsRequest](taxEnrolmentsRequest)
      Json.prettyPrint(taxEnrolmentsRequestJson) shouldBe Json.prettyPrint(expectedTaxEnrolmentsNoDOERequestJson)
    }
  }
}
