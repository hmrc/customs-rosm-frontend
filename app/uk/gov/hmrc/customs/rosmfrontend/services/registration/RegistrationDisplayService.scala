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

package uk.gov.hmrc.customs.rosmfrontend.services.registration

import java.time.Clock

import javax.inject.Inject
import org.joda.time.{DateTime, DateTimeZone}
import uk.gov.hmrc.customs.rosmfrontend.connector.{EoriHttpResponse, RegistrationDisplayConnector}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.RequestParameter
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.registration._
import uk.gov.hmrc.customs.rosmfrontend.domain.{RegistrationDetails, SafeId}
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class RegistrationDisplayService @Inject()(
  sessionCache: SessionCache,
  connector: RegistrationDisplayConnector,
  creator: RegistrationDetailsCreator
) {

  def requestDetails(
    safeId: SafeId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[EoriHttpResponse, RegistrationDisplayResponse]] =
    connector.registrationDisplay(buildRequest(safeId))

  private def buildRequest(safeId: SafeId): RegistrationDisplayRequestHolder =
    RegistrationDisplayRequestHolder(
      RegistrationDisplayRequest(
        RequestCommon(
          new DateTime(Clock.systemUTC().instant.toEpochMilli, DateTimeZone.UTC),
          Seq(
            RequestParameter("REGIME", "CDS"),
            RequestParameter("ID_Type", "SAFE"),
            RequestParameter("ID_Value", safeId.id)
          )
        )
      )
    )

  def cacheDetails(
    response: RegistrationDisplayResponse
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val registrationDetails: RegistrationDisplayResponse => RegistrationDetails = details =>
      creator.registrationDetails(details)
    sessionCache.saveRegistrationDetails(registrationDetails(response))
  }
}
