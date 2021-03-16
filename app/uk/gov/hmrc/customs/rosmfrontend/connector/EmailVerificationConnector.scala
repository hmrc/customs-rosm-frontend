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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.customs.rosmfrontend.audit.Auditable
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.connector.EmailVerificationKeys.{LinkExpiryDurationKey, TemplateIdKey, _}
import uk.gov.hmrc.customs.rosmfrontend.connector.httpparsers.EmailVerificationRequestHttpParser.EmailVerificationRequestResponse
import uk.gov.hmrc.customs.rosmfrontend.connector.httpparsers.EmailVerificationStateHttpParser.EmailVerificationStateResponse
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailVerificationConnector @Inject()(http: HttpClient, appConfig: AppConfig, audit: Auditable)(
  implicit ec: ExecutionContext
) {

  private[connector] lazy val checkVerifiedEmailUrl: String =
    s"${appConfig.emailVerificationBaseUrl}/${appConfig.emailVerificationServiceContext}/verified-email-check"

  private[connector] lazy val createEmailVerificationRequestUrl: String =
    s"${appConfig.emailVerificationBaseUrl}/${appConfig.emailVerificationServiceContext}/verification-requests"

  def getEmailVerificationState(
    emailAddress: String
  )(implicit hc: HeaderCarrier): Future[EmailVerificationStateResponse] =
    http.POST[JsObject, EmailVerificationStateResponse](checkVerifiedEmailUrl, Json.obj("email" -> emailAddress))

  def createEmailVerificationRequest(emailAddress: String, continueUrl: String)(
    implicit hc: HeaderCarrier
  ): Future[EmailVerificationRequestResponse] = {
    val jsonBody =
      Json.obj(
        EmailKey -> emailAddress,
        TemplateIdKey -> appConfig.emailVerificationTemplateId,
        TemplateParametersKey -> Json.obj(),
        LinkExpiryDurationKey -> appConfig.emailVerificationLinkExpiryDuration,
        ContinueUrlKey -> continueUrl
      )
    http.POST[JsObject, EmailVerificationRequestResponse](createEmailVerificationRequestUrl, jsonBody)
  }
}

object EmailVerificationKeys {
  val EmailKey = "email"
  val TemplateIdKey = "templateId"
  val TemplateParametersKey = "templateParameters"
  val LinkExpiryDurationKey = "linkExpiryDuration"
  val ContinueUrlKey = "continueUrl"
}
