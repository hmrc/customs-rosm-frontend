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

package unit.services.organisation

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, mock => _}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.customs.rosmfrontend.domain.{CdsOrganisationType, CorporateBody, Partnership}
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec
import util.builders.RegistrationDetailsBuilder

import scala.concurrent.Future

class OrgTypeLookupSpec extends UnitSpec with BeforeAndAfterEach with MockitoSugar {

  private val mockCache = mock[SessionCache]
  private val mockReqSessionData = mock[RequestSessionData]
  private val hc = mock[HeaderCarrier]
  private val req = mock[Request[AnyContent]]

  override def beforeEach {
    reset(mockCache, mockReqSessionData)
  }

  val lookup = new OrgTypeLookup(mockReqSessionData, mockCache)

  "Org type lookup" should {

    "give org type from request session" in {
      when(mockReqSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
        .thenReturn(Some(CdsOrganisationType.Company))

      val orgType = await(lookup.etmpOrgType(req, hc))

      orgType shouldBe Some(CorporateBody)
    }

    "give org type from cache" in {
      when(mockReqSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(RegistrationDetailsBuilder.partnershipRegistrationDetails))

      val orgType = await(lookup.etmpOrgType(req, hc))

      orgType shouldBe Some(Partnership)
    }

    "return None when neither the request session or cache contains the org type" in {
      when(mockReqSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(RegistrationDetailsBuilder.emptyETMPOrgTypeRegistrationDetails))

      val orgType = await(lookup.etmpOrgType(req, hc))

      orgType shouldBe None
    }

    "throw an exception when different type of registration details is retrieved" in {
      when(mockReqSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(RegistrationDetailsBuilder.individualRegistrationDetails))

      val thrown = intercept[IllegalStateException] {
        await(lookup.etmpOrgType(req, hc))
      }

      thrown.getMessage shouldBe "No Registration details in cache."
    }
  }
}
