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
import uk.gov.hmrc.customs.rosmfrontend.connector.PdfGeneratorConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.{LoggedInUserWithEnrolments, SubscriptionCreateOutcome}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.error_template
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.eori_number_download

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EoriDownloadController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  cdsFrontendDataCache: SessionCache,
  mcc: MessagesControllerComponents,
  errorTemplateView: error_template,
  eoriNumberDownloadView: eori_number_download,
  pdfGenerator: PdfGeneratorConnector
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def download(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      cdsFrontendDataCache.subscriptionCreateOutcome.map {
        case SubscriptionCreateOutcome(processedDate, fullName, Some(eori)) =>
          Right(eoriNumberDownloadView(journey, eori, fullName, processedDate).body)
        case _ => Left(InternalServerError(errorTemplateView()))
      }.flatMap {
        case Right(pdfAsHtml) =>
          pdfGenerator.generatePdf(pdfAsHtml).map { pdfByteStream =>
            Ok(pdfByteStream)
              .as("application/pdf")
              .withHeaders(CONTENT_DISPOSITION -> "attachment; filename=EORI-number.pdf")
          }
        case Left(errorTemplate) => Future.successful(errorTemplate)
      }
  }
}
