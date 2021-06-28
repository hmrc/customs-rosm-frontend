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

import common.support.testdata.registration.RegistrationInfoGenerator._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json.toJson
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.ResponseCommon
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{BusinessShortName, SubscriptionDetails}
import uk.gov.hmrc.customs.rosmfrontend.services.Save4LaterService
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{CachedData, SessionCache, SessionTimeOutException}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import util.builders.RegistrationDetailsBuilder._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SessionCacheSpec extends IntegrationTestsSpec with MockitoSugar with MongoSpecSupport {

  lazy val appConfig = app.injector.instanceOf[AppConfig]

  private val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }
  private val save4LaterService = mock[Save4LaterService]
  val sessionCache = new SessionCache(appConfig, reactiveMongoComponent, save4LaterService)

  val hc = mock[HeaderCarrier]

  "Session cache" should {

    "store, fetch and update Subscription details handler correctly" in {
      val sessionId: SessionId = setupSession

      val holder = SubscriptionDetails(businessShortName = Some(BusinessShortName(Some("some business name"))))

      await(sessionCache.saveSubscriptionDetails(holder)(hc))

      val expectedJson = toJson(CachedData(subDetails = Some(holder)))
      val cache = await(sessionCache.findById(Id(sessionId.value)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.subscriptionDetails(hc)) mustBe holder

      val updatedHolder = SubscriptionDetails(
        businessShortName = Some(BusinessShortName(Some("different business name"))),
        sicCode = Some("sic")
      )

      await(sessionCache.saveSubscriptionDetails(updatedHolder)(hc))

      val expectedUpdatedJson = toJson(CachedData(subDetails = Some(updatedHolder)))
      val updatedCache = await(sessionCache.findById(Id(sessionId.value)))
      val Some(Cache(_, Some(updatedJson), _, _)) = updatedCache
      updatedJson mustBe expectedUpdatedJson
    }

    "provide default when subscription details holder not in cache" in {
      when(hc.sessionId).thenReturn(Some(SessionId("does-not-exist")))

      val e1 = intercept[SessionTimeOutException] {
        await(sessionCache.subscriptionDetails(hc))
      }
      e1.errorMessage mustBe "No match session id for signed in user with session : does-not-exist"

      val s1 = setupSession

      val e2 = intercept[SessionTimeOutException] {
        await(sessionCache.subscriptionDetails(hc))
      }
      e2.errorMessage mustBe s"No match session id for signed in user with session : ${s1.value}"

      val s2 = setupSession
      await(
        sessionCache.insert(
          Cache(Id(s2.value), data = Some(toJson(CachedData(regDetails = Some(individualRegistrationDetails)))))
        )
      )

      await(sessionCache.subscriptionDetails(hc)) mustBe SubscriptionDetails()
    }

    "store, fetch SubscriptionCreateOutcome correctly" in {
      val sessionId: SessionId = setupSession
      val subscriptionCreateOutcome = SubscriptionCreateOutcome("GB02118283272", "23 June 2018", Some("orgName"))
      await(sessionCache.saveSubscriptionCreateOutcome(subscriptionCreateOutcome)(hc))
      val cache = await(sessionCache.findById(Id(sessionId.value)))
      val expectedJson = toJson(CachedData(subscriptionCreateOutcome = Some(subscriptionCreateOutcome)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson
      await(sessionCache.subscriptionCreateOutcome(hc)) mustBe subscriptionCreateOutcome
    }

    "store, fetch SubscriptionStatusOutcome correctly" in {
      val sessionId: SessionId = setupSession
      val subscriptionStatusOutcome = SubscriptionStatusOutcome("23 June 2018")
      await(sessionCache.saveSubscriptionStatusOutcome(subscriptionStatusOutcome)(hc))
      val cache = await(sessionCache.findById(Id(sessionId.value)))
      val expectedJson = toJson(CachedData(subscriptionStatusOutcome = Some(subscriptionStatusOutcome)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson
      await(sessionCache.subscriptionStatusOutcome(hc)) mustBe subscriptionStatusOutcome

    }
    "store, fetch Email correctly" in {
      val sessionId: SessionId = setupSession
      val email = "test@test.com"
      await(sessionCache.saveEmail(email)(hc))
      val cache = await(sessionCache.findById(Id(sessionId.value)))
      val expectedJson = toJson(CachedData(email = Some(email)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson
      await(sessionCache.email(hc)) mustBe email
      await(sessionCache.mayBeEmail(hc)) mustBe Some(email)

    }
    "store, fetch Eori correctly" in {
      val sessionId: SessionId = setupSession
      val eori = Eori("GB0000000000")
      await(sessionCache.saveEori(eori)(hc))
      val cache = await(sessionCache.findById(Id(sessionId.value)))
      val expectedJson = toJson(CachedData(eori = Some(eori.id)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson
      await(sessionCache.eori(hc)) mustBe Some(eori.id)
    }

    "store, fetch and update Registration details with OrgType correctly" in {
      val sessionId: SessionId = setupSession
      when(save4LaterService.saveOrgType(any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      await(
        sessionCache.saveRegistrationDetails(
          organisationRegistrationDetails,
          InternalId("InternalId"),
          Some(CdsOrganisationType.Company)
        )(hc)
      )
      val cache = await(sessionCache.findById(Id(sessionId.value)))
      val expectedJson = toJson(CachedData(regDetails = Some(organisationRegistrationDetails)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson
      await(sessionCache.name(hc)) mustBe Some(organisationRegistrationDetails.name)

    }

    "store, fetch and update Registration details with OrgType AND SafeId for ROW correctly" in {
      val sessionId: SessionId = setupSession
      when(save4LaterService.saveOrgType(any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(save4LaterService.saveSafeId(any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      await(
        sessionCache.saveRegistrationDetailsWithoutId(
          organisationRegistrationDetails,
          InternalId("InternalId"),
          Some(CdsOrganisationType.Company)
        )(hc)
      )
      val cache = await(sessionCache.findById(Id(sessionId.value)))
      val expectedJson = toJson(CachedData(regDetails = Some(organisationRegistrationDetails)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson
    }

    "store, fetch and update Registration details correctly" in {
      val sessionId: SessionId = setupSession

      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(CachedData(regDetails = Some(organisationRegistrationDetails)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.registrationDetails(hc)) mustBe organisationRegistrationDetails
      await(sessionCache.saveRegistrationDetails(individualRegistrationDetails)(hc))

      val updatedCache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedUpdatedJson = toJson(CachedData(regDetails = Some(individualRegistrationDetails)))
      val Some(Cache(_, Some(updatedJson), _, _)) = updatedCache
      updatedJson mustBe expectedUpdatedJson
    }

    "store and fetch RegisterWith EORI And Id Response correctly for Register with Eori and Id response" in {
      val sessionId: SessionId = setupSession

      val processingDate = DateTime.now.withTimeAtStartOfDay()
      val responseCommon = ResponseCommon(status = "OK", processingDate = processingDate)
      val trader = Trader(fullName = "New trading", shortName = "nt")
      val establishmentAddress =
        EstablishmentAddress(streetAndNumber = "new street", city = "leeds", countryCode = "GB")
      val responseData: ResponseData = ResponseData(
        SAFEID = "SomeSafeId",
        trader = trader,
        establishmentAddress = establishmentAddress,
        hasInternetPublication = true,
        startDate = "2018-01-01"
      )
      val registerWithEoriAndIdResponseDetail = RegisterWithEoriAndIdResponseDetail(
        outcome = Some("PASS"),
        caseNumber = Some("case no 1"),
        responseData = Some(responseData)
      )
      val rd = RegisterWithEoriAndIdResponse(
        responseCommon = responseCommon,
        responseDetail = Some(registerWithEoriAndIdResponseDetail)
      )

      await(sessionCache.saveRegisterWithEoriAndIdResponse(rd)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(CachedData(registerWithEoriAndIdResponse = Some(rd)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.registerWithEoriAndIdResponse(hc)) mustBe rd
    }

    "throw exception when registration Details requested and not available in cache" in {
      val s = setupSession
      await(sessionCache.insert(Cache(Id(s.value), data = Some(toJson(CachedData())))))

      val caught = intercept[IllegalStateException] {
        await(sessionCache.registrationDetails(hc))
      }
      caught.getMessage mustBe s"regDetails is not cached in data for the sessionId: ${s.value}"
    }

    "store, fetch and update Registration Info correctly" in {
      val sessionId: SessionId = setupSession

      await(sessionCache.saveRegistrationInfo(organisationRegistrationInfoWithAllOptionalValues)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(CachedData(regInfo = Some(organisationRegistrationInfoWithAllOptionalValues)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.registrationInfo(hc)) mustBe organisationRegistrationInfoWithAllOptionalValues
      await(sessionCache.saveRegistrationInfo(individualRegistrationInfoWithAllOptionalValues)(hc))

      val updatedCache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedUpdatedJson = toJson(CachedData(regInfo = Some(individualRegistrationInfoWithAllOptionalValues)))
      val Some(Cache(_, Some(updatedJson), _, _)) = updatedCache
      updatedJson mustBe expectedUpdatedJson
    }

    "throw exception when registration info requested and not available in cache" in {
      val s = setupSession
      await(sessionCache.insert(Cache(Id(s.value), data = Some(toJson(CachedData())))))

      val caught = intercept[IllegalStateException] {
        await(sessionCache.registrationInfo(hc))
      }
      caught.getMessage mustBe s"regInfo is not cached in data for the sessionId: ${s.value}"
    }

    "store Registration Details, Info and Subscription Details Holder correctly" in {
      val sessionId: SessionId = setupSession

      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails)(hc))
      val holder = SubscriptionDetails()
      await(sessionCache.saveSubscriptionDetails(holder)(hc))
      await(sessionCache.saveRegistrationInfo(organisationRegistrationInfoWithAllOptionalValues)(hc))
      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(
        CachedData(
          Some(organisationRegistrationDetails),
          Some(holder),
          Some(organisationRegistrationInfoWithAllOptionalValues)
        )
      )

      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson
    }

    "remove from the cache" in {
      val sessionId: SessionId = setupSession
      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails)(hc))

      await(sessionCache.remove(hc))

      val cached = await(sessionCache.findById(Id(sessionId.value)))
      cached mustBe None
    }
  }

  private def setupSession: SessionId = {
    val sessionId = SessionId("sessionId-" + UUID.randomUUID())
    when(hc.sessionId).thenReturn(Some(sessionId))
    sessionId
  }
}
