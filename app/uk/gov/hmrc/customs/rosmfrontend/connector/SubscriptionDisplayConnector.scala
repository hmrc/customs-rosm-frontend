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

package uk.gov.hmrc.customs.rosmfrontend.connector

import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.{SubscriptionDisplayResponse, SubscriptionDisplayResponseHolder}
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
@Singleton
class SubscriptionDisplayConnector @Inject()(http: HttpClient, appConfig: AppConfig) {

  private val url = appConfig.getServiceUrl("subscription-display")
  private val loggerComponentId = "SubscriptionDisplayConnector"

  def subscriptionDisplay(
    subscriptionDisplayRequest: Seq[(String, String)]
  )(implicit hc: HeaderCarrier): Future[Either[EoriHttpResponse, SubscriptionDisplayResponse]] =
    http.GET[SubscriptionDisplayResponseHolder](url, subscriptionDisplayRequest) map { resp =>
      CdsLogger.info(s"[$loggerComponentId] subscription-display successful. url: $url")
      Right(resp.subscriptionDisplayResponse)
    } recover {
      case NonFatal(e) => {
        CdsLogger.error(s"[$loggerComponentId][status] subscription-display failed. url: $url, error: $e")
        Left(ServiceUnavailableResponse)
      }
    }
}
