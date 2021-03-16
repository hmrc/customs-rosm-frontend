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

package uk.gov.hmrc.customs.rosmfrontend.controllers.auth

import play.api.i18n.I18nSupport
import play.api.mvc.Results.Redirect
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Organisation}
import uk.gov.hmrc.auth.core.{AffinityGroup, CredentialRole, User}
import uk.gov.hmrc.customs.rosmfrontend.controllers.{routes, JourneyTypeFromUrl}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey

import scala.concurrent.Future

trait AccessController extends JourneyTypeFromUrl with AllowlistVerification with I18nSupport {

  def permitUserOrRedirect(
    affinityGroup: Option[AffinityGroup],
    credentialRole: Option[CredentialRole],
    email: Option[String]
  )(action: Future[Result])(implicit request: Request[AnyContent]): Future[Result] =
    (isPermittedUserType(affinityGroup, credentialRole), isPermitted(email)) match {
      case (true, true)  => action
      case (true, false) => Future.successful(Redirect(routes.YouCannotUseServiceController.unauthorisedPage()))
      case (false, _)    => Future.successful(Redirect(routes.YouCannotUseServiceController.page(journeyFromUrl)))
    }

  def isPermitted(email: Option[String])(implicit request: Request[AnyContent]): Boolean =
    journeyFromUrl == Journey.GetYourEORI || isAllowlisted(email)

  def isPermittedUserType(affinityGroup: Option[AffinityGroup], credentialRole: Option[CredentialRole]): Boolean =
    affinityGroup match {
      case Some(Agent)        => false
      case Some(Organisation) => credentialRole.fold(false)(cr => cr == User)
      case _                  => true
    }
}
