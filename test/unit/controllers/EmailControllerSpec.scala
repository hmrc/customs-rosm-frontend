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

package unit.controllers

import common.pages.matching.AddressPageFactoring
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.connector.Save4LaterConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.EmailController
import uk.gov.hmrc.customs.rosmfrontend.domain.{EnrolmentResponse, EnrolmentStoreProxyResponse, InternalId, KeyValue}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.email.EmailStatus
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.email.EmailVerificationService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{EnrolmentStoreProxyService, SubscriptionStatusService}
import uk.gov.hmrc.customs.rosmfrontend.services.{Save4LaterService, UserGroupIdSubscriptionStatusCheckService}
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EmailControllerSpec extends ControllerSpec with AddressPageFactoring with MockitoSugar with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockEmailVerificationService = mock[EmailVerificationService]
  private val mockSave4LaterService = mock[Save4LaterService]
  private val mockSessionCache = mock[SessionCache]
  private val mockSave4LaterConnector = mock[Save4LaterConnector]
  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]
  private val mockSubscriptionStatusService = mock[SubscriptionStatusService]
  private val mockUserGroupIdSubscriptionStatusCheckService =
    new UserGroupIdSubscriptionStatusCheckService(
      mockSubscriptionStatusService,
      mockEnrolmentStoreProxyService,
      mockSave4LaterConnector
    )

  private val controller = new EmailController(
    app,
    mockAuthConnector,
    mockEmailVerificationService,
    mockSessionCache,
    mcc,
    mockSave4LaterService,
    mockUserGroupIdSubscriptionStatusCheckService
  )
  val enrolments = EnrolmentStoreProxyResponse(
    List(
      EnrolmentResponse("HMRC-CUS-ORG", "Activated", List(KeyValue("EORINumber", "10000000000000001"))),
      EnrolmentResponse("HMRC-SS-ORG", "Activated", List(KeyValue("EORINumber", "10000000000000002")))
    )
  )

  private val emailStatus = EmailStatus("test@example.com")

  override def beforeEach: Unit = {
    when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(emailStatus)))
    when(
      mockEmailVerificationService
        .isEmailVerified(any[String])(any[HeaderCarrier])
    ).thenReturn(Future.successful(Some(true)))
    when(mockSave4LaterService.saveEmail(any(), any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
    when(mockSessionCache.saveEmail(any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))
    when(mockSave4LaterConnector.get(any(), any())(any(), any(), any()))
      .thenReturn(Future.successful(None))
    when(
      mockEnrolmentStoreProxyService
        .isEnrolmentAssociatedToGroup(any())(any(), any())
    ).thenReturn(Future.successful(false))
    when(mockEnrolmentStoreProxyService.groupIdEnrolments(any())(any(), any()))
      .thenReturn(Future.successful(enrolments.enrolments))
    when(mockSessionCache.saveEori(any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))
  }

  "Viewing the form on  Get Access CDS" should {

    "display the form with no errors" in {
      showFormSubscription() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/subscribe-for-cds/email-confirmed")

        val page = CdsPage(bodyOf(result))
        page.getElementsText(PageLevelErrorSummaryListXPath) shouldBe empty
      }
    }
  }

  "Viewing the form on Get an Eori" should {

    "display the form with no errors" in {
      showFormEori() { result =>
        status(result) shouldBe SEE_OTHER
        val page = CdsPage(bodyOf(result))
        result.header.headers("Location") should endWith("/customs/register-for-cds/eori-exist")

        page.getElementsText(PageLevelErrorSummaryListXPath) shouldBe empty
      }
    }

    "display the form with no errors1" in {
      when(mockEnrolmentStoreProxyService.groupIdEnrolments(any())(any(), any()))
        .thenReturn(Future.successful(Nil))
      showFormEori() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/register-for-cds/email-confirmed")
        val page = CdsPage(bodyOf(result))
        page.getElementsText(PageLevelErrorSummaryListXPath) shouldBe empty
      }
    }
  }
  private def showFormEori(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .form(Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSession(userId))
    )
  }

  private def showFormSubscription(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .form(Journey.Migrate)
        .apply(SessionBuilder.buildRequestWithSession(userId))
    )
  }
}
