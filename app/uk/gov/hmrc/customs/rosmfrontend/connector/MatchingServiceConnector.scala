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

import uk.gov.hmrc.customs.rosmfrontend.audit.Auditable
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching._
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class MatchingServiceConnector @Inject()(http: HttpClient, appConfig: AppConfig, audit: Auditable) {

  private val url = appConfig.getServiceUrl("register-with-id")
  private val loggerComponentId = "MatchingServiceConnector"
  val NoMatchFound = "002 - No Match Found"

  def handleResponse(response: MatchingResponse): Option[MatchingResponse] = {
    val statusTxt = response.registerWithIDResponse.responseCommon.statusText
    if (statusTxt.exists(_.equalsIgnoreCase(NoMatchFound))) None
    else Some(response)
  }

  def lookup(req: MatchingRequestHolder)(implicit hc: HeaderCarrier): Future[Option[MatchingResponse]] = {
    CdsLogger.info(
      s"[$loggerComponentId][lookup] RegisterWithID postUrl: $url,  acknowledgement ref: ${req.registerWithIDRequest.requestCommon.acknowledgementReference}"
    )
    auditCallRequest(url, req)
    http.POST[MatchingRequestHolder, MatchingResponse](url, req) map { resp =>
      CdsLogger.info(
        s"[MatchingServiceConnector][lookup] RegisterWithID business match found for acknowledgement ref: ${req.registerWithIDRequest.requestCommon.acknowledgementReference}"
      )
      auditCallResponse(url, resp)
      handleResponse(resp)
    } recover {
      case e: Throwable =>
        CdsLogger.info(
          s"[$loggerComponentId][lookup] RegisterWithID Match request failed for acknowledgement ref: ${req.registerWithIDRequest.requestCommon.acknowledgementReference}. Reason: $e"
        )
        throw e
    }

  }

  private def auditCallRequest(url: String, request: MatchingRequestHolder)(implicit hc: HeaderCarrier): Unit =
    audit.sendDataEvent(
      transactionName = "customs-registration-with-id",
      path = url,
      detail = request.keyValueMap(),
      eventType = "customsRegistrationWithIdSubmitted"
    )

  private def auditCallResponse(url: String, response: MatchingResponse)(implicit hc: HeaderCarrier): Unit =
    audit.sendExtendedDataEvent(
      transactionName = "customs-registration-with-id",
      path = url,
      details = response.jsObject(),
      eventType = "customsRegistrationWithIdConfirmation"
    )
}
