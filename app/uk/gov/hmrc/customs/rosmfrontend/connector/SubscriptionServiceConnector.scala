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

import play.libs.Json
import uk.gov.hmrc.customs.rosmfrontend.audit.Auditable
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.{SubscriptionRequest, SubscriptionResponse}
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpClient}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class SubscriptionServiceConnector @Inject()(http: HttpClient, appConfig: AppConfig, audit: Auditable) {

  private val url = appConfig.getServiceUrl("subscribe")
  private val loggerComponentId = "SubscriptionServiceConnector"

  def subscribe(request: SubscriptionRequest)(implicit hc: HeaderCarrier): Future[SubscriptionResponse] = {
    val loggerId = s"[$loggerComponentId][subscribe] Subscription Create"
    auditCallRequest(request, url)
    http.POST[SubscriptionRequest, SubscriptionResponse](url, request) map { response =>
      CdsLogger.info(
        s"$loggerId complete for acknowledgementReference : ${request.subscriptionCreateRequest.requestCommon.acknowledgementReference}"
      )
      auditCallResponse(response, url)
      response
    } recoverWith {
      case e: BadRequestException =>
        CdsLogger.error(
          s"$loggerId request failed for acknowledgementReference : ${request.subscriptionCreateRequest.requestCommon.acknowledgementReference}. Reason: $e"
        )
        Future.failed(e)
      case NonFatal(e) =>
        CdsLogger.error(
          s"$loggerId request failed for acknowledgementReference : ${request.subscriptionCreateRequest.requestCommon.acknowledgementReference}. Reason: $e"
        )
        Future.failed(e)
    }
  }

  private def auditCallRequest(request: SubscriptionRequest, url: String)(implicit hc: HeaderCarrier): Unit =
    audit.sendDataEvent(
      transactionName = "customs-subscription",
      path = url,
      detail = Map("txName" -> "SubscriptionSubmitted") ++ request.subscriptionCreateRequest.keyValueMap(),
      eventType = "SubscriptionSubmitted"
    )

  private def auditCallResponse(response: SubscriptionResponse, url: String)(implicit hc: HeaderCarrier): Unit =
    audit.sendDataEvent(
      transactionName = "customs-subscription",
      path = url,
      detail = Map("txName" -> "SubscriptionResult") ++ response.subscriptionCreateResponse.keyValueMap(),
      eventType = "SubscriptionResult"
    )
}
