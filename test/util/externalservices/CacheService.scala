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

package util.externalservices

import play.api.Application
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.SessionId

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object CacheService {

  def addRegistrationDetailsToCache(sessionId: String, reviewDetails: RegistrationDetails)(
    implicit app: Application
  ): Unit =
    awaitResult(cache(app).saveRegistrationDetails(reviewDetails)(hc(sessionId)))

  def addSubscriptionStatusOutcomeToCache(sessionId: String, outcome: SubscriptionStatusOutcome)(implicit app: Application): Unit =
    awaitResult(cache(app).saveSubscriptionStatusOutcome(outcome)(hc(sessionId)))

  def addSubscribeOutcomeToCache(sessionId: String, outcome: SubscriptionCreateOutcome)(implicit app: Application): Unit =
    awaitResult(cache(app).saveSubscriptionCreateOutcome(outcome)(hc(sessionId)))

  def addSubscriptionDetailsHolderToCache(sessionId: String, holder: SubscriptionDetails = SubscriptionDetails())(
    implicit app: Application
  ): Unit =
    awaitResult(cache(app).saveSubscriptionDetails(holder)(hc(sessionId)))

  def addEoriToCache(sessionId: String, eori: Eori)(implicit app: Application): Unit =
    awaitResult(cache(app).saveEori(eori)(hc(sessionId)))

  private def cache(app: Application) = app.injector.instanceOf[SessionCache]
  private def hc(sessionId: String) = HeaderCarrier(sessionId = Some(SessionId(sessionId)))
  private def awaitResult[T](future: Future[T], atMost: Duration = 5.seconds): T = Await.result(future, atMost)
}
