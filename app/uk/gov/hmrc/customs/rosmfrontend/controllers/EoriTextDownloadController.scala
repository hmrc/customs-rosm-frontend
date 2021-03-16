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

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.eori_number_text_download

import scala.concurrent.ExecutionContext

@Singleton
class EoriTextDownloadController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  cdsFrontendDataCache: SessionCache,
  eoriNumberTextDownloadView: eori_number_text_download,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def download(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction(implicit request => { _: LoggedInUserWithEnrolments =>
      for {
        Some(eori) <- cdsFrontendDataCache.subscriptionCreateOutcome.map(_.eori)
        name <- cdsFrontendDataCache.subscriptionCreateOutcome.map(_.fullName.trim)
        processedDate <- cdsFrontendDataCache.subscriptionCreateOutcome
          .map(_.processedDate)
      } yield {
        Ok(eoriNumberTextDownloadView(eori, name, processedDate))
          .as("plain/text")
          .withHeaders(CONTENT_DISPOSITION -> "attachment; filename=EORI-number.txt")
      }
    })
}
