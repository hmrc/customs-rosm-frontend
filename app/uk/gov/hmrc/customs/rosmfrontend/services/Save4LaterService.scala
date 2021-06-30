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

package uk.gov.hmrc.customs.rosmfrontend.services

import uk.gov.hmrc.customs.rosmfrontend.connector.Save4LaterConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.{CdsOrganisationType, InternalId, SafeId}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.email.EmailStatus
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Save4LaterService @Inject()(save4LaterConnector: Save4LaterConnector)(implicit ec: ExecutionContext) {

  private val safeIdKey = "safeId"
  private val orgTypeKey = "orgType"
  private val emailKey = "email"

  def saveSafeId(internalId: InternalId, safeId: SafeId)(implicit hc: HeaderCarrier): Future[Unit] = {
    CdsLogger.info("saving SafeId to mongo")
    save4LaterConnector.put[SafeId](internalId.id, safeIdKey, safeId)
  }

  def saveOrgType(internalId: InternalId, mayBeOrgType: Option[CdsOrganisationType])(
    implicit hc: HeaderCarrier
  ): Future[Unit] = {
    CdsLogger.info("saving OrganisationType to mongo")
    save4LaterConnector
      .put[CdsOrganisationType](internalId.id, orgTypeKey, mayBeOrgType)
  }

  def saveEmail(internalId: InternalId, emailStatus: EmailStatus)(implicit hc: HeaderCarrier): Future[Unit] = {
    CdsLogger.info("saving email address to mongo")
    save4LaterConnector.put[EmailStatus](internalId.id, emailKey, emailStatus)
  }

  def fetchOrgType(internalId: InternalId)(implicit hc: HeaderCarrier): Future[Option[CdsOrganisationType]] = {
    CdsLogger.info("fetching OrganisationType from mongo")
    save4LaterConnector
      .get[CdsOrganisationType](internalId.id, orgTypeKey)
  }

  def fetchSafeId(internalId: InternalId)(implicit hc: HeaderCarrier): Future[Option[SafeId]] = {
    CdsLogger.info("fetching SafeId from mongo")
    save4LaterConnector
      .get[SafeId](internalId.id, safeIdKey)
  }

  def fetchEmail(internalId: InternalId)(implicit hc: HeaderCarrier): Future[Option[EmailStatus]] = {
    CdsLogger.info("fetching EmailStatus from mongo")
    save4LaterConnector
      .get[EmailStatus](internalId.id, emailKey)
  }

}
