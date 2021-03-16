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

package uk.gov.hmrc.customs.rosmfrontend.controllers

import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.email.routes.CheckYourEmailController
import uk.gov.hmrc.customs.rosmfrontend.controllers.email.routes.WhatIsYourEmailController.createForm
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.EoriExistsController
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes.{EnrolmentExistsAgainstGroupIdController, EnrolmentPendingAgainstGroupIdController}
import uk.gov.hmrc.customs.rosmfrontend.domain.{GroupId, InternalId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.email.EmailStatus
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.email.EmailVerificationService
import uk.gov.hmrc.customs.rosmfrontend.services.{Save4LaterService, UserGroupIdSubscriptionStatusCheckService}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  emailVerificationService: EmailVerificationService,
  sessionCache: SessionCache,
  mcc: MessagesControllerComponents,
  save4LaterService: Save4LaterService,
  userGroupIdSubscriptionStatusCheckService: UserGroupIdSubscriptionStatusCheckService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def groupIsEnrolled(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    Future.successful(Redirect(EnrolmentExistsAgainstGroupIdController.show(journey)))
  private def userIsInProcess(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    continue(journey)

  private def otherUserWithinGroupIsInProcess(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    Future.successful(Redirect(EnrolmentPendingAgainstGroupIdController.show(journey)))

  private def continue(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    save4LaterService.fetchEmail(InternalId(user.internalId)) flatMap {
      _.fold {
        CdsLogger.warn(s"[EmailController][form] -  emailStatus cache none ${user.internalId}")
        Future.successful(Redirect(createForm(journey)))
      } { cachedEmailStatus =>
        if (cachedEmailStatus.isVerified) {
          sessionCache.saveEmail(cachedEmailStatus.email) map { _ =>
            Redirect(CheckYourEmailController.emailConfirmed(journey))
          }
        } else checkWithEmailService(cachedEmailStatus, journey)
      }
    }

  def form(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user: LoggedInUserWithEnrolments =>
      if (journey == Journey.GetYourEORI) {
        userGroupIdSubscriptionStatusCheckService.userOrGroupHasAnEori(GroupId(user.groupId)).flatMap {
          case Some(eori) =>
            sessionCache.saveEori(eori).map { _ =>
              Redirect(EoriExistsController.eoriExist(journey))
            }
          case None => {
            userGroupIdSubscriptionStatusCheckService
              .checksToProceed(GroupId(user.groupId), InternalId(user.internalId)) {
                continue(journey)
              } { groupIsEnrolled(journey) } {
                userIsInProcess(journey)
              } { otherUserWithinGroupIsInProcess(journey) }
          }
        }
      } else {
        userGroupIdSubscriptionStatusCheckService.checksToProceed(GroupId(user.groupId), InternalId(user.internalId)) {
          continue(journey)
        } { groupIsEnrolled(journey) } {
          userIsInProcess(journey)
        } { otherUserWithinGroupIsInProcess(journey) }
      }

    }

  private def checkWithEmailService(emailStatus: EmailStatus, journey: Journey.Value)(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier,
    userWithEnrolments: LoggedInUserWithEnrolments
  ): Future[Result] =
    emailVerificationService.isEmailVerified(emailStatus.email).flatMap {
      case Some(true) => {
        for {
          _ <- {
            CdsLogger.warn("updated verified email status true to save4later")
            save4LaterService.saveEmail(InternalId(userWithEnrolments.internalId), emailStatus.copy(isVerified = true))
          }
          _ <- {
            CdsLogger.warn("saved verified email address true to cache")
            sessionCache.saveEmail(emailStatus.email)
          }
        } yield Redirect(CheckYourEmailController.emailConfirmed(journey))
      }
      case Some(false) => {
        CdsLogger.warn("verified email address false")
        Future.successful(Redirect(CheckYourEmailController.verifyEmailView(journey)))
      }
      case _ => {
        CdsLogger.error("Couldn't verify email address")
        Future.successful(Redirect(CheckYourEmailController.verifyEmailView(journey)))
      }
    }
}
