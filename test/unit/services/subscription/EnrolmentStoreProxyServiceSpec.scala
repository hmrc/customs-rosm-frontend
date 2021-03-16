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

package unit.services.subscription

import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.customs.rosmfrontend.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.{
  EnrolmentResponse,
  EnrolmentStoreProxyResponse,
  Eori,
  GroupId,
  GroupIds,
  KeyValue
}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrolmentStoreProxyServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfter {

  private val mockEnrolmentStoreProxyConnector =
    mock[EnrolmentStoreProxyConnector]

  private val service = new EnrolmentStoreProxyService(mockEnrolmentStoreProxyConnector)
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  before {
    reset(mockEnrolmentStoreProxyConnector)
  }

  private val serviceName = "HMRC-CUS-ORG"
  private val state = "Activated"
  private val identifier = KeyValue("EORINumber", "10000000000000001")
  private val groupId = GroupId("groupId")
  private val enrolmentResponse =
    EnrolmentResponse(serviceName, state, List(identifier))
  private val enrolmentStoreProxyResponse = EnrolmentStoreProxyResponse(List(enrolmentResponse))
  private val serviceName1 = "HMRC-VAT-ORG"
  private val enrolmentResponseNoHmrcCusOrg =
    EnrolmentResponse(serviceName1, state, List(identifier))
  private val enrolmentStoreProxyResponsNoHmrcCusOrg =
    EnrolmentStoreProxyResponse(List(enrolmentResponseNoHmrcCusOrg))
  private val responseWithGroups = """{
                             |    "principalGroupIds": [
                             |       "ABCEDEFGI1234567",
                             |       "ABCEDEFGI1234568"
                             |    ],
                             |    "delegatedGroupIds": [
                             |       "ABCEDEFGI1234567",
                             |       "ABCEDEFGI1234568"
                             |    ]
                             |}""".stripMargin

  private val groupIds = Json.parse(responseWithGroups).as[GroupIds]

  "EnrolmentStoreProxyService" should {
    "return enrolment if they exist against the groupId" in {
      when(
        mockEnrolmentStoreProxyConnector
          .getCdsEnrolmentByGroupId(any[String])(meq(headerCarrier), any())
      ).thenReturn(Future.successful(enrolmentStoreProxyResponse))

      await(service.isEnrolmentAssociatedToGroup(groupId)) shouldBe true

      verify(mockEnrolmentStoreProxyConnector).getCdsEnrolmentByGroupId(any[String])(meq(headerCarrier), any())
    }

    "return enrolment if they exist against service  HMRC-CUS-ORG the groupId" in {
      when(
        mockEnrolmentStoreProxyConnector
          .getCdsEnrolmentByGroupId(any[String])(meq(headerCarrier), any())
      ).thenReturn(Future.successful(enrolmentStoreProxyResponsNoHmrcCusOrg))

      await(service.isEnrolmentAssociatedToGroup(groupId)) shouldBe false

      verify(mockEnrolmentStoreProxyConnector).getCdsEnrolmentByGroupId(any[String])(meq(headerCarrier), any())
    }

    "return all enrolments if they exists" in {
      val enrolments = EnrolmentStoreProxyResponse(
        List(
          EnrolmentResponse("HMRC-CUS-ORG", "Activated", List(KeyValue("EORINumber", "10000000000000001"))),
          EnrolmentResponse("HMRC-SS-ORG", "Activated", List(KeyValue("EORINumber", "10000000000000002")))
        )
      )
      when(
        mockEnrolmentStoreProxyConnector
          .getAllEnrolmentsByGroupId(any[String])(meq(headerCarrier), any())
      ).thenReturn(Future.successful(enrolments))

      await(service.groupIdEnrolments(groupId)) shouldBe enrolments.enrolments

    }

    "return true if Eori is already linked to GGID for service name HMRC-CUS-ORG" in {
      when(mockEnrolmentStoreProxyConnector.getGroupIdsForCdsEnrolment(any[Eori])(meq(headerCarrier), any()))
        .thenReturn(Future.successful(groupIds))

      await(service.isEoriEnrolledWithAnotherGG(Eori("GB111111111111"))) shouldBe true

      verify(mockEnrolmentStoreProxyConnector).getGroupIdsForCdsEnrolment(any[Eori])(meq(headerCarrier), any())
    }

    "return false if Eori is not linked to GGID for service name HMRC-CUS-ORG" in {
      when(mockEnrolmentStoreProxyConnector.getGroupIdsForCdsEnrolment(any[Eori])(meq(headerCarrier), any()))
        .thenReturn(Future.successful(GroupIds(List.empty[String], List.empty[String])))

      await(service.isEoriEnrolledWithAnotherGG(Eori("GB111111111111"))) shouldBe false

      verify(mockEnrolmentStoreProxyConnector).getGroupIdsForCdsEnrolment(any[Eori])(meq(headerCarrier), any())
    }
  }
}
