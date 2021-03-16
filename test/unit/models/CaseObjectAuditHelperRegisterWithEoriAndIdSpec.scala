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

import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.{RequestCommon, ResponseCommon}
import util.UnitSpec

import scala.collection.Map

class CaseObjectAuditHelperRegisterWithEoriAndIdSpec extends UnitSpec {

  import play.api.libs.json.Json
  import uk.gov.hmrc.customs.rosmfrontend.domain.RegisterWithEoriAndIdRequest

  val registerWithEoriAndIdRequestFromJson =
    Json.parse("""
        |{
        |    "requestCommon": {
        |      "regime":"CDS",
        |      "receiptDate": "2001-12-17T09:30:47Z",
        |      "acknowledgementReference": "2438490385338590358"
        |    },
        |    "requestDetail": {
        |      "registerModeEORI": {
        |        "EORI": "GB012345678911",
        |        "fullName": "John Doe",
        |        "address": {
        |          "streetAndNumber": "100 Parliament Street",
        |          "city": "London",
        |          "postalCode": "SW1A 2BQ",
        |          "countryCode": "GB"
        |        }
        |      },
        |      "registerModeID": {
        |        "IDType": "UTR",
        |        "IDNumber": "11111111",
        |        "isNameMatched": false,
        |        "organisation": {
        |          "name": "pg",
        |          "type": "0001"
        |        }
        |      },
        |      "govGatewayCredentials": {
        |        "email": "johndoe@example.com"
        |      }
        |    }
        |}
      """.stripMargin).as[RegisterWithEoriAndIdRequest]

  val registerWithEoriAndIdResponse =
    Json.parse("""
        |{
        |    "responseCommon": {
        |      "status": "OK",
        |      "processingDate": "2001-12-17T09:30:47Z"
        |    },
        |    "responseDetail": {
        |      "caseNumber": "C001",
        |      "outcome": "PASS",
        |      "responseData": {
        |        "SAFEID": "XA1234567890123",
        |        "trader": {
        |          "fullName": "john doe",
        |          "shortName": "Mr S"
        |        },
        |        "establishmentAddress": {
        |          "streetAndNumber": "100 Parliament Street",
        |          "city": "London",
        |          "postalCode": "SW1A 2BQ",
        |          "countryCode": "GB"
        |        },
        |        "contactDetail": {
        |          "address": {
        |            "streetAndNumber": "100 Parliament Street",
        |            "city": "London",
        |            "postalCode": "SW1A 2BQ",
        |            "countryCode": "GB"
        |          },
        |          "contactName": "Joe Smith",
        |          "phone": "1234567",
        |          "fax": "89067",
        |          "email": "asp@example.com"
        |        },
        |        "VATIDs": [
        |          {
        |            "countryCode": "AD",
        |            "vatNumber": "1234"
        |          },
        |          {
        |            "countryCode": "GB",
        |            "vatNumber": "4567"
        |          }
        |        ],
        |        "hasInternetPublication": false,
        |        "principalEconomicActivity": "P001",
        |        "hasEstablishmentInCustomsTerritory": true,
        |        "legalStatus": "Official",
        |        "thirdCountryIDNumber": [
        |          "1234",
        |          "67890"
        |        ],
        |        "dateOfEstablishmentBirth": "2018-05-16",
        |        "startDate": "2018-05-15",
        |        "expiryDate": "2018-05-16",
        |        "personType": 9
        |      }
        |    }
        |}
      """.stripMargin).as[RegisterWithEoriAndIdResponse]

  val contactDetailsFromJson = Json.parse(""" {
      |          "address": {
      |            "streetAndNumber": "98 streetAndNumber",
      |            "city": "city",
      |            "postalCode": "POSTCODE",
      |            "countryCode": "GB"
      |          },
      |          "contactName": "First Last",
      |          "phone": "1234567",
      |          "fax": "89067",
      |
      |          "email": "asp@example.com"
      |        }""".stripMargin).as[ContactDetail]

  val addressFromJson = Json.parse(""" {"streetAndNumber": "98 streetAndNumber",
      |            "city": "city",
      |            "postalCode": "POSTCODE",
      |            "countryCode": "GB"
      |}""".stripMargin).as[EstablishmentAddress]

  val traderFromJson = Json.parse("""         {
      |          "fullName": "john doe",
      |          "shortName": "Mr S"
      |        }""".stripMargin).as[Trader]

  val vatIdFfromJson = Json.parse("""[
      |          {
      |            "countryCode": "AD",
      |            "vatNumber": "1234"
      |          },
      |          {
      |            "countryCode": "GB",
      |            "vatNumber": "4567"
      |          }
      |        ]""".stripMargin).as[Seq[VatIds]]

  "ContactDetails Object" should {
    "create audit map" in {
      val contactDetailMap = contactDetailsFromJson.keyValueMap()
      contactDetailMap shouldBe Map(
        "email" -> "asp@example.com",
        "address.postalCode" -> "POSTCODE",
        "address.city" -> "city",
        "contactName" -> "First Last",
        "address.countryCode" -> "GB",
        "address.streetAndNumber" -> "98 streetAndNumber",
        "fax" -> "89067",
        "phone" -> "1234567"
      )
      contactDetailMap.size shouldBe 8

    }
  }

  "EstablishmentAddress Object" should {
    "create audit map" in {
      val addresssMap = addressFromJson.toMap()
      addresssMap shouldBe Map(
        "postalCode" -> "POSTCODE",
        "city" -> "city",
        "countryCode" -> "GB",
        "streetAndNumber" -> "98 streetAndNumber"
      )
      addresssMap.size shouldBe 4

    }
  }

  "Trader Object" should {
    "create audit map" in {
      val traderMap = traderFromJson.toMap()
      traderMap shouldBe Map("fullName" -> "john doe", "shortName" -> "Mr S")
      traderMap.size shouldBe 2

    }
  }

  "VatIds Object" should {
    "create audit map" in {
      val vatIds = vatIdFfromJson.map(_.toMap())
      vatIds shouldBe Seq(
        Map("countryCode" -> "AD", "vatNumber" -> "1234"),
        Map("countryCode" -> "GB", "vatNumber" -> "4567")
      )

      vatIds.size shouldBe 2

    }
  }
  val responseCommonFromJson = Json.parse("""  {
      |      "status": "OK",
      |      "processingDate": "2001-12-17T09:30:47Z"
      |    }""".stripMargin).as[ResponseCommon]

  "ResponseCommon Object" should {
    "create audit map" in {
      val responseCommonFromMap = responseCommonFromJson.keyValueMap()
      responseCommonFromMap shouldBe Map("status" -> "OK", "processingDate" -> "2001-12-17T09:30:47.000Z")
      responseCommonFromMap.size shouldBe 2

    }
  }

  "RegisterWithEoriAndIdResponse Object" should {
    "create audit map" in {
      val registerWithEoriAndIdResponseMap = registerWithEoriAndIdResponse.keyValueMap()
      registerWithEoriAndIdResponseMap shouldBe Map(
        "thirdCountryIDNumber.1" -> "1234",
        "VATIDs.countryCode.1" -> "GB",
        "dateOfEstablishmentBirth" -> "2018-05-16",
        "contactDetail.email" -> "asp@example.com",
        "address.postalCode" -> "SW1A 2BQ",
        "VATIDs.vatNumber.0" -> "1234",
        "caseNumber" -> "C001",
        "outcome" -> "PASS",
        "thirdCountryIDNumber.2" -> "67890",
        "processingDate" -> "2001-12-17T09:30:47.000Z",
        "contactDetail.phone" -> "1234567",
        "address.city" -> "London",
        "expiryDate" -> "2018-05-16",
        "legalStatus" -> "Official",
        "contactDetail.contactName" -> "Joe Smith",
        "address.countryCode" -> "GB",
        "contactDetail.address.postalCode" -> "SW1A 2BQ",
        "address.streetAndNumber" -> "100 Parliament Street",
        "hasInternetPublication" -> "false",
        "hasEstablishmentInCustomsTerritory" -> "true",
        "contactDetail.fax" -> "89067",
        "personType" -> "9",
        "status" -> "OK",
        "principalEconomicActivity" -> "P001",
        "VATIDs.countryCode.0" -> "AD",
        "contactDetail.address.streetAndNumber" -> "100 Parliament Street",
        "SAFEID" -> "XA1234567890123",
        "startDate" -> "2018-05-15",
        "VATIDs.vatNumber.1" -> "4567",
        "trader.shortName" -> "Mr S",
        "trader.fullName" -> "john doe",
        "contactDetail.address.city" -> "London",
        "contactDetail.address.countryCode" -> "GB"
      )
      registerWithEoriAndIdResponseMap.size shouldBe 33

    }
  }

  val requestCommonFromJson = Json.parse("""  {
      |    "regime":"CDS",
      |    "receiptDate": "2001-12-17T09:30:47Z",
      |    "acknowledgementReference": "2438490385338590358"
      |  }""".stripMargin).as[RequestCommon]

  "RequestCommon Object" should {
    "create audit map" in {
      val requestCommonFromMap = requestCommonFromJson.keyValueMap()
      requestCommonFromMap shouldBe Map(
        "regime" -> "CDS",
        "receiptDate" -> "2001-12-17T09:30:47.000Z",
        "acknowledgementReference" -> "2438490385338590358"
      )
      requestCommonFromMap.size shouldBe 3
    }
  }
  val registerModeIdJson = Json.parse("""{
      |        "IDType": "UTR",
      |        "IDNumber": "11111111",
      |        "isNameMatched": false,
      |        "organisation": {
      |          "name": "pg",
      |          "type": "0001"
      |        }
      |      }""".stripMargin).as[RegisterModeId]

  "RegisterModeId Object" should {
    "create audit map" in {
      val rregisterModeIdMap = registerModeIdJson.keyValueMap()
      rregisterModeIdMap shouldBe Map(
        "IDNumber" -> "11111111",
        "organisation.type" -> "0001",
        "isNameMatched" -> "false",
        "organisation.name" -> "pg",
        "IDType" -> "UTR"
      )
      rregisterModeIdMap.size shouldBe 5
    }
  }

  val registerModeEoriJson =
    Json.parse("""
      |  {
      |    "EORI": "GB012345678911",
      |    "fullName": "John Doe",
      |    "address": {
      |      "streetAndNumber": "100 Parliament Street",
      |      "city": "London",
      |      "postalCode": "SW1A 2BQ",
      |      "countryCode": "GB"
      |    }
      |    }""".stripMargin).as[RegisterModeEori]

  "RegisterModeEori Object" should {
    "create audit map" in {
      val registerModeEoriMap = registerModeEoriJson.keyValueMap()
      registerModeEoriMap shouldBe Map(
        "address.postalCode" -> "SW1A 2BQ",
        "fullName" -> "John Doe",
        "EORI" -> "GB012345678911",
        "address.city" -> "London",
        "address.countryCode" -> "GB",
        "address.streetAndNumber" -> "100 Parliament Street"
      )
      registerModeEoriMap.size shouldBe 6
    }
  }
  "RegisterWithEoriAndIdRequest Object" should {
    "create audit map" in {
      val registerWithEoriAndIdRequestMap = registerWithEoriAndIdRequestFromJson.keyValueMap()
      registerWithEoriAndIdRequestMap shouldBe Map(
        "receiptDate" -> "2001-12-17T09:30:47.000Z",
        "IDNumber" -> "11111111",
        "organisation.type" -> "0001",
        "isNameMatched" -> "false",
        "email" -> "johndoe@example.com",
        "regime" -> "CDS",
        "address.postalCode" -> "SW1A 2BQ",
        "fullName" -> "John Doe",
        "EORI" -> "GB012345678911",
        "address.city" -> "London",
        "organisation.name" -> "pg",
        "IDType" -> "UTR",
        "address.countryCode" -> "GB",
        "address.streetAndNumber" -> "100 Parliament Street",
        "acknowledgementReference" -> "2438490385338590358"
      )
      registerWithEoriAndIdRequestMap.size shouldBe 15
    }
  }

}
