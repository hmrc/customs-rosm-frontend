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

package unit.controllers.subscription

import common.pages.subscription.{EoriNumberPage, SubscriptionContactDetailsPage}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.UseThisEoriController
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.EoriNumberSubscriptionFlowPage
import uk.gov.hmrc.customs.rosmfrontend.domain.{CdsOrganisationType, EnrolmentResponse, KeyValue}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.RequestSessionData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.{use_this_eori, use_this_eori_different_gg}
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UseThisEoriControllerSpec
    extends SubscriptionFlowCreateModeTestSupport with BeforeAndAfterEach
    with SubscriptionFlowReviewModeTestSupport {
  protected override val formId: String = EoriNumberPage.formId

  protected override def submitInCreateModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.WhatIsYourEoriController
      .submit(isInReviewMode = false, Journey.Migrate)
      .url

  protected override def submitInReviewModeUrl: String =
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.WhatIsYourEoriController
      .submit(isInReviewMode = true, Journey.Migrate)
      .url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val useThisEoriView = app.injector.instanceOf[use_this_eori]
  private val useThisEoriDifferentGGView = app.injector.instanceOf[use_this_eori_different_gg]
  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]

  private val controller = new UseThisEoriController(
    app,
    mockAuthConnector,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsHolderService,
    mockEnrolmentStoreProxyService,
    mcc,
    useThisEoriView,
    useThisEoriDifferentGGView
  )

  val existingGroupEnrolment: EnrolmentResponse =
    EnrolmentResponse("HMRC-OTHER-ORG", "Active", List(KeyValue("EORINumber", "GB1234567890")))

  val existingEori = "GB1234567890"

  override def beforeEach: Unit = {
    reset(mockSubscriptionFlowManager, mockSubscriptionDetailsHolderService, mockEnrolmentStoreProxyService)
    when(mockSubscriptionDetailsHolderService.cachedExistingEoriNumber(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(existingEori)))
    when(mockEnrolmentStoreProxyService.isEoriEnrolledWithAnotherGG(any())(any(), any()))
      .thenReturn(Future.successful(false))
    setupMockSubscriptionFlowManager(EoriNumberSubscriptionFlowPage)
  }

  "Subscription Use This Eori Number" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.display(Journey.Migrate))

    "display title as 'What is your GB EORI number?'" in {
      showCreateForm(journey = Journey.Migrate) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("This is the EORI number linked to your Government Gateway")
      }
    }

    "display the back link" in {
      showCreateForm(journey = Journey.Migrate)(verifyBackLinkInCreateModeSubscribe)
    }

    "display the back link for subscribe user journey" in {
      showCreateForm(journey = Journey.Migrate) { result =>
        verifyBackLinkIn(result)
      }
    }

    "display existing Eori Number" in {
      showCreateForm(journey = Journey.Migrate) { result =>
        val page = CdsPage(contentAsString(result))
        verifyExistinEoriNumber(page)
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm(journey = Journey.Migrate) { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementValue(EoriNumberPage.continueButtonXpath) shouldBe "Continue"
      }
    }

  }

  "submitting the form all organisation types" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.submit(Journey.Migrate))
    when(mockEnrolmentStoreProxyService.isEoriEnrolledWithAnotherGG(any())(any(), any()))
      .thenReturn(Future.successful(false))
    "redirect to next screen" in {
      submitForm()(verifyRedirectToNextPageIn(_)("next-page-url"))
    }

  }

  "submitting the form all organisation types for already eori linked to different GG" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.submit(Journey.Migrate))

    "redirect to eori signout" in {
      when(mockEnrolmentStoreProxyService.isEoriEnrolledWithAnotherGG(any())(any(), any()))
        .thenReturn(Future.successful(true))

      submitForm()(verifyRedirectToNextPageIn(_)("/customs/subscribe-for-cds/eori-already-used-sign-out"))
    }

  }

  private def submitForm(userId: String = defaultUserId, userSelectedOrgType: Option[CdsOrganisationType] = None)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(userSelectedOrgType)
    when(mockSubscriptionDetailsHolderService.cacheEoriNumber(any())(any())).thenReturn(Future.successful(()))

    test(
      controller.submit(Journey.Migrate)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, Map("yes-no-answer" -> "true"))
      )
    )
  }

  private def showCreateForm(
    userId: String = defaultUserId,
    userSelectedOrganisationType: Option[CdsOrganisationType] = None,
    journey: Journey.Value
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(userSelectedOrganisationType)

    test(controller.display(Journey.Migrate).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyExistinEoriNumber(page: CdsPage): Unit =
    page.getElementsText("//*[@id='eori-number']/tbody/tr/td[2]") should be(existingEori)

  private def verifyBackLinkIn(result: Result) = {
    val page = CdsPage(contentAsString(result))
    page.getElementAttributeHref(SubscriptionContactDetailsPage.backLinkXPath) shouldBe previousPageUrl
  }

  private def verifyRedirectToNextPageIn(result: Result)(linkToVerify: String) = {
    status(result) shouldBe SEE_OTHER
    result.header.headers(LOCATION) should endWith(linkToVerify)
  }

}
