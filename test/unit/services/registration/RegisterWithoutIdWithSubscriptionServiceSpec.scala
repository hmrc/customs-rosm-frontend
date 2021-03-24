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
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.mvc.{Action, AnyContent, Request, Results}
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionCreateController
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.ResponseCommon._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging._
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.ContactDetailsModel
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.customs.rosmfrontend.services.registration.{
  RegisterWithoutIdService,
  RegisterWithoutIdWithSubscriptionService
}
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec

import scala.concurrent.Future

class RegisterWithoutIdWithSubscriptionServiceSpec
    extends UnitSpec with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach {
  private val mockRegisterWithoutIdService = mock[RegisterWithoutIdService]
  private val mockSessionCache = mock[SessionCache]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSubscriptionCreateController = mock[SubscriptionCreateController]
  private val mockOrgTypeLookup = mock[OrgTypeLookup]
  private val mockRegistrationDetails = mock[RegistrationDetails]

  private implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  private implicit val rq: Request[AnyContent] = mock[Request[AnyContent]]

  private val loggedInUserId = java.util.UUID.randomUUID.toString
  private val mockLoggedInUser = mock[LoggedInUserWithEnrolments]
  private val emulatedFailure = new RuntimeException("something bad happened")

  private val okResponse = RegisterWithoutIDResponse(
    ResponseCommon(StatusOK, Some("All OK"), DateTime.now()),
    Some(RegisterWithoutIdResponseDetail("TestSafeId", None))
  )
  private val notOKResponse = RegisterWithoutIDResponse(
    ResponseCommon(StatusNotOK, Some("Something went wrong"), DateTime.now()),
    Some(RegisterWithoutIdResponseDetail("TestSafeId", None))
  )
  private val contactDetails =
    ContactDetailsModel("John Doe", "john@doe.com", "01632961234private ", None, Some(true), None, None, None, None)

  private val service = new RegisterWithoutIdWithSubscriptionService(
    mockRegisterWithoutIdService,
    mockSessionCache,
    mockRequestSessionData,
    mockOrgTypeLookup,
    mockSubscriptionCreateController
  )

  override protected def beforeAll(): Unit =
    when(mockLoggedInUser.userId()).thenReturn(loggedInUserId)

  override protected def beforeEach(): Unit = {
    reset(
      mockRegisterWithoutIdService,
      mockSessionCache,
      mockRequestSessionData,
      mockOrgTypeLookup,
      mockSubscriptionCreateController,
      mockRegistrationDetails
    )
    when(mockSessionCache.saveRegistrationDetails(any[RegistrationDetails])(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))
    mockSessionCacheRegistrationDetails()
    when(mockRegistrationDetails.safeId).thenReturn(SafeId(""))
  }

  private def mockRegisterWithoutIdOKResponse() = {
    when(
      mockRegisterWithoutIdService.registerOrganisation(
        anyString(),
        any[Address],
        any[Option[ContactDetailsModel]],
        any[LoggedInUser],
        any(),
        any[Option[CdsOrganisationType]]
      )(any[HeaderCarrier])
    ).thenReturn(Future.successful(okResponse), Nil: _*)
    when(mockRegisterWithoutIdService.registerIndividual(any(), any(), any(), any(), any(), any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(okResponse), Nil: _*)
  }

  private def mockRegisterWithoutIdNotOKResponse() = {
    when(
      mockRegisterWithoutIdService.registerOrganisation(
        anyString(),
        any[Address],
        any[Option[ContactDetailsModel]],
        any[LoggedInUser],
        any(),
        any[Option[CdsOrganisationType]]
      )(any[HeaderCarrier])
    ).thenReturn(Future.successful(notOKResponse), Nil: _*)
    when(mockRegisterWithoutIdService.registerIndividual(any(), any(), any(), any(), any(), any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(notOKResponse), Nil: _*)
  }

  private def mockRegisterWithoutIdFailure() = {
    when(
      mockRegisterWithoutIdService.registerOrganisation(
        anyString(),
        any[Address],
        any[Option[ContactDetailsModel]],
        any[LoggedInUser],
        any(),
        any[Option[CdsOrganisationType]]
      )(any[HeaderCarrier])
    ).thenReturn(Future.failed(emulatedFailure))
    when(mockRegisterWithoutIdService.registerIndividual(any(), any(), any(), any(), any(), any())(any[HeaderCarrier]))
      .thenReturn(Future.failed(emulatedFailure))
  }

  private def mockSessionCacheRegistrationDetails() = {
    when(mockSessionCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockRegistrationDetails))
    when(mockRegistrationDetails.name).thenReturn("orgName")
    when(mockRegistrationDetails.address)
      .thenReturn(Address("add1", Some("add2"), Some("add3"), Some("add4"), Some("postcode"), "country"))
  }

  private def mockSessionCacheSubscriptionDetails() =
    when(mockSessionCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(
      Future.successful(
        SubscriptionDetails(
          nameDobDetails =
            Some(NameDobMatchModel("firstName", Some("middleName"), "lastName", new LocalDate(1980, 3, 31))),
          contactDetails = Some(contactDetails)
        )
      )
    )

  "RegisterWithoutIdWithSubscriptionService" should {

    "when UK, call subscriptionCreate, do not call registerOrganisation or registerIndividual" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(CorporateBody))
      mockRegisterWithoutIdOKResponse()
      mockSubscriptionCreateControllerCall()

      await(service.rowRegisterWithoutIdWithSubscription(mockLoggedInUser, Journey.GetYourEORI)(hc, rq))

      verify(mockSubscriptionCreateController, times(1)).subscribe(any())
      verify(mockRegisterWithoutIdService, never).registerOrganisation(anyString(), any(), any(), any(), any(), any())(
        any()
      )
      verify(mockRegisterWithoutIdService, never).registerIndividual(any(), any(), any(), any(), any(), any())(any())
    }

    "when UK, and Migration, call subscriptionCreate, do not call registerOrganisation or registerIndividual" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(CorporateBody))
      mockRegisterWithoutIdOKResponse()
      mockSubscriptionCreateControllerCall()

      await(service.rowRegisterWithoutIdWithSubscription(mockLoggedInUser, Journey.Migrate)(hc, rq))

      verify(mockSubscriptionCreateController, times(1)).subscribe(any())
      verify(mockRegisterWithoutIdService, never).registerOrganisation(anyString(), any(), any(), any(), any(), any())(
        any()
      )
      verify(mockRegisterWithoutIdService, never).registerIndividual(any(), any(), any(), any(), any(), any())(any())
    }

    "when CorporateBody and ROW and Migration, call subscriptionCreate, do not call registerOrganisation or registerIndividual" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]]))
        .thenReturn(Some(UserLocation.ThirdCountry))
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(CorporateBody))
      mockRegisterWithoutIdOKResponse()
      mockSubscriptionCreateControllerCall()

      await(service.rowRegisterWithoutIdWithSubscription(mockLoggedInUser, Journey.Migrate)(hc, rq))

      verify(mockSubscriptionCreateController, times(1)).subscribe(any())
      verify(mockRegisterWithoutIdService, never).registerOrganisation(anyString(), any(), any(), any(), any(), any())(
        any()
      )
      verify(mockRegisterWithoutIdService, never).registerIndividual(any(), any(), any(), any(), any(), any())(any())
    }

    "when CorporateBody and ROW and GYE, call subscriptionCreate, do not call Register without id" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]]))
        .thenReturn(Some(UserLocation.ThirdCountry))
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(CorporateBody))
      when(mockRegistrationDetails.safeId).thenReturn(SafeId("SAFEID"))
      mockRegisterWithoutIdOKResponse()
      mockSubscriptionCreateControllerCall()

      await(service.rowRegisterWithoutIdWithSubscription(mockLoggedInUser, Journey.GetYourEORI)(hc, rq))

      verify(mockSubscriptionCreateController, times(1)).subscribe(any())
      verify(mockRegisterWithoutIdService, never).registerOrganisation(anyString(), any(), any(), any(), any(), any())(
        any()
      )
      verify(mockRegisterWithoutIdService, never).registerIndividual(any(), any(), any(), any(), any(), any())(any())
    }

    "when NA and ROW, call subscriptionCreate, call registerIndividual, do not call registerOrganisation" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]]))
        .thenReturn(Some(UserLocation.ThirdCountry))
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(NA))
      mockRegisterWithoutIdOKResponse()
      mockSubscriptionCreateControllerCall()
      mockSessionCacheRegistrationDetails()
      mockSessionCacheSubscriptionDetails()

      await(service.rowRegisterWithoutIdWithSubscription(mockLoggedInUser, Journey.GetYourEORI)(hc, rq))

      verify(mockRegisterWithoutIdService, times(1)).registerIndividual(any(), any(), any(), any(), any(), any())(any())
      verify(mockSubscriptionCreateController, times(1)).subscribe(any())
      verify(mockRegisterWithoutIdService, never).registerOrganisation(anyString(), any(), any(), any(), any(), any())(
        any()
      )
      verify(mockSessionCache, times(2)).registrationDetails(any())
      verify(mockSessionCache).subscriptionDetails(any())
    }

    "when CorporateBody and ROW, call Register without id Successfully, then call subscriptionCreate" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]]))
        .thenReturn(Some(UserLocation.ThirdCountry))
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(CorporateBody))
      mockSessionCacheRegistrationDetails()
      mockSessionCacheSubscriptionDetails()
      mockRegisterWithoutIdOKResponse()
      mockSubscriptionCreateControllerCall()

      await(service.rowRegisterWithoutIdWithSubscription(mockLoggedInUser, Journey.GetYourEORI)(hc, rq))

      verify(mockSubscriptionCreateController, times(1)).subscribe(any[Journey.Value])
      verify(mockRegisterWithoutIdService, times(1)).registerOrganisation(
        anyString(),
        any(),
        meq(Some(contactDetails)),
        any(),
        any(),
        any()
      )(any())
      verify(mockRegisterWithoutIdService, never).registerIndividual(any(), any(), any(), any(), any(), any())(any())
      verify(mockSessionCache, times(2)).registrationDetails(any())
      verify(mockSessionCache).subscriptionDetails(any())
    }

    "when CorporateBody and ROW, call Register without id which fails, do not call subscriptionCreate" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]]))
        .thenReturn(Some(UserLocation.ThirdCountry))
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(CorporateBody))
      mockSessionCacheRegistrationDetails()
      mockSessionCacheSubscriptionDetails()

      mockRegisterWithoutIdFailure()

      val thrown = the[RuntimeException] thrownBy {
        await(service.rowRegisterWithoutIdWithSubscription(mockLoggedInUser, Journey.GetYourEORI)(hc, rq))
      }
      thrown shouldBe emulatedFailure
    }

    "when CorporateBody and ROW, call register without id, which returns NotOK status, do not call subscriptionCreate" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]]))
        .thenReturn(Some(UserLocation.ThirdCountry))
      when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(CorporateBody))
      mockSessionCacheRegistrationDetails()
      mockSessionCacheSubscriptionDetails()

      mockRegisterWithoutIdNotOKResponse()

      the[RuntimeException] thrownBy {
        await(service.rowRegisterWithoutIdWithSubscription(mockLoggedInUser, Journey.GetYourEORI)(hc, rq))
      } should have message "Registration of organisation FAILED"
    }
  }

  private def mockSubscriptionCreateControllerCall() {
    val mockAction = mock[Action[AnyContent]]
    when(mockAction.apply(any[Request[AnyContent]])).thenReturn(Future.successful(Results.Ok))
    when(mockSubscriptionCreateController.subscribe(any())).thenReturn(mockAction)
  }
}
