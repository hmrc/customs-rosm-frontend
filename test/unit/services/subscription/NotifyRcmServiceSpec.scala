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
import uk.gov.hmrc.customs.rosmfrontend.connector.NotifyRcmConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.NotifyRcmRequest
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.NotifyRcmService
import uk.gov.hmrc.http.HeaderCarrier
import util.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NotifyRcmServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfter {

  private val mockNotifyRcmConnector = mock[NotifyRcmConnector]
  private val mockSessionCache = mock[SessionCache]
  private val mockSubscriptionDetailsHolder = mock[SubscriptionDetails]

  private val service = new NotifyRcmService(mockSessionCache, mockNotifyRcmConnector)
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  before {
    reset(mockSessionCache, mockSessionCache)
  }

  "NotifyRcmService" should {
    "notifyRcm" in {
      when(mockSessionCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSessionCache.email).thenReturn("test@test.com")
      when(mockSubscriptionDetailsHolder.name).thenReturn("name")
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("GB0000000000"))
      when(mockNotifyRcmConnector.notifyRCM(any[NotifyRcmRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      await(service.notifyRcm()) shouldBe (())
      verify(mockNotifyRcmConnector).notifyRCM(any[NotifyRcmRequest])(meq(headerCarrier))
    }
  }
}
