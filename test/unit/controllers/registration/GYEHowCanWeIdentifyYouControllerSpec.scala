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

///*
// * Copyright 2021 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package unit.controllers.registration
//
//import common.pages.RegisterHowCanWeIdentifyYouPage
//import org.joda.time.LocalDate
//import org.mockito.ArgumentMatchers
//import org.mockito.ArgumentMatchers._
//import org.mockito.Mockito.when
//import org.scalatest.BeforeAndAfter
//import play.api.mvc.Result
//import play.api.test.Helpers._
//import uk.gov.hmrc.auth.core.AuthConnector
//import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.GYEHowCanWeIdentifyYouController
//import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.Individual
//import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
//import uk.gov.hmrc.customs.rosmfrontend.domain.{CdsOrganisationType, NameDobMatchModel, Utr}
//import uk.gov.hmrc.customs.rosmfrontend.models.Journey
//import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
//import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
//import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.how_can_we_identify_you
//import uk.gov.hmrc.http.HeaderCarrier
//import unit.controllers.CdsPage
//import util.ControllerSpec
//import util.builders.AuthBuilder.withAuthorisedUser
//import util.builders.SessionBuilder
//
//import scala.concurrent.Future
//import scala.concurrent.ExecutionContext.Implicits.global
//
//class GYEHowCanWeIdentifyYouControllerSpec extends ControllerSpec with BeforeAndAfter {
//
//  private val mockAuthConnector = mock[AuthConnector]
//  private val mockMatchingService = mock[MatchingService]
//  private val mockFrontendDataCache = mock[SessionCache]
//
//  private val howCanWeIdentifyYouView = app.injector.instanceOf[how_can_we_identify_you]
//
//  private val controller = new GYEHowCanWeIdentifyYouController(
//    app,
//    mockAuthConnector,
//    mockMatchingService,
//    mcc,
//    howCanWeIdentifyYouView,
//    mockFrontendDataCache
//  )
//
//  "Viewing the form " should {
//    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
//      mockAuthConnector,
//      controller.form(CdsOrganisationType.IndividualId, Journey.GetYourEORI)
//    )
//  }
//
//  "Submitting the form " should {
//
//    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
//      mockAuthConnector,
//      controller.submit(CdsOrganisationType.IndividualId, Journey.GetYourEORI)
//    )
//
//    "redirect to the Confirm page when a nino is matched" in {
//
//      val nino = "AB123456C"
//      when(mockFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(
//        Future.successful(
//          SubscriptionDetails(nameDobDetails = Some(NameDobMatchModel("test", None, "user", LocalDate.now)))
//        )
//      )
//      when(
//        mockMatchingService
//          .matchIndividualWithNino(ArgumentMatchers.eq(nino), any[Individual], any())(any[HeaderCarrier])
//      ).thenReturn(Future.successful(true))
//
//      submitForm(Map("nino" -> nino, "ninoOrUtrRadio" -> "nino"), CdsOrganisationType.IndividualId, Journey.GetYourEORI) {
//        result =>
//          status(result) shouldBe SEE_OTHER
//          result.header.headers("Location") shouldBe "/customs/register-for-cds/matching/confirm"
//      }
//    }
//
//    "redirect to the Confirm page when a UTR is matched" in {
//
//      val utr = "1111111111"
//      when(mockFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(
//        Future.successful(
//          SubscriptionDetails(nameDobDetails = Some(NameDobMatchModel("test", None, "user", LocalDate.now)))
//        )
//      )
//      when(
//        mockMatchingService
//          .matchIndividualWithId(ArgumentMatchers.eq(Utr(utr)), any[Individual], any())(any[HeaderCarrier])
//      ).thenReturn(Future.successful(true))
//
//      submitForm(Map("utr" -> utr, "ninoOrUtrRadio" -> "utr"), CdsOrganisationType.IndividualId, Journey.GetYourEORI) {
//        result =>
//          status(result) shouldBe SEE_OTHER
//          result.header.headers("Location") shouldBe "/customs/register-for-cds/matching/confirm"
//      }
//    }
//
//    "give a page level error when a nino is not matched" in {
//      val nino = "AB123456C"
//      when(mockFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(
//        Future.successful(
//          SubscriptionDetails(nameDobDetails = Some(NameDobMatchModel("test", None, "user", LocalDate.now)))
//        )
//      )
//      when(
//        mockMatchingService
//          .matchIndividualWithNino(ArgumentMatchers.eq(nino), any[Individual], any())(any[HeaderCarrier])
//      ).thenReturn(Future.successful(false))
//
//      submitForm(Map("nino" -> nino, "ninoOrUtrRadio" -> "nino"), CdsOrganisationType.IndividualId, Journey.GetYourEORI) {
//        result =>
//          status(result) shouldBe BAD_REQUEST
//          val page = CdsPage(bodyOf(result))
//          page.getElementsText(RegisterHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Your details have not been found. Check that your details are correct and then try again."
//      }
//    }
//
//    "give a page level error when a UTR is not matched" in {
//      val utr = "1111111111"
//      when(mockFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(
//        Future.successful(
//          SubscriptionDetails(nameDobDetails = Some(NameDobMatchModel("test", None, "user", LocalDate.now)))
//        )
//      )
//      when(
//        mockMatchingService
//          .matchIndividualWithId(ArgumentMatchers.eq(Utr(utr)), any[Individual], any())(any[HeaderCarrier])
//      ).thenReturn(Future.successful(false))
//
//      submitForm(Map("utr" -> utr, "ninoOrUtrRadio" -> "utr"), CdsOrganisationType.IndividualId, Journey.GetYourEORI) {
//        result =>
//          status(result) shouldBe BAD_REQUEST
//          val page = CdsPage(bodyOf(result))
//          page.getElementsText(RegisterHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe "Your details have not been found. Check that your details are correct and then try again."
//      }
//    }
//  }
//
//  def submitForm(form: Map[String, String], orgType: String, journey: Journey.Value, userId: String = defaultUserId)(
//    test: Future[Result] => Any
//  ) {
//    withAuthorisedUser(userId, mockAuthConnector)
//    test(controller.submit(orgType, journey).apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)))
//  }
//}
