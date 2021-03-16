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
import uk.gov.hmrc.customs.rosmfrontend.domain.{RegisterWithoutIdRequestHolder, _}
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class RegisterWithoutIdConnector @Inject()(http: HttpClient, appConfig: AppConfig, audit: Auditable) {

  private val url = appConfig.getServiceUrl("register-without-id")
  private val loggerComponentId = "RegisterWithoutIdConnector"

  def register(request: RegisterWithoutIDRequest)(implicit hc: HeaderCarrier): Future[RegisterWithoutIDResponse] = {
    auditCallRequest(url, request)
    http.POST[RegisterWithoutIdRequestHolder, RegisterWithoutIdResponseHolder](
      url,
      RegisterWithoutIdRequestHolder(request)
    ) map { resp =>
      CdsLogger.info(
        s"[$loggerComponentId] Successful. postUrl $url, acknowledgement ref: ${request.requestCommon.acknowledgementReference}, response status: ${resp.registerWithoutIDResponse.responseCommon.statusText}"
      )
      auditCallResponse(url, resp)
      resp.registerWithoutIDResponse
    } recover {
      case e: Throwable =>
        CdsLogger.debug(
          s"[$loggerComponentId] Failure. postUrl: $url, acknowledgement ref: ${request.requestCommon.acknowledgementReference}, error: $e"
        )
        throw e
    }
  }

  private def auditCallRequest(url: String, request: RegisterWithoutIDRequest)(implicit hc: HeaderCarrier): Unit =
    audit.sendDataEvent(
      transactionName = "customs-registration-without-id",
      path = url,
      detail = Map("txName" -> "CustomsRegistrationWithoutIdSubmitted") ++ request.requestDetail.keyValueMap(),
      eventType = "CustomsRegistrationWithoutIdSubmitted"
    )

  private def auditCallResponse(url: String, response: RegisterWithoutIdResponseHolder)(
    implicit hc: HeaderCarrier
  ): Unit =
    audit.sendDataEvent(
      transactionName = "customs-registration-without-id",
      path = url,
      detail = Map("txName" -> "CustomsRegistrationWithoutIdResult") ++ response.registerWithoutIDResponse
        .keyValueMap(),
      eventType = "CustomsRegistrationWithoutIdResult"
    )
}
