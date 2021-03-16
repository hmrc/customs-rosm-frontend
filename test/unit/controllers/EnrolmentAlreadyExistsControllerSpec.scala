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

import common.pages.RegistrationCompletePage
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.EnrolmentAlreadyExistsController
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.registration_exists
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global

class EnrolmentAlreadyExistsControllerSpec extends ControllerSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val cdsEnrolmentId: Option[String] = Some("GB1234567890ABCDE")

  private val registrationExistsView = app.injector.instanceOf[registration_exists]

  val controller = new EnrolmentAlreadyExistsController(app, mockAuthConnector, registrationExistsView, mcc)

  "Enrolment already exists controller" should {

    "redirect to the enrolment already exists page" in {

      withAuthorisedUser(defaultUserId, mockAuthConnector, cdsEnrolmentId = cdsEnrolmentId)
      val result =
        await(
          controller
            .enrolmentAlreadyExists(Journey.GetYourEORI)
            .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
        )

      status(result) shouldBe OK

      val page = CdsPage(bodyOf(result))

      page.title should startWith("You already have access to the Customs Declaration Service")
      page.getElementsText(RegistrationCompletePage.pageHeadingXpath) shouldBe "You already have access to the Customs Declaration Service"
      val Some(eori) = cdsEnrolmentId
      page.getElementsText(RegistrationCompletePage.eoriNumberXpath) shouldBe s"EORI number: $eori"

      page.elementIsPresent(RegistrationCompletePage.LeaveFeedbackLinkXpath) shouldBe true
      page.getElementsText(RegistrationCompletePage.LeaveFeedbackLinkXpath) shouldBe "What did you think of this service? (opens in a new window or tab)"
      page.getElementsHref(RegistrationCompletePage.LeaveFeedbackLinkXpath) shouldBe "/feedback/CDS"
    }
  }
}
