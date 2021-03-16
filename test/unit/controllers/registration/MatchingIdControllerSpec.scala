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

package unit.controllers.registration

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.mvc.Http.Status.SEE_OTHER
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.MatchingIdController
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder._
import util.builders.{AuthBuilder, SessionBuilder}

import scala.concurrent.Future

class MatchingIdControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockMatchingService = mock[MatchingService]
  private val mockRequestSessionData = mock[RequestSessionData]

  private val userId: String = "someUserId"
  private val ctUtrId: String = "ct-utr-Id"
  private val saUtrId: String = "sa-utr-Id"
  private val payeNinoId: String = "AB123456C"

  private val controller =
    new MatchingIdController(app, mockAuthConnector, mockMatchingService, mockRequestSessionData, mcc)

  override def beforeEach: Unit =
    reset(mockMatchingService)

  "MatchingIdController for GetAnEori Journey" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.matchWithIdOnly())

    "for Journey GetAnEori redirect to Select user location page when Government Gateway Account has no enrollments" in {
      withAuthorisedUser(userId, mockAuthConnector)

      val controller =
        new MatchingIdController(app, mockAuthConnector, mockMatchingService, mockRequestSessionData, mcc)
      val result: Result = await(controller.matchWithIdOnly().apply(SessionBuilder.buildRequestWithSession(userId)))

      status(result) shouldBe SEE_OTHER
      assertRedirectToUserLocationPage(result, Journey.GetYourEORI)
      verifyNoInteractions(mockMatchingService)
    }

    "for Journey GetAnEori redirect to Confirm page when a match found with CT UTR only" in {
      withAuthorisedUser(userId, mockAuthConnector, ctUtrId = Some(ctUtrId))

      when(mockMatchingService.matchBusinessWithIdOnly(meq(Utr(ctUtrId)), any[LoggedInUser])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      val result = controller.matchWithIdOnly().apply(SessionBuilder.buildRequestWithSession(userId))

      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") should be(
        uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.ConfirmContactDetailsController
          .form(Journey.GetYourEORI)
          .url
      )
    }

    "for Journey GetAnEori redirect to Select user location page when no match found for SA UTR" in {
      withAuthorisedUser(userId, mockAuthConnector, saUtrId = Some(saUtrId))

      when(mockMatchingService.matchBusinessWithIdOnly(meq(Utr(saUtrId)), any[LoggedInUser])(any[HeaderCarrier]))
        .thenReturn(Future.successful(false))

      val controller =
        new MatchingIdController(app, mockAuthConnector, mockMatchingService, mockRequestSessionData, mcc)
      val result = await(controller.matchWithIdOnly().apply(SessionBuilder.buildRequestWithSession(userId)))

      status(result) shouldBe SEE_OTHER
      assertRedirectToUserLocationPage(result, Journey.GetYourEORI)
    }

    "for Journey GetAnEori redirect to Confirm page when a match found with SA UTR only" in {
      withAuthorisedUser(userId, mockAuthConnector, saUtrId = Some(saUtrId))

      when(mockMatchingService.matchBusinessWithIdOnly(meq(Utr(saUtrId)), any[LoggedInUser])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      val result = controller.matchWithIdOnly().apply(SessionBuilder.buildRequestWithSession(userId))

      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") should be(
        uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.ConfirmContactDetailsController
          .form(Journey.GetYourEORI)
          .url
      )
    }

    "for Journey GetAnEori redirect to Confirm page when a match found with a valid PAYE Nino" in {
      withAuthorisedUser(userId, mockAuthConnector, payeNinoId = Some(payeNinoId))

      when(mockMatchingService.matchBusinessWithIdOnly(meq(Nino(payeNinoId)), any[LoggedInUser])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      val result = controller.matchWithIdOnly().apply(SessionBuilder.buildRequestWithSession(userId))

      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") should be(
        uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.ConfirmContactDetailsController
          .form(Journey.GetYourEORI)
          .url
      )
    }

    "for Journey GetAnEori use CT UTR when user is registered for CT and SA" in {
      withAuthorisedUser(userId, mockAuthConnector, ctUtrId = Some(ctUtrId), saUtrId = Some(saUtrId))

      when(mockMatchingService.matchBusinessWithIdOnly(meq(Utr(ctUtrId)), any[LoggedInUser])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      val result = controller.matchWithIdOnly().apply(SessionBuilder.buildRequestWithSession(userId))

      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") should be(
        uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.ConfirmContactDetailsController
          .form(Journey.GetYourEORI)
          .url
      )
    }
  }

  "MatchingIdController for Subscribe Journey" should {

    "redirect to BAS Gateway login when request is not authenticated with redirect to based in UK" in {
      AuthBuilder.withNotLoggedInUser(mockAuthConnector)

      val result = controller
        .matchWithIdOnlyForExistingReg()
        .apply(
          SessionBuilder.buildRequestWithSessionAndPathNoUserAndBasedInUkNotSelected(
            method = "GET",
            path = "/customs/subscribe-for-cds/subscribe"
          )
        )
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") shouldBe "/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9830%2Fcustoms%2Fsubscribe-for-cds%2Fare-you-based-in-uk&origin=customs-rosm-frontend"
    }

    "redirect to BAS Gateway login when request is not authenticated with redirect to Subscribe when the user selects yes on based in uk" in {
      AuthBuilder.withNotLoggedInUser(mockAuthConnector)

      val result = controller
        .matchWithIdOnlyForExistingReg()
        .apply(
          SessionBuilder
            .buildRequestWithSessionAndPathNoUser(method = "GET", path = "/customs/subscribe-for-cds/subscribe")
        )
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") shouldBe "/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9830%2Fcustoms%2Fsubscribe-for-cds%2Fsubscribe&origin=customs-rosm-frontend"
    }

    "redirect to Select Location Type page for selected journey type Subscribe " in {
      withAuthorisedUser(userId, mockAuthConnector, ctUtrId = Some(ctUtrId))

      when(mockMatchingService.matchBusinessWithIdOnly(meq(Utr(ctUtrId)), any[LoggedInUser])(any[HeaderCarrier]))
        .thenReturn(Future.successful(false))

      val controller =
        new MatchingIdController(app, mockAuthConnector, mockMatchingService, mockRequestSessionData, mcc)
      val result =
        await(controller.matchWithIdOnlyForExistingReg().apply(SessionBuilder.buildRequestWithSession(userId)))

      status(result) shouldBe SEE_OTHER
      assertRedirectToUserLocationPage(result, Journey.Migrate)
    }
  }

  private def assertRedirectToUserLocationPage(result: Result, journey: Journey.Value): Unit =
    result.header.headers("Location") should be(
      uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.UserLocationController.form(journey).url
    )
}
