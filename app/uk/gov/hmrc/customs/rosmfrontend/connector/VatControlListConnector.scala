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
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatControlListConnector @Inject()(http: HttpClient, appConfig: AppConfig)(implicit ec: ExecutionContext) {

  private val url = appConfig.getServiceUrl("vat-known-facts-control-list")
  private val loggerComponentId = "VatControlListConnector"

  def vatControlList(
    request: VatControlListRequest
  )(implicit hc: HeaderCarrier): Future[Either[EoriHttpResponse, VatControlListResponse]] =
    http.GET[VatControlListResponse](url, request.queryParams) map { resp =>
      CdsLogger.info(s"[$loggerComponentId] vat-known-facts-control-list successful. url: $url")
      Right(resp)
    } recover {
      case Upstream4xxResponse(_, 404, _, _) =>
        Left(NotFoundResponse)
      case _: BadRequestException         => Left(InvalidResponse)
      case _: ServiceUnavailableException => Left(ServiceUnavailableResponse)
      case e: Throwable =>
        CdsLogger.error(s"[$loggerComponentId][status] vat-known-facts-control-list failed. url: $url, error: $e", e)
        throw e
    }
}
