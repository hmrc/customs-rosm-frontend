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

import play.api.libs.json._
import play.mvc.Http.Status._
import uk.gov.hmrc.customs.rosmfrontend.audit.Auditable
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.http.{BadRequestException, HttpClient, _}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class Save4LaterConnector @Inject()(http: HttpClient, appConfig: AppConfig, audit: Auditable) {

  val LoggerComponentId = "Save4LaterConnector"

  def get[T](
    id: String,
    key: String
  )(implicit hc: HeaderCarrier, reads: Reads[T], writes: Writes[T]): Future[Option[T]] = {
    val url = s"${appConfig.handleSubscriptionBaseUrl}/save4later/$id/$key"
    CdsLogger.info(s"[$LoggerComponentId][call] GET: $url")
    http.GET[HttpResponse](url) map { response =>
      CdsLogger.info(s"[$LoggerComponentId][call] complete for call to $url and headers ${hc.headers}")

      response.status match {
        case OK => {
          auditCallResponse(url, response.json)
          Some(response.json.as[T])
        }
        case NOT_FOUND => {
          auditCallResponse(url, response.status)
          None
        }
        case _ => throw new BadRequestException(s"Status:${response.status}")
      }
    } recoverWith {
      case NonFatal(e) =>
        CdsLogger.error(
          s"[$LoggerComponentId][call] request failed for call to $url and headers ${hc.headers}: ${e.getMessage}",
          e
        )
        Future.failed(e)
    }
  }

  def put[T](
    id: String,
    key: String,
    payload: JsValue
  )(implicit hc: HeaderCarrier, reads: Reads[T], writes: Writes[T]): Future[Unit] = {
    val url = s"${appConfig.handleSubscriptionBaseUrl}/save4later/$id/$key"
    CdsLogger.info(s"[$LoggerComponentId][call] PUT: $url")
    auditCallRequest(url, payload)
    http.PUT[JsValue, HttpResponse](url, payload) map { response =>
      CdsLogger.info(s"[$LoggerComponentId][call] complete for call to $url and headers ${hc.headers}")
      auditCallResponse(url, response.status)
      response.status match {
        case NO_CONTENT | CREATED | OK => ()
        case _                         => throw new BadRequestException(s"Status:${response.status}")
      }
    } recoverWith {
      case NonFatal(e) =>
        CdsLogger.error(
          s"[$LoggerComponentId][call] request failed for call to $url and headers ${hc.headers}: ${e.getMessage}",
          e
        )
        Future.failed(e)
    }
  }

  def delete[T](id: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    val url = s"${appConfig.handleSubscriptionBaseUrl}/save4later/$id"
    CdsLogger.info(s"[$LoggerComponentId][call] DELETE: $url")
    auditCallRequest(url, JsNull)
    http.DELETE[HttpResponse](url) map { response =>
      CdsLogger.info(s"[$LoggerComponentId][call] complete for call to $url and headers ${hc.headers}")
      auditCallResponse(url, response.status)
      response.status match {
        case NO_CONTENT => ()
        case _          => throw new BadRequestException(s"Status:${response.status}")
      }
    } recoverWith {
      case NonFatal(e) =>
        CdsLogger.error(
          s"[$LoggerComponentId][call] request failed for call to $url and headers ${hc.headers}: ${e.getMessage}",
          e
        )
        Future.failed(e)
    }
  }

  private def auditCallRequest[T](
    url: String,
    request: JsValue
  )(implicit hc: HeaderCarrier, reads: HttpReads[T]): Future[Unit] =
    Future {
      audit.sendExtendedDataEvent(
        transactionName = "Save4laterRequest",
        path = url,
        details = request,
        eventType = "Save4later"
      )
    }

  private def auditCallResponse[T](
    url: String,
    response: T
  )(implicit hc: HeaderCarrier, writes: Writes[T]): Future[Unit] =
    Future {
      audit.sendExtendedDataEvent(
        transactionName = "Save4laterResponse",
        path = url,
        details = Json.toJson(response),
        eventType = "Save4later"
      )
    }

}
