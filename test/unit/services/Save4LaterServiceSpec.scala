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

package unit.services

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Span}
import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.customs.rosmfrontend.connector.Save4LaterConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.{CdsOrganisationType, InternalId, SafeId}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.email.EmailStatus
import uk.gov.hmrc.customs.rosmfrontend.services.Save4LaterService
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Save4LaterServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ScalaFutures {
  private val mockSave4LaterConnector = mock[Save4LaterConnector]

  private implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  private val safeId = SafeId("safeId")
  private val internalId = InternalId("internalId-123")
  private val organisationType: CdsOrganisationType =
    CdsOrganisationType.Company
  private val emailStatus = EmailStatus("test@example.com")

  private val safeIdKey = "safeId"
  private val orgTypeKey = "orgType"
  private val emailKey = "email"

  private val service =
    new Save4LaterService(mockSave4LaterConnector)

  override implicit def patienceConfig: PatienceConfig =
    super.patienceConfig.copy(timeout = Span(defaultTimeout.toMillis, Millis))

  override protected def beforeEach(): Unit =
    reset(mockSave4LaterConnector)

  "Save4LaterService" should {
    "save the safeId against the users InternalId" in {
      when(
        mockSave4LaterConnector.put[SafeId](
          ArgumentMatchers.eq(internalId.id),
          ArgumentMatchers.eq(safeIdKey),
          ArgumentMatchers.eq(safeId)
        )(any[HeaderCarrier], any[Reads[SafeId]], any[Writes[SafeId]])
      ).thenReturn(Future.successful(()))

      val result: Unit = service
        .saveSafeId(internalId, safeId)
        .futureValue
      result shouldBe (())
    }

    "save the CdsOrganisationType against the users InternalId" in {
      when(
        mockSave4LaterConnector.put[CdsOrganisationType](
          ArgumentMatchers.eq(internalId.id),
          ArgumentMatchers.eq(orgTypeKey),
          ArgumentMatchers.eq(Some(organisationType))
        )(any[HeaderCarrier], any[Reads[CdsOrganisationType]], any[Writes[CdsOrganisationType]])
      ).thenReturn(Future.successful(()))

      val result: Unit = service
        .saveOrgType(internalId, Some(organisationType))
        .futureValue
      result shouldBe (())
    }

    "save the email against the users InternalId" in {
      when(
        mockSave4LaterConnector.put[EmailStatus](
          ArgumentMatchers.eq(internalId.id),
          ArgumentMatchers.eq(emailKey),
          ArgumentMatchers.eq(emailStatus)
        )(any[HeaderCarrier], any[Reads[EmailStatus]], any[Writes[EmailStatus]])
      ).thenReturn(Future.successful(()))

      val result: Unit = service
        .saveEmail(internalId, emailStatus)
        .futureValue
      result shouldBe (())
    }

    "fetch the SafeId for the users InternalId" in {
      when(
        mockSave4LaterConnector.get[SafeId](ArgumentMatchers.eq(internalId.id), ArgumentMatchers.eq(safeIdKey))(
          any[HeaderCarrier],
          any[Reads[SafeId]],
          any[Writes[SafeId]]
        )
      ).thenReturn(Future.successful(Some(safeId)))

      val result = service
        .fetchSafeId(internalId)
        .futureValue
      result shouldBe Some(safeId)
    }

    "fetch the Organisation Type for the users InternalId" in {
      when(
        mockSave4LaterConnector.get[CdsOrganisationType](
          ArgumentMatchers.eq(internalId.id),
          ArgumentMatchers.eq(orgTypeKey)
        )(any[HeaderCarrier], any[Reads[CdsOrganisationType]], any[Writes[CdsOrganisationType]])
      ).thenReturn(Future.successful(Some(organisationType)))

      val result = service
        .fetchOrgType(internalId)
        .futureValue
      result shouldBe Some(organisationType)
    }

    "fetch the email for the users InternalId" in {
      when(
        mockSave4LaterConnector.get[EmailStatus](ArgumentMatchers.eq(internalId.id), ArgumentMatchers.eq(emailKey))(
          any[HeaderCarrier],
          any[Reads[EmailStatus]],
          any[Writes[EmailStatus]]
        )
      ).thenReturn(Future.successful(Some(emailStatus)))

      val result = service
        .fetchEmail(internalId)
        .futureValue
      result shouldBe Some(emailStatus)
    }
  }

}
