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

package unit.controllers.migration

import common.support.testdata.subscription.SubscriptionContactDetailsModelBuilder._
import common.support.testdata.subscription.ReviewPageOrganisationTypeTables
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.CheckYourDetailsController
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.{SubscriptionDetails, SubscriptionFlow}
import uk.gov.hmrc.customs.rosmfrontend.domain.{IdMatchModel, NameDobMatchModel, _}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.check_your_details
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.RegistrationDetailsBuilder.{existingOrganisationRegistrationDetails, individualRegistrationDetails}
import util.builders.SessionBuilder
import util.builders.SubscriptionContactDetailsFormBuilder.Email

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CheckYourDetailsControllerSpec
    extends ControllerSpec with ReviewPageOrganisationTypeTables
    with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockCdsDataCache = mock[SessionCache]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSubscriptionFlow = mock[SubscriptionFlow]

  private val checkYourDetailsView = app.injector.instanceOf[check_your_details]

  val controller = new CheckYourDetailsController(
    app,
    mockAuthConnector,
    mockCdsDataCache,
    mcc,
    checkYourDetailsView,
    mockRequestSessionData
  )

  override def beforeEach: Unit = {
    reset(mockCdsDataCache, mockSubscriptionFlow)
    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(mockSubscriptionFlow)

    val subscriptionDetailsHolderForCompany = SubscriptionDetails(
      personalDataDisclosureConsent = Some(true),
      contactDetails = Some(contactUkDetailsModelWithMandatoryValuesOnly),
      nameDobDetails = Some(NameDobMatchModel("John", None, "Doe", LocalDate.parse("2003-04-08"))),
      idDetails = Some(IdMatchModel(id = "AB123456C")),
      eoriNumber = Some("SOMEEORINUMBER"),
      dateEstablished = Some(LocalDate.parse("2003-04-08")),
      nameIdOrganisationDetails = Some(NameIdOrganisationMatchModel(name = "Company UTR number", id = "UTRNUMBER")),
      addressDetails =
        Some(AddressViewModel(street = "street", city = "city", postcode = Some("postcode"), countryCode = "GB")),
      email = Some("john.doe@example.com")
    )
    when(mockCdsDataCache.email(any[HeaderCarrier])).thenReturn(Future.successful(Email))

    when(mockCdsDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(subscriptionDetailsHolderForCompany)
    when(mockCdsDataCache.registrationDetails(any[HeaderCarrier])).thenReturn(individualRegistrationDetails)
  }

  "Reviewing the details" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(mockAuthConnector, controller.reviewDetails(Journey.Migrate))

    "return ok when data has been provided" in {
      when(mockCdsDataCache.registrationDetails(any[HeaderCarrier])).thenReturn(existingOrganisationRegistrationDetails)

      showForm() { result =>
        status(result) shouldBe OK
      }
    }
  }

  def showForm(
    userSelectedOrgType: Option[CdsOrganisationType] = None,
    userId: String = defaultUserId,
    isIndividualSubscriptionFlow: Boolean = false
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(userSelectedOrgType)
    when(mockSubscriptionFlow.isIndividualFlow).thenReturn(isIndividualSubscriptionFlow)

    test(controller.reviewDetails(Journey.Migrate).apply(SessionBuilder.buildRequestWithSession(userId)))
  }
}
