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

import javax.inject.{Inject, Singleton}
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.customs.rosmfrontend.connector.Save4LaterConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.auth.EnrolmentExtractor
import uk.gov.hmrc.customs.rosmfrontend.domain.{CacheIds, Eori, GroupId, InternalId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.customs.rosmfrontend.services.cache.CachedData
import uk.gov.hmrc.customs.rosmfrontend.services.subscription._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserGroupIdSubscriptionStatusCheckService @Inject()(
  subscriptionStatusService: SubscriptionStatusService,
  enrolmentStoreProxyService: EnrolmentStoreProxyService,
  save4LaterConnector: Save4LaterConnector
)(implicit ec: ExecutionContext)
    extends EnrolmentExtractor {
  private val idType = "SAFE"

  def checksToProceed(groupId: GroupId, internalId: InternalId)(
    continue: => Future[Result]
  )(groupIsEnrolled: => Future[Result])(userIsInProcess: => Future[Result])(
    otherUserWithinGroupIsInProcess: => Future[Result]
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    enrolmentStoreProxyService.isEnrolmentAssociatedToGroup(groupId).flatMap {
      case true => groupIsEnrolled //Block the user
      case false => {
        save4LaterConnector
          .get[CacheIds](groupId.id, CachedData.groupIdKey)
          .flatMap {
            case Some(cacheIds) => {
              subscriptionStatusService
                .getStatus(idType, cacheIds.safeId.id)
                .flatMap {
                  case NewSubscription | SubscriptionRejected => {
                    save4LaterConnector
                      .delete(groupId.id)
                      .flatMap(_ => continue) // Delete and then proceed normal
                  }
                  case _ => {
                    if (cacheIds.internalId == internalId) {
                      userIsInProcess
                    } else {
                      otherUserWithinGroupIsInProcess
                    }
                  }
                }
            }
            case _ => {
              continue
            }
          }
      }
    }

  def userOrGroupHasAnEori(
    groupId: GroupId
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments, hc: HeaderCarrier): Future[Option[Eori]] =
    enrolmentStoreProxyService.groupIdEnrolments(groupId).map {
      existingEoriForUserOrGroup(user, _)
    }
}
