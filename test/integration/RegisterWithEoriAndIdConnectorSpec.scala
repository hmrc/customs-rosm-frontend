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

package integration

import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.mvc.Http.Status.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR}
import uk.gov.hmrc.customs.rosmfrontend.connector.RegisterWithEoriAndIdConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.{Individual, RequestCommon, ResponseCommon}
import uk.gov.hmrc.customs.rosmfrontend.domain.{EstablishmentAddress, _}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import util.externalservices.ExternalServicesConfig._
import util.externalservices.{AuditService, RegisterWithEoriAndIdMessagingService}

class RegisterWithEoriAndIdConnectorSpec extends IntegrationTestsSpec with ScalaFutures {
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      Map(
        "microservice.services.customs-hods-proxy.host" -> Host,
        "microservice.services.customs-hods-proxy.port" -> Port,
        "microservice.services.customs-hods-proxy.register-with-eori-and-id.context" -> "register-with-eori-and-id",
        "auditing.enabled" -> true,
        "auditing.consumer.baseUri.host" -> Host,
        "auditing.consumer.baseUri.port" -> Port
      )
    )
    .build()

  private lazy val RegisterWithEoriAndIdConnector = app.injector.instanceOf[RegisterWithEoriAndIdConnector]
  val expectedPostUrl = "/register-with-eori-and-id"

  implicit val hc: HeaderCarrier = HeaderCarrier()

  before {
    resetMockServer()
    AuditService.stubAuditService()
  }

  override def beforeAll: Unit =
    startMockServer()

  override def afterAll: Unit =
    stopMockServer()

  private val requestDateInd = (new DateTime).withDate(2001, 12, 17).withTime(9, 30, 47, 0)

  val individualNinoRequest = RegisterWithEoriAndIdRequest(
    RequestCommon("CDS", requestDateInd, "012345678901234"),
    RegisterWithEoriAndIdDetail(
      registerModeEORI = RegisterModeEori(
        "ZZ123456789112",
        "John Doe",
        address = EstablishmentAddress("100 Parliament Street", "London", Some("SW1A 2BQ"), "GB")
      ),
      registerModeID = RegisterModeId(
        "NINO",
        "AB123456C",
        isNameMatched = true,
        individual = Some(Individual("John", None, "Doe", "1980-10-08"))
      ),
      Some(GovGatewayCredentials("john.doe@example.com"))
    )
  )

  private val individualNinoRequestJsonString =
    Json.parse("""
        |{
        |  "registerWithEORIAndIDRequest": {
        |    "requestCommon": {
        |      "regime":"CDS",
        |      "receiptDate": "2001-12-17T09:30:47Z",
        |      "acknowledgementReference": "012345678901234"
        |    },
        |    "requestDetail": {
        |      "registerModeEORI": {
        |        "EORI": "ZZ123456789112",
        |        "fullName": "John Doe",
        |        "address": {
        |          "streetAndNumber": "100 Parliament Street",
        |          "city": "London",
        |          "postalCode": "SW1A 2BQ",
        |          "countryCode": "GB"
        |        }
        |      },
        |      "registerModeID": {
        |        "IDType": "NINO",
        |        "IDNumber": "AB123456C",
        |        "isNameMatched": true,
        |        "individual": {
        |          "firstName": "John",
        |          "lastName": "Doe",
        |          "dateOfBirth": "1980-10-08"
        |        }
        |      },
        |      "govGatewayCredentials": {
        |        "email": "john.doe@example.com"
        |      }
        |    }
        |  }
        |}
      """.stripMargin).toString

  val individualNinoResponse = RegisterWithEoriAndIdResponseHolder(
    RegisterWithEoriAndIdResponse(
      ResponseCommon("OK", None, requestDateInd, None),
      Some(
        RegisterWithEoriAndIdResponseDetail(
          Some("PASS"),
          Some("C001"),
          responseData = Some(
            ResponseData(
              "XA1234567890123",
              Trader("John Doe", "Mr D"),
              EstablishmentAddress("Line 1", "City Name", Some("SW1A 2BQ"), "GB"),
              Some(
                ContactDetail(
                  EstablishmentAddress("Line 1", "City Name", Some("SW1A 2BQ"), "GB"),
                  "John Contact Doe",
                  Some("1234567"),
                  Some("89067"),
                  Some("john.doe@example.com")
                )
              ),
              VATIDs = Some(Seq(VatIds("AD", "1234"), VatIds("GB", "4567"))),
              hasInternetPublication = false,
              principalEconomicActivity = Some("P001"),
              hasEstablishmentInCustomsTerritory = Some(true),
              legalStatus = Some("Official"),
              thirdCountryIDNumber = Some(Seq("1234", "67890")),
              personType = Some(9),
              dateOfEstablishmentBirth = Some("2018-05-16"),
              startDate = "2018-05-15",
              expiryDate = Some("2018-05-16")
            )
          )
        )
      ),
      Some(AdditionalInformation(Nino("AB123456C"), true))
    )
  )

  private val requestDateOrg = (new DateTime).withDate(2000, 1, 1).withTime(0, 0, 0, 0)

  val organisationUtrRequest = RegisterWithEoriAndIdRequest(
    RequestCommon("CDS", requestDateOrg, "2438490385338590358"),
    RegisterWithEoriAndIdDetail(
      registerModeEORI = RegisterModeEori(
        "ZZ123456789112",
        "John Doe",
        address = EstablishmentAddress("Street", "City", Some("SW1A 2BQ"), "GB")
      ),
      registerModeID = RegisterModeId(
        "UTR",
        "1111111111",
        isNameMatched = false,
        individual = None,
        organisation = Some(RegisterWithEoriAndIdOrganisation("pg", "0001"))
      ),
      govGatewayCredentials = Some(GovGatewayCredentials("john.doe@example.com"))
    )
  )

  val organisationUtrRequestJsonString =
    Json.parse("""
        |{
        |  "registerWithEORIAndIDRequest": {
        |    "requestCommon": {
        |      "regime":"CDS",
        |      "receiptDate": "2000-01-01T00:00:00Z",
        |      "acknowledgementReference": "2438490385338590358"
        |    },
        |    "requestDetail": {
        |      "registerModeEORI": {
        |        "EORI": "ZZ123456789112",
        |        "fullName": "John Doe",
        |        "address": {
        |          "streetAndNumber": "Street",
        |          "city": "City",
        |          "postalCode": "SW1A 2BQ",
        |          "countryCode": "GB"
        |        }
        |      },
        |      "registerModeID": {
        |        "IDType": "UTR",
        |        "IDNumber": "1111111111",
        |        "isNameMatched": false,
        |        "organisation": {
        |          "name": "pg",
        |          "type": "0001"
        |        }
        |      },
        |      "govGatewayCredentials": {
        |        "email": "john.doe@example.com"
        |      }
        |    }
        |  }
        |}
      """.stripMargin).toString

  private val responseTime = (new DateTime).withDate(2018, 1, 16).withTime(9, 0, 0, 0)
  val organisationUtrResponse = RegisterWithEoriAndIdResponseHolder(
    RegisterWithEoriAndIdResponse(
      ResponseCommon("OK", None, responseTime, None),
      responseDetail = Some(RegisterWithEoriAndIdResponseDetail(Some("DEFERRED"), Some("a"))),
      additionalInformation = Some(AdditionalInformation(Utr("1111111111"), false))
    )
  )

  val serviceResponsePassJsonString =
    Json.parse("""
        |{
        |  "registerWithEORIAndIDResponse": {
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
        |          "fullName": "John Doe",
        |          "shortName": "Mr D"
        |        },
        |        "establishmentAddress": {
        |          "streetAndNumber": "Line 1",
        |          "city": "City Name",
        |          "postalCode": "SW1A 2BQ",
        |          "countryCode": "GB"
        |        },
        |        "contactDetail": {
        |          "address": {
        |            "streetAndNumber": "Line 1",
        |            "city": "City Name",
        |            "postalCode": "SW1A 2BQ",
        |            "countryCode": "GB"
        |          },
        |          "contactName": "John Contact Doe",
        |          "phone": "1234567",
        |          "fax": "89067",
        |          "email": "john.doe@example.com"
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
        |  }
        |}
      """.stripMargin).toString

  val serviceResponseDeferredJsonString =
    Json.parse("""
        |{
        |  "registerWithEORIAndIDResponse": {
        |    "responseCommon": {
        |      "status": "OK",
        |      "processingDate": "2018-01-16T09:00:00Z"
        |    },
        |    "responseDetail": {
        |      "outcome": "DEFERRED",
        |      "caseNumber": "a"
        |    }
        |  }
        |}
      """.stripMargin).toString

  "RegisterWithEoriAndIdConnector" should {
    "return successful response when matching service returns 200 for an Individual Nino request" in {

      RegisterWithEoriAndIdMessagingService.returnTheResponseWhenReceiveRequest(
        expectedPostUrl,
        individualNinoRequestJsonString,
        serviceResponsePassJsonString
      )

      await(RegisterWithEoriAndIdConnector.register(individualNinoRequest)) must be(
        individualNinoResponse.registerWithEORIAndIDResponse
      )
    }

    "return successful response when matching service returns 200 for an organisation UTR request" in {

      RegisterWithEoriAndIdMessagingService.returnTheResponseWhenReceiveRequest(
        expectedPostUrl,
        organisationUtrRequestJsonString,
        serviceResponseDeferredJsonString
      )

      await(RegisterWithEoriAndIdConnector.register(organisationUtrRequest)) must be(
        organisationUtrResponse.registerWithEORIAndIDResponse
      )
    }

    "fail when given a bad request" in {
      RegisterWithEoriAndIdMessagingService.returnTheResponseWhenReceiveRequest(
        expectedPostUrl,
        individualNinoRequestJsonString,
        serviceResponsePassJsonString,
        BAD_REQUEST
      )

      val caught = intercept[UpstreamErrorResponse] {
        await(RegisterWithEoriAndIdConnector.register(individualNinoRequest))
      }

      caught.statusCode mustBe BAD_REQUEST
      caught.getMessage must startWith("POST of ")
    }

    "fail for Internal Server Error" in {
      RegisterWithEoriAndIdMessagingService.returnTheResponseWhenReceiveRequest(
        expectedPostUrl,
        individualNinoRequestJsonString,
        serviceResponsePassJsonString,
        INTERNAL_SERVER_ERROR
      )

      val caught: UpstreamErrorResponse = intercept[UpstreamErrorResponse] {
        await(RegisterWithEoriAndIdConnector.register(individualNinoRequest))
      }

      caught.statusCode mustBe INTERNAL_SERVER_ERROR
      caught.getMessage must startWith("POST of ")
    }

    "fail when 4xx status code is recieved" in {
      RegisterWithEoriAndIdMessagingService.returnTheResponseWhenReceiveRequest(
        expectedPostUrl,
        individualNinoRequestJsonString,
        serviceResponsePassJsonString,
        FORBIDDEN
      )

      val caught: UpstreamErrorResponse = intercept[UpstreamErrorResponse] {
        await(RegisterWithEoriAndIdConnector.register(individualNinoRequest))
      }

      caught.statusCode mustBe FORBIDDEN
      caught.getMessage must startWith("POST of ")
    }

    "audit a successful request" in {
      RegisterWithEoriAndIdMessagingService.returnTheResponseWhenReceiveRequest(
        expectedPostUrl,
        individualNinoRequestJsonString,
        serviceResponsePassJsonString
      )

      await(RegisterWithEoriAndIdConnector.register(individualNinoRequest))

      eventually(AuditService.verifyXAuditWrite(2))
    }
    "not audit a failed request" in {
      RegisterWithEoriAndIdMessagingService.returnTheResponseWhenReceiveRequest(
        expectedPostUrl,
        individualNinoRequestJsonString,
        serviceResponsePassJsonString,
        BAD_REQUEST
      )

      val caught = intercept[UpstreamErrorResponse] {
        await(RegisterWithEoriAndIdConnector.register(individualNinoRequest))
      }

      caught.statusCode mustBe BAD_REQUEST
      eventually(AuditService.verifyXAuditWrite(1))
    }
  }
}
