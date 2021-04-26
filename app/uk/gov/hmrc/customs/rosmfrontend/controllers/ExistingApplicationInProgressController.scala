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

import org.joda.time.DateTime
import play.api.Application
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.forms.FormUtils.dateTimeFormat
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription.{existing_eori_application_processing, subscription_status_outcome_processing}

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global

class ExistingApplicationInProgressController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  sessionCache: SessionCache,
  subscriptionStatusOutcomeView: existing_eori_application_processing,
) extends CdsController(mcc) {
  lazy val  formattedDate = dateTimeFormat.print(DateTime.now())

  def show(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        mayBeSubscriptionCreateOutcome <- sessionCache.mayBeSubscriptionCreateOutcome
      } yield Ok(subscriptionStatusOutcomeView(mayBeSubscriptionCreateOutcome.map(_.fullName),mayBeSubscriptionCreateOutcome.map(_.processedDate).getOrElse(formattedDate)))
  }
}
