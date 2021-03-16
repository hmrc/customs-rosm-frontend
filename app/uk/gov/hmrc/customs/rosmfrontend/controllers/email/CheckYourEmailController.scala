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

package uk.gov.hmrc.customs.rosmfrontend.controllers.email

import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.email.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.MatchingIdController
import uk.gov.hmrc.customs.rosmfrontend.controllers.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.{CdsController, FeatureFlags}
import uk.gov.hmrc.customs.rosmfrontend.domain.{InternalId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.email.EmailForm.{YesNo, confirmEmailYesNoAnswerForm}
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.Save4LaterService
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.email.EmailVerificationService
import uk.gov.hmrc.customs.rosmfrontend.views.html.email.{check_your_email, email_confirmed, verify_your_email}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CheckYourEmailController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  save4LaterService: Save4LaterService,
  cdsFrontendDataCache: SessionCache,
  mcc: MessagesControllerComponents,
  checkYourEmailView: check_your_email,
  emailConfirmedView: email_confirmed,
  verifyYourEmail: verify_your_email,
  emailVerificationService: EmailVerificationService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with FeatureFlags {

  private def populateView(email: Option[String], isInReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    Future.successful(
      Ok(checkYourEmailView(email, confirmEmailYesNoAnswerForm, isInReviewMode = isInReviewMode, journey = journey))
    )

  private def populateEmailVerificationView(
    email: Option[String],
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    Future.successful(Ok(verifyYourEmail(email, journey = journey)))

  def createForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
      {
        save4LaterService.fetchEmail(InternalId(userWithEnrolments.internalId)) flatMap {
          _.fold {
            CdsLogger.warn("[CheckYourEmailController][createForm] -   emailStatus cache none")
            populateView(None, isInReviewMode = false, journey = journey)
          } { emailStatus =>
            populateView(Some(emailStatus.email), isInReviewMode = false, journey = journey)
          }
        }
      }
    }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
      confirmEmailYesNoAnswerForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            save4LaterService
              .fetchEmail(InternalId(userWithEnrolments.internalId))
              .flatMap {
                _.fold {
                  CdsLogger.warn("[CheckYourEmailController][submit] -   emailStatus cache none")
                  Future(
                    BadRequest(
                      checkYourEmailView(None, formWithErrors, isInReviewMode = isInReviewMode, journey = journey)
                    )
                  )
                } { emailStatus =>
                  Future(
                    BadRequest(
                      checkYourEmailView(
                        Some(emailStatus.email),
                        formWithErrors,
                        isInReviewMode = isInReviewMode,
                        journey = journey
                      )
                    )
                  )
                }
              }
          },
          yesNoAnswer =>
            locationByAnswer(InternalId(userWithEnrolments.internalId), yesNoAnswer, isInReviewMode, journey)
        )
    }

  def verifyEmailView(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
      {
        save4LaterService.fetchEmail(InternalId(userWithEnrolments.internalId)) flatMap { emailStatus =>
          emailStatus.fold {
            CdsLogger.warn("[CheckYourEmailController][verifyEmailView] -  emailStatus cache none")
            populateEmailVerificationView(None, journey = journey)
          } { email =>
            populateEmailVerificationView(Some(email.email), journey = journey)
          }
        }
      }
    }

  def emailConfirmed(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
      {
        save4LaterService.fetchEmail(InternalId(userWithEnrolments.internalId)) flatMap { emailStatus =>
          emailStatus.fold {
            CdsLogger.warn("[CheckYourEmailController][emailConfirmed] -  emailStatus cache none")
            Future.successful(Redirect(SecuritySignOutController.signOut(journey)))
          } { email =>
            {
              if (email.isConfirmed.getOrElse(false)) {
                Future.successful(toResult(journey))
              } else {
                save4LaterService
                  .saveEmail(InternalId(userWithEnrolments.internalId), email.copy(isConfirmed = Some(true)))
                  .map { _ =>
                    Ok(emailConfirmedView(journey))
                  }

              }
            }
          }
        }
      }

    }

  def emailConfirmedContinue(journey: Journey.Value): Action[AnyContent] =
    Action { implicit request =>
      toResult(journey)
    }

  def toResult(journey: Journey.Value)(implicit request: Request[AnyContent], hc: HeaderCarrier): Result =
    journey match {
      case Journey.GetYourEORI =>
        Redirect(MatchingIdController.matchWithIdOnly())
      case Journey.Migrate =>
        Redirect(MatchingIdController.matchWithIdOnlyForExistingReg())
    }

  private def submitNewDetails(internalId: InternalId, isInReviewMode: Boolean, journey: Journey.Value)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    save4LaterService.fetchEmail(internalId) flatMap {
      _.fold {
        CdsLogger.warn("[CheckYourEmailController][submitNewDetails] -  emailStatus cache none")
        throw new IllegalStateException("[CheckYourEmailController][submitNewDetails] - emailStatus cache none")
      } { emailStatus =>
        emailVerificationService.createEmailVerificationRequest(emailStatus.email, EmailController.form(journey).url) flatMap {
          case Some(true) =>
            Future.successful(Redirect(CheckYourEmailController.verifyEmailView(journey)))
          case Some(false) => {
            CdsLogger.warn(
              "[CheckYourEmailController][sendVerification] - " +
                "Unable to send email verification request. Service responded with 'already verified'"
            )
            save4LaterService
              .saveEmail(internalId, emailStatus.copy(isVerified = true))
              .flatMap { _ =>
                cdsFrontendDataCache.saveEmail(emailStatus.email).map { _ =>
                  Redirect(EmailController.form(journey))
                }
              }
          }
          case _ =>
            throw new IllegalStateException("CreateEmailVerificationRequest Failed")
        }
      }
    }

  private def locationByAnswer(
    internalId: InternalId,
    yesNoAnswer: YesNo,
    isInReviewMode: Boolean,
    journey: Journey.Value
  )(implicit request: Request[AnyContent]): Future[Result] = yesNoAnswer match {
    case theAnswer if theAnswer.isYes =>
      submitNewDetails(internalId, isInReviewMode, journey)
    case _ => Future(Redirect(WhatIsYourEmailController.createForm(journey)))
  }
}
