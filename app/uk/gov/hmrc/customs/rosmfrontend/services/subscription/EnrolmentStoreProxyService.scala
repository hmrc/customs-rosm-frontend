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

package uk.gov.hmrc.customs.rosmfrontend.services.subscription

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.customs.rosmfrontend.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.{EnrolmentResponse, Eori, GroupId}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnrolmentStoreProxyService @Inject()(enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector) {

  private val state = "Activated"
  private val service = "HMRC-CUS-ORG"

  def isEnrolmentAssociatedToGroup(
    groupId: GroupId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    enrolmentStoreProxyConnector
      .getCdsEnrolmentByGroupId(groupId.id)
      .map(_.enrolments)
      .map { enrolment =>
        enrolment.exists(x => x.state == state && x.service == service)
      }

  def groupIdEnrolments(
    groupId: GroupId
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[EnrolmentResponse]] =
    enrolmentStoreProxyConnector.getAllEnrolmentsByGroupId(groupId.id).map(_.enrolments)

  def isEoriEnrolledWithAnotherGG(eori: Eori)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    enrolmentStoreProxyConnector.getGroupIdsForCdsEnrolment(eori).map { groupIds =>
      groupIds.principalGroupIds.nonEmpty
    }

}
