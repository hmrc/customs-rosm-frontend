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

package unit.views.registration

import common.pages.registration.UserLocationPageOrganisation
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector}
import uk.gov.hmrc.customs.rosmfrontend.connector.Save4LaterConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.UserLocationController
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.Save4LaterService
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.registration.RegistrationDisplayService
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.{
  EnrolmentStoreProxyService,
  SubscriptionStatusService,
  TaxEnrolmentsService
}
import uk.gov.hmrc.customs.rosmfrontend.views.html.error_template
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.user_location
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.{subscription_status_outcome_processing, subscription_status_outcome_rejected}
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserLocationFormViewSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSessionCache = mock[SessionCache]
  private val mockSave4LaterService = mock[Save4LaterService]
  private val mockSubscriptionStatusService = mock[SubscriptionStatusService]
  private val mockTaxEnrolmentsService = mock[TaxEnrolmentsService]
  private val mockRegistrationDisplayService = mock[RegistrationDisplayService]
  private val mockSave4LaterConnector = mock[Save4LaterConnector]
  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]

  private val userLocationView = app.injector.instanceOf[user_location]
  private val subscriptionStatusOutcomeProcessing =
    app.injector.instanceOf[subscription_status_outcome_processing]
  private val errorTemplate = app.injector.instanceOf[error_template]
  private val subscriptionStatusOutcomeRejected =
    app.injector.instanceOf[subscription_status_outcome_rejected]

  private val controller = new UserLocationController(
    app,
    mockAuthConnector,
    mockRequestSessionData,
    mockSave4LaterService,
    mockSubscriptionStatusService,
    mockTaxEnrolmentsService,
    mockSessionCache,
    mockRegistrationDisplayService,
    mcc,
    userLocationView,
    subscriptionStatusOutcomeProcessing,
    subscriptionStatusOutcomeRejected,
    errorTemplate
  )

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    when(mockSave4LaterConnector.get(any(), any())(any(), any(), any()))
      .thenReturn(Future.successful(None))
    when(
      mockEnrolmentStoreProxyService
        .isEnrolmentAssociatedToGroup(any())(any(), any())
    ).thenReturn(Future.successful(false))
  }

  "User location page" should {

    val expectedTitleOrganisation = "Where is your organisation established?"

    s"display title as '$expectedTitleOrganisation for entity with type organisation" in {
      showForm()({ result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith(expectedTitleOrganisation)
      })
    }

    val expectedTitleIndividual = "Where are you based?"

    s"display title as '$expectedTitleIndividual for entity with type individual" in {
      showForm(affinityGroup = AffinityGroup.Individual)({ result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith(expectedTitleIndividual)
      })
    }

    "submit result when user chooses to continue" in {
      showForm()({ result =>
        val page = CdsPage(bodyOf(result))
        page
          .formAction("user-location-form") shouldBe uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.UserLocationController
          .submit(Journey.GetYourEORI)
          .url
      })
    }

    "display correct location on registration journey" in {
      showForm()({ result =>
        val page = CdsPage(bodyOf(result))
        page.elementIsPresent(UserLocationPageOrganisation.locationUkField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationUkField) should be("uk")
        page.elementIsPresent(UserLocationPageOrganisation.locationIomField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationIomField) should be("iom")
        page.elementIsPresent(UserLocationPageOrganisation.locationIslandsField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationIslandsField) should be("islands")
        page.elementIsPresent(UserLocationPageOrganisation.locationEuField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationEuField) should be("eu")
        page.elementIsPresent(UserLocationPageOrganisation.locationThirdCountryField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationThirdCountryField) should be("third-country")
      })
    }

    "display correct location on migration journey" in {
      showMigrationForm()({ result =>
        val page = CdsPage(bodyOf(result))
        page.elementIsPresent(UserLocationPageOrganisation.locationUkField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationUkField) should be("uk")
        page.elementIsPresent(UserLocationPageOrganisation.locationIslandsField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationIslandsField) should be("islands")
        page.elementIsPresent(UserLocationPageOrganisation.locationEuField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationEuField) should be("eu")
        page.elementIsPresent(UserLocationPageOrganisation.locationThirdCountryField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationThirdCountryField) should be("third-country")
      })
    }

    "display a progressive disclosure element for countries in the EU" in {
      showForm()({ result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(UserLocationPageOrganisation.countriesInTheEuTitleElement) should be("Countries in the EU")
        page.getElementsText(UserLocationPageOrganisation.countriesInTheEuContentsElement) should be(
          "Austria, Belgium, Bulgaria, Croatia, Republic of Cyprus, Czech Republic, Denmark, Estonia, Finland, France, Germany, Greece, Hungary, Ireland, Italy, Latvia, Lithuania, Luxembourg, Malta, Netherlands, Poland, Portugal, Romania, Slovakia, Slovenia, Spain, Sweden and the UK."
        )
      })
    }
  }

  private def showForm(userId: String = defaultUserId, affinityGroup: AffinityGroup = AffinityGroup.Organisation)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector, userAffinityGroup = affinityGroup)

    val result = controller
      .form(Journey.GetYourEORI)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  private def showMigrationForm(
    userId: String = defaultUserId,
    affinityGroup: AffinityGroup = AffinityGroup.Organisation
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector, userAffinityGroup = affinityGroup)

    val result = controller
      .form(Journey.Migrate)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }
}
