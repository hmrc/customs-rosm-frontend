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

package unit.services.registration

import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.customs.rosmfrontend.connector.RegisterWithEoriAndIdConnector
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging._
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.customs.rosmfrontend.services.RequestCommonGenerator
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.mapping.OrganisationTypeConfiguration
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegisterWithEoriAndIdService
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec
import scala.concurrent.Future

class RegisterWithEoriAndIdServiceSpec extends UnitSpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach {
  private val mockConnector = mock[RegisterWithEoriAndIdConnector]
  private val mockReqCommonGen = mock[RequestCommonGenerator]
  private val mockRequestCommon = mock[RequestCommon]
  private val mockDataCache = mock[SessionCache]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val validDate = "2016-07-08T08:35:13Z"
  private val validDateTime = DateTime.parse(validDate)
  implicit val hc: HeaderCarrier = mock[HeaderCarrier]

  private val loggedInUserId = java.util.UUID.randomUUID.toString
  private val mockLoggedInUser = mock[LoggedInUserWithEnrolments]

  val service =
    new RegisterWithEoriAndIdService(mockConnector, mockReqCommonGen, mockDataCache, mockRequestSessionData)

  private val organisationNameAndAddress =
    EoriAndIdNameAndAddress("Full Name", EstablishmentAddress("25 Some Street", "Testville", Some("AB99 3XZ"), "GB"))
  private val organisationNameAndAddressNonePostcode =
    EoriAndIdNameAndAddress("Full Name", EstablishmentAddress("25 Some Street", "Testville", None, "GB"))

  private val organisationDetails = RegisterWithEoriAndIdDetail(
    RegisterModeEori("ZZ123456789112", "Full Name", organisationNameAndAddress.address),
    RegisterModeId(
      "UTR",
      "11111111",
      isNameMatched = false,
      individual = None,
      Some(RegisterWithEoriAndIdOrganisation("OrgName", "OrgTypeCode"))
    ),
    Some(GovGatewayCredentials("some@example.com"))
  )

  private val individualDetailsWithNoDob = RegisterWithEoriAndIdDetail(
    RegisterModeEori("GB1234567891234", "Full Name", organisationNameAndAddress.address),
    RegisterModeId(
      "NINO",
      "AB123456D",
      isNameMatched = true,
      individual = Some(Individual("FirstName", None, "MiddleName", ""))
    ),
    Some(GovGatewayCredentials("some@example.com"))
  )

  private val subscriptionDetails = SubscriptionDetails(dateEstablished = Some(LocalDate.parse("1978-02-10")))
  private val subscriptionDetailsForIndividual = SubscriptionDetails(
    nameDobDetails = Some(NameDobMatchModel("FirstName", None, "LastName", LocalDate.parse("1999-02-11")))
  )
  private val personTypeCompany = Some(OrganisationTypeConfiguration.Company)
  private val personTypeIndividual = Some(OrganisationTypeConfiguration.Individual)

  private val organisationUtrDetailsNonePostCode = RegisterWithEoriAndIdDetail(
    RegisterModeEori("ZZ123456789112", "Full Name", organisationNameAndAddressNonePostcode.address),
    RegisterModeId(
      "UTR",
      "11111111",
      isNameMatched = false,
      individual = None,
      Some(RegisterWithEoriAndIdOrganisation("OrgName", "OrgTypeCode"))
    ),
    Some(GovGatewayCredentials("some@example.com"))
  )

  private val individualDetails = RegisterWithEoriAndIdDetail(
    RegisterModeEori("GB1234567891234", "Full Name", organisationNameAndAddress.address),
    RegisterModeId(
      "NINO",
      "AB123456D",
      isNameMatched = true,
      individual = Some(Individual("FirstName", None, "MiddleName", "DOB"))
    ),
    Some(GovGatewayCredentials("some@example.com"))
  )

  private val registrationResponse = RegisterWithEoriAndIdResponseHolder(
    RegisterWithEoriAndIdResponse(
      ResponseCommon("status", Some("status text"), validDateTime, Some(List(MessagingServiceParam("name", "value")))),
      responseDetail = Some(
        RegisterWithEoriAndIdResponseDetail(
          Some("OutCome"),
          Some("CaseNumber"),
          responseData = Some(
            ResponseData(
              "SOMESAFEID",
              Trader("Full Name", "Mr Name"),
              organisationNameAndAddress.address,
              None,
              None,
              false,
              None,
              None,
              None,
              None,
              Some("1978-02-10"),
              Some(2),
              "2018-01-01",
              None
            )
          )
        )
      )
    )
  )

  private val registrationResponseNoSafeId =
    RegisterWithEoriAndIdResponseHolder(
      RegisterWithEoriAndIdResponse(
        ResponseCommon(
          "status",
          Some("status text"),
          validDateTime,
          Some(List(MessagingServiceParam("name", "value")))
        ),
        responseDetail = Some(
          RegisterWithEoriAndIdResponseDetail(
            Some("OutCome"),
            Some("CaseNumber"),
            responseData = Some(
              ResponseData(
                "",
                Trader("Full Name", "Mr Name"),
                organisationNameAndAddress.address,
                None,
                None,
                false,
                None,
                None,
                None,
                None,
                Some("1978-02-10"),
                Some(2),
                "2018-01-01",
                None
              )
            )
          )
        )
      )
    )

  private val registrationWithNoDoeAndNoPersonType =
    RegisterWithEoriAndIdResponseHolder(
      RegisterWithEoriAndIdResponse(
        ResponseCommon(
          "status",
          Some("status text"),
          validDateTime,
          Some(List(MessagingServiceParam("name", "value")))
        ),
        responseDetail = Some(
          RegisterWithEoriAndIdResponseDetail(
            Some("OutCome"),
            Some("CaseNumber"),
            responseData = Some(
              ResponseData(
                "SOMESAFEID",
                Trader("Full Name", "Mr Name"),
                organisationNameAndAddress.address,
                None,
                None,
                false,
                None,
                None,
                None,
                None,
                None,
                None,
                "2018-01-01",
                None
              )
            )
          )
        )
      )
    )

  private val registrationResponseWithDoeAndPersonType =
    RegisterWithEoriAndIdResponseHolder(
      RegisterWithEoriAndIdResponse(
        ResponseCommon(
          "status",
          Some("status text"),
          validDateTime,
          Some(List(MessagingServiceParam("name", "value")))
        ),
        responseDetail = Some(
          RegisterWithEoriAndIdResponseDetail(
            Some("OutCome"),
            Some("CaseNumber"),
            responseData = Some(
              ResponseData(
                "SOMESAFEID",
                Trader("Full Name", "Mr Name"),
                organisationNameAndAddress.address,
                None,
                None,
                false,
                None,
                None,
                None,
                None,
                Some("1978-02-10"),
                Some(2),
                "2018-01-01",
                None
              )
            )
          )
        )
      )
    )

  private val registrationWithDoe = RegisterWithEoriAndIdResponseHolder(
    RegisterWithEoriAndIdResponse(
      ResponseCommon("status", Some("status text"), validDateTime, Some(List(MessagingServiceParam("name", "value")))),
      responseDetail = Some(
        RegisterWithEoriAndIdResponseDetail(
          Some("OutCome"),
          Some("CaseNumber"),
          responseData = Some(
            ResponseData(
              "SOMESAFEID",
              Trader("Full Name", "Mr Name"),
              organisationNameAndAddress.address,
              None,
              None,
              false,
              None,
              None,
              None,
              None,
              Some("1988-02-10"),
              Some(2),
              "2018-01-01",
              None
            )
          )
        )
      )
    )
  )

  private val registrationResponseWithDate =
    RegisterWithEoriAndIdResponseHolder(
      RegisterWithEoriAndIdResponse(
        ResponseCommon(
          "status",
          Some("status text"),
          validDateTime,
          Some(List(MessagingServiceParam("name", "value")))
        ),
        responseDetail = Some(
          RegisterWithEoriAndIdResponseDetail(
            Some("OutCome"),
            Some("CaseNumber"),
            responseData = Some(
              ResponseData(
                "SOMESAFEID",
                Trader("Full Name", "Mr Name"),
                organisationNameAndAddress.address,
                None,
                None,
                false,
                None,
                None,
                None,
                None,
                Some("1988-02-10"),
                Some(2),
                "2018-01-01",
                None
              )
            )
          )
        )
      )
    )

  private val registrationWithNoRespData = RegisterWithEoriAndIdResponseHolder(
    RegisterWithEoriAndIdResponse(
      ResponseCommon("status", Some("status text"), validDateTime, Some(List(MessagingServiceParam("name", "value")))),
      responseDetail =
        Some(RegisterWithEoriAndIdResponseDetail(Some("OutCome"), Some("CaseNumber"), responseData = None))
    )
  )

  private val registrationWithNoDobRequest =
    RegisterWithEoriAndIdResponseHolder(
      RegisterWithEoriAndIdResponse(
        ResponseCommon(
          "status",
          Some("status text"),
          validDateTime,
          Some(List(MessagingServiceParam("name", "value")))
        ),
        responseDetail = Some(
          RegisterWithEoriAndIdResponseDetail(
            Some("OutCome"),
            Some("CaseNumber"),
            responseData = Some(
              ResponseData(
                "SOMESAFEID",
                Trader("Full Name", "Mr Name"),
                organisationNameAndAddress.address,
                None,
                None,
                false,
                None,
                None,
                None,
                None,
                None,
                None,
                "2018-01-01",
                None
              )
            )
          )
        )
      )
    )

  private val registrationResponseWithDob = RegisterWithEoriAndIdResponseHolder(
    RegisterWithEoriAndIdResponse(
      ResponseCommon("status", Some("status text"), validDateTime, Some(List(MessagingServiceParam("name", "value")))),
      responseDetail = Some(
        RegisterWithEoriAndIdResponseDetail(
          Some("OutCome"),
          Some("CaseNumber"),
          responseData = Some(
            ResponseData(
              "SOMESAFEID",
              Trader("Full Name", "Mr Name"),
              organisationNameAndAddress.address,
              None,
              None,
              false,
              None,
              None,
              None,
              None,
              Some("1999-02-11"),
              Some(1),
              "2018-01-01",
              None
            )
          )
        )
      )
    )
  )

  private val registrationWithNoDobWithPTRequest =
    RegisterWithEoriAndIdResponseHolder(
      RegisterWithEoriAndIdResponse(
        ResponseCommon(
          "status",
          Some("status text"),
          validDateTime,
          Some(List(MessagingServiceParam("name", "value")))
        ),
        responseDetail = Some(
          RegisterWithEoriAndIdResponseDetail(
            Some("OutCome"),
            Some("CaseNumber"),
            responseData = Some(
              ResponseData(
                "SOMESAFEID",
                Trader("Full Name", "Mr Name"),
                organisationNameAndAddress.address,
                None,
                None,
                false,
                None,
                None,
                None,
                None,
                None,
                Some(1),
                "2018-01-01",
                None
              )
            )
          )
        )
      )
    )

  private val registrationResponseWithDobAndPT =
    RegisterWithEoriAndIdResponseHolder(
      RegisterWithEoriAndIdResponse(
        ResponseCommon(
          "status",
          Some("status text"),
          validDateTime,
          Some(List(MessagingServiceParam("name", "value")))
        ),
        responseDetail = Some(
          RegisterWithEoriAndIdResponseDetail(
            Some("OutCome"),
            Some("CaseNumber"),
            responseData = Some(
              ResponseData(
                "SOMESAFEID",
                Trader("Full Name", "Mr Name"),
                organisationNameAndAddress.address,
                None,
                None,
                false,
                None,
                None,
                None,
                None,
                Some("1999-02-11"),
                Some(1),
                "2018-01-01",
                None
              )
            )
          )
        )
      )
    )

  private val registrationWithDobAndNoPTRequest =
    RegisterWithEoriAndIdResponseHolder(
      RegisterWithEoriAndIdResponse(
        ResponseCommon(
          "status",
          Some("status text"),
          validDateTime,
          Some(List(MessagingServiceParam("name", "value")))
        ),
        responseDetail = Some(
          RegisterWithEoriAndIdResponseDetail(
            Some("OutCome"),
            Some("CaseNumber"),
            responseData = Some(
              ResponseData(
                "SOMESAFEID",
                Trader("Full Name", "Mr Name"),
                organisationNameAndAddress.address,
                None,
                None,
                false,
                None,
                None,
                None,
                None,
                Some("1999-02-11"),
                None,
                "2018-01-01",
                None
              )
            )
          )
        )
      )
    )

  private val registrationWithNoDoeAndPT = RegisterWithEoriAndIdResponseHolder(
    RegisterWithEoriAndIdResponse(
      ResponseCommon("status", Some("status text"), validDateTime, Some(List(MessagingServiceParam("name", "value")))),
      responseDetail = Some(
        RegisterWithEoriAndIdResponseDetail(
          Some("OutCome"),
          Some("CaseNumber"),
          responseData = Some(
            ResponseData(
              "SOMESAFEID",
              Trader("Full Name", "Mr Name"),
              organisationNameAndAddress.address,
              None,
              None,
              false,
              None,
              None,
              None,
              None,
              None,
              Some(2),
              "2018-01-01",
              None
            )
          )
        )
      )
    )
  )

  private val organisationRegistrationDetails = RegistrationDetails.individual(
    "SapNumber",
    SafeId("safe-id"),
    "Name",
    Address("LineOne", None, None, None, postalCode = Some("Postcode"), countryCode = "GB"),
    dateOfBirth = LocalDate.parse("2018-01-01"),
    customsId = Some(Utr("someId"))
  )

  override protected def beforeEach(): Unit = {
    reset(mockConnector, mockDataCache)
    when(mockLoggedInUser.userId()).thenReturn(loggedInUserId)
    when(mockReqCommonGen.generate()).thenReturn(mockRequestCommon)
    when(mockDataCache.saveRegisterWithEoriAndIdResponse(any[RegisterWithEoriAndIdResponse])(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))
  }

  private val expectedException = new RuntimeException("Something bad happened")

  private def mockRegistrationFailure() =
    when(
      mockConnector
        .register(any[RegisterWithEoriAndIdRequest])(any[HeaderCarrier])
    ) thenReturn Future.failed(expectedException)

  private def mockRegistrationSuccess() =
    when(
      mockConnector
        .register(any[RegisterWithEoriAndIdRequest])(any[HeaderCarrier])
    ) thenReturn Future.successful(registrationResponse.registerWithEORIAndIDResponse)

  "RegisterWithEoriAndIdService" should {

    "send correct request for an organisation" in {
      mockRegistrationSuccess()

      service
        .registerWithEoriAndId(organisationDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)
        .futureValue shouldBe true

      val captor =
        ArgumentCaptor.forClass(classOf[RegisterWithEoriAndIdRequest])
      verify(mockConnector).register(captor.capture())(meq(hc))

      val registrationRequest: RegisterWithEoriAndIdRequest = captor.getValue

      registrationRequest.requestCommon shouldBe mockRequestCommon
      registrationRequest.requestDetail.registerModeEORI.EORI shouldBe "ZZ123456789112"
      registrationRequest.requestDetail.registerModeEORI.fullName shouldBe "Full Name"
      registrationRequest.requestDetail.registerModeEORI.address shouldBe EstablishmentAddress(
        "25 Some Street",
        "Testville",
        Some("AB99 3XZ"),
        "GB"
      )
      registrationRequest.requestDetail.registerModeID.IDType shouldBe "UTR"
      registrationRequest.requestDetail.registerModeID.IDNumber shouldBe "11111111"
      registrationRequest.requestDetail.registerModeID.isNameMatched shouldBe false
      registrationRequest.requestDetail.registerModeID.individual shouldBe None
      registrationRequest.requestDetail.registerModeID.organisation shouldBe Some(
        RegisterWithEoriAndIdOrganisation("OrgName", "OrgTypeCode")
      )
      registrationRequest.requestDetail.govGatewayCredentials shouldBe Some(GovGatewayCredentials("some@example.com"))
    }

    "send correct request for an organisation when the postcode is None" in {
      mockRegistrationSuccess()

      await(
        service.registerWithEoriAndId(organisationUtrDetailsNonePostCode, subscriptionDetails, personTypeCompany)(
          hc,
          mockLoggedInUser
        )
      ) shouldBe true

      val captor =
        ArgumentCaptor.forClass(classOf[RegisterWithEoriAndIdRequest])
      verify(mockConnector).register(captor.capture())(meq(hc))

      val registrationRequest: RegisterWithEoriAndIdRequest = captor.getValue

      registrationRequest.requestDetail.registerModeEORI.address.postalCode shouldBe None
    }

    "send request with K removed from UTR" in {
      mockRegistrationSuccess()

      val orgDetails = RegisterWithEoriAndIdDetail(
        RegisterModeEori("ZZ123456789112", "Full Name", organisationNameAndAddress.address),
        RegisterModeId(
          "UTR",
          "1111111111K",
          isNameMatched = false,
          individual = None,
          Some(RegisterWithEoriAndIdOrganisation("OrgName", "OrgTypeCode"))
        ),
        Some(GovGatewayCredentials("some@example.com"))
      )

      await(service.registerWithEoriAndId(orgDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)) shouldBe true

      val captor =
        ArgumentCaptor.forClass(classOf[RegisterWithEoriAndIdRequest])
      verify(mockConnector).register(captor.capture())(meq(hc))

      val registrationRequest: RegisterWithEoriAndIdRequest = captor.getValue

      registrationRequest.requestDetail.registerModeID.IDNumber shouldBe "1111111111"
    }

    "send request with k removed from UTR" in {
      mockRegistrationSuccess()

      val orgDetails = RegisterWithEoriAndIdDetail(
        RegisterModeEori("ZZ123456789112", "Full Name", organisationNameAndAddress.address),
        RegisterModeId(
          "UTR",
          "1111111111k",
          isNameMatched = false,
          individual = None,
          Some(RegisterWithEoriAndIdOrganisation("OrgName", "OrgTypeCode"))
        ),
        Some(GovGatewayCredentials("some@example.com"))
      )

      await(service.registerWithEoriAndId(orgDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)) shouldBe true

      val captor =
        ArgumentCaptor.forClass(classOf[RegisterWithEoriAndIdRequest])
      verify(mockConnector).register(captor.capture())(meq(hc))

      val registrationRequest: RegisterWithEoriAndIdRequest = captor.getValue

      registrationRequest.requestDetail.registerModeID.IDNumber shouldBe "1111111111"
    }

    "send request should not remove K from NINO" in {
      mockRegistrationSuccess()

      val orgDetails = RegisterWithEoriAndIdDetail(
        RegisterModeEori("ZZ123456789112", "Full Name", organisationNameAndAddress.address),
        RegisterModeId(
          "NINO",
          "AB123456C",
          isNameMatched = false,
          individual = None,
          Some(RegisterWithEoriAndIdOrganisation("OrgName", "OrgTypeCode"))
        ),
        Some(GovGatewayCredentials("some@example.com"))
      )

      await(service.registerWithEoriAndId(orgDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)) shouldBe true

      val captor =
        ArgumentCaptor.forClass(classOf[RegisterWithEoriAndIdRequest])
      verify(mockConnector).register(captor.capture())(meq(hc))

      val registrationRequest: RegisterWithEoriAndIdRequest = captor.getValue

      registrationRequest.requestDetail.registerModeID.IDNumber shouldBe "AB123456C"
    }

    "throw an exception when request for an organisation fails" in {
      mockRegistrationFailure()

      val caught = intercept[RuntimeException](
        await(
          service
            .registerWithEoriAndId(organisationDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)
        )
      )

      caught shouldBe expectedException
    }

    "store response details in cache and DB" in {
      mockRegistrationSuccess()

      await(
        service.registerWithEoriAndId(organisationDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)
      ) shouldBe true

      verify(mockDataCache).saveRegisterWithEoriAndIdResponse(meq(registrationResponse.registerWithEORIAndIDResponse))(
        meq(hc)
      )
    }

    "store response in cache when there is no SAFEID" in {
      when(
        mockConnector
          .register(any[RegisterWithEoriAndIdRequest])(any[HeaderCarrier])
      ) thenReturn Future.successful(registrationResponseNoSafeId.registerWithEORIAndIDResponse)

      await(
        service.registerWithEoriAndId(organisationDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)
      ) shouldBe true

      verify(mockDataCache).saveRegisterWithEoriAndIdResponse(
        meq(registrationResponseNoSafeId.registerWithEORIAndIDResponse)
      )(meq(hc))
    }

    "Date of establishment and PersonType are not returned from Register with Eori and Id then Date of establishment and PersonType stored in cache is used" in {
      when(
        mockConnector
          .register(any[RegisterWithEoriAndIdRequest])(any[HeaderCarrier])
      ) thenReturn Future.successful(registrationWithNoDoeAndNoPersonType.registerWithEORIAndIDResponse)
      await(
        service.registerWithEoriAndId(organisationDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)
      ) shouldBe true

      verify(mockDataCache).saveRegisterWithEoriAndIdResponse(
        meq(registrationResponseWithDoeAndPersonType.registerWithEORIAndIDResponse)
      )(meq(hc))
    }

    "Date of establishment is returned from Register with Eori and Id then the returned Date of establishment is used" in {
      when(
        mockConnector
          .register(any[RegisterWithEoriAndIdRequest])(any[HeaderCarrier])
      ) thenReturn Future.successful(registrationWithDoe.registerWithEORIAndIDResponse)
      await(
        service.registerWithEoriAndId(organisationDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)
      ) shouldBe true

      verify(mockDataCache).saveRegisterWithEoriAndIdResponse(
        meq(registrationResponseWithDate.registerWithEORIAndIDResponse)
      )(meq(hc))
    }

    "Store response is cache when Response Data is returned as None " in {
      when(
        mockConnector
          .register(any[RegisterWithEoriAndIdRequest])(any[HeaderCarrier])
      ) thenReturn Future.successful(registrationWithNoRespData.registerWithEORIAndIDResponse)
      await(
        service.registerWithEoriAndId(organisationDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)
      ) shouldBe true

      verify(mockDataCache).saveRegisterWithEoriAndIdResponse(
        meq(registrationWithNoRespData.registerWithEORIAndIDResponse)
      )(meq(hc))
    }

    "Date of establishment is not returned from Register with Eori and Id then Date of Birth stored in cache is used" in {
      when(
        mockConnector
          .register(any[RegisterWithEoriAndIdRequest])(any[HeaderCarrier])
      ) thenReturn Future.successful(registrationWithNoDobRequest.registerWithEORIAndIDResponse)
      await(
        service.registerWithEoriAndId(
          individualDetailsWithNoDob,
          subscriptionDetailsForIndividual,
          personTypeIndividual
        )(hc, mockLoggedInUser)
      ) shouldBe true

      verify(mockDataCache).saveRegisterWithEoriAndIdResponse(
        meq(registrationResponseWithDob.registerWithEORIAndIDResponse)
      )(meq(hc))
    }

    "Date of establishment is not returned from Register with Eori and Id but personType is returned for individual" in {
      when(
        mockConnector
          .register(any[RegisterWithEoriAndIdRequest])(any[HeaderCarrier])
      ) thenReturn Future.successful(registrationWithNoDobWithPTRequest.registerWithEORIAndIDResponse)
      await(
        service.registerWithEoriAndId(
          individualDetailsWithNoDob,
          subscriptionDetailsForIndividual,
          personTypeIndividual
        )(hc, mockLoggedInUser)
      ) shouldBe true

      verify(mockDataCache).saveRegisterWithEoriAndIdResponse(
        meq(registrationResponseWithDobAndPT.registerWithEORIAndIDResponse)
      )(meq(hc))
    }

    "Date of establishment is not returned from Register with Eori and Id but personType is returned for organisation" in {
      when(
        mockConnector
          .register(any[RegisterWithEoriAndIdRequest])(any[HeaderCarrier])
      ) thenReturn Future.successful(registrationWithNoDoeAndPT.registerWithEORIAndIDResponse)
      await(
        service.registerWithEoriAndId(organisationDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)
      ) shouldBe true

      verify(mockDataCache).saveRegisterWithEoriAndIdResponse(
        meq(registrationResponseWithDoeAndPersonType.registerWithEORIAndIDResponse)
      )(meq(hc))
    }

    "Date of establishment returned from Register with Eori and Id but personType is not returned then personType stored in cache is used" in {
      when(
        mockConnector
          .register(any[RegisterWithEoriAndIdRequest])(any[HeaderCarrier])
      ) thenReturn Future.successful(registrationWithDobAndNoPTRequest.registerWithEORIAndIDResponse)
      await(
        service.registerWithEoriAndId(
          individualDetailsWithNoDob,
          subscriptionDetailsForIndividual,
          personTypeIndividual
        )(hc, mockLoggedInUser)
      ) shouldBe true

      verify(mockDataCache).saveRegisterWithEoriAndIdResponse(
        meq(registrationResponseWithDobAndPT.registerWithEORIAndIDResponse)
      )(meq(hc))
    }

    "not proceed/return until organisation details are stored in cache" in {
      mockRegistrationSuccess()

      when(mockDataCache.saveRegisterWithEoriAndIdResponse(any[RegisterWithEoriAndIdResponse])(any[HeaderCarrier]))
        .thenReturn(Future.failed(expectedException))

      val caught = intercept[RuntimeException] {
        await(
          service
            .registerWithEoriAndId(organisationDetails, subscriptionDetails, personTypeCompany)(hc, mockLoggedInUser)
        )
      }
      caught shouldBe expectedException
    }

    "send correct request for an individual" in {
      mockRegistrationSuccess()

      await(
        service
          .registerWithEoriAndId(individualDetails, subscriptionDetails, personTypeIndividual)(hc, mockLoggedInUser)
      ) shouldBe true

      val captor =
        ArgumentCaptor.forClass(classOf[RegisterWithEoriAndIdRequest])
      verify(mockConnector).register(captor.capture())(meq(hc))

      val registrationRequest: RegisterWithEoriAndIdRequest = captor.getValue

      registrationRequest.requestDetail.registerModeID.individual shouldBe Some(
        Individual("FirstName", None, "MiddleName", "DOB")
      )
    }

    "determine correct request for an individual" in {
      val cachedAddressViewModel =
        Some(AddressViewModel("Address Line 1", "city", Some("postcode"), "GB"))
      val eori = Some("EORINUMBERXXXXXXX")
      val dob = Some(LocalDate.parse("1976-04-13"))
      val nameDobDetails = Some(NameDobMatchModel("FName", None, "LastName", LocalDate.parse("1976-04-13")))
      val nino = Some(Nino("NINO1234"))

      val subscriptionDetailsHolder = mock[SubscriptionDetails]

      when(mockDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(mockDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.Company))
      when(mockRequestSessionData.selectedUserLocation(any()))
        .thenReturn(Some(UserLocation.Uk))

      when(subscriptionDetailsHolder.addressDetails)
        .thenReturn(cachedAddressViewModel)
      when(subscriptionDetailsHolder.eoriNumber).thenReturn(eori)
      when(subscriptionDetailsHolder.dateOfBirth).thenReturn(dob)
      when(subscriptionDetailsHolder.nameDobDetails).thenReturn(nameDobDetails)
      when(subscriptionDetailsHolder.customsId).thenReturn(nino)

      mockRegistrationSuccess()

      service
        .sendIndividualRequest(any(), mockLoggedInUser, hc)
        .futureValue shouldBe true
    }

    "determine correct request for an RegistrationDetailsOrganisation" in {
      val mockRegistrationDetailsOrganisation =
        mock[RegistrationDetailsOrganisation]
      val mayBeCachedAddressViewModel =
        Some(AddressViewModel("Address Line 1", "city", Some("postcode"), "GB"))
      val mayBeEori = Some("EORINUMBERXXXXXXX")
      val nameIdOrganisationDetails =
        NameIdOrganisationMatchModel("test", "1111111111k")
      val mockSubscriptionDetailsHolder = mock[SubscriptionDetails]
      when(mockDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscriptionDetailsHolder))
      when(mockDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockRegistrationDetailsOrganisation))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.Company))
      when(mockRequestSessionData.selectedUserLocation(any()))
        .thenReturn(Some(UserLocation.Uk))
      when(mockSubscriptionDetailsHolder.addressDetails)
        .thenReturn(mayBeCachedAddressViewModel)
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(mayBeEori)
      when(mockSubscriptionDetailsHolder.nameIdOrganisationDetails)
        .thenReturn(Some(nameIdOrganisationDetails))
      mockRegistrationSuccess()

      service
        .sendOrganisationRequest(any(), mockLoggedInUser, hc)
        .futureValue shouldBe true
    }

    "send correct request for RegistrationDetailsOrganisation" in {
      val mayBeCachedAddressViewModel =
        Some(AddressViewModel("Address Line 1", "city", Some("postcode"), "GB"))
      val mayBeEori = Some("EORINUMBERXXXXXXX")
      val nameIdOrganisationDetails =
        Some(NameIdOrganisationMatchModel("test", "1111111111k"))

      val mockSubscriptionDetailsHolder = mock[SubscriptionDetails]
      when(mockDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscriptionDetailsHolder))
      when(mockDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.Company))

      when(mockSubscriptionDetailsHolder.addressDetails)
        .thenReturn(mayBeCachedAddressViewModel)
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(mayBeEori)
      when(mockSubscriptionDetailsHolder.nameIdOrganisationDetails)
        .thenReturn(nameIdOrganisationDetails)
      mockRegistrationSuccess()

      await(service.sendOrganisationRequest(any(), mockLoggedInUser, hc)) shouldBe true

    }

    "send correct request for IndividualRequest" in {
      val address =
        Some(AddressViewModel("Address Line 1", "city", Some("postcode"), "GB"))
      val eori = Some("EORINUMBERXXXXXXX")
      val nino = Some(Nino("NINO1234"))

      val mockSubscription = mock[SubscriptionDetails]
      when(mockDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscription))
      when(mockDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.Company))
      when(mockSubscription.addressDetails).thenReturn(address)
      when(mockSubscription.eoriNumber).thenReturn(eori)
      when(mockSubscription.customsId).thenReturn(nino)
      when(mockSubscription.nameDobDetails)
        .thenReturn(Some(NameDobMatchModel("Fname", None, "Lname", LocalDate.parse("1978-02-10"))))
      mockRegistrationSuccess()

      await(service.sendIndividualRequest(any(), mockLoggedInUser, hc)) shouldBe true

    }
  }
}
