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

package uk.gov.hmrc.customs.rosmfrontend.filters

import akka.stream.Materializer
import javax.inject.Inject
import play.api.mvc.Results.Redirect
import play.api.mvc._
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig

import scala.concurrent.Future

class ShutterFilter @Inject()(appConfig: AppConfig)(implicit val mat: Materializer) extends Filter {

  val isShuttered: Boolean = appConfig.isShuttered
  override def apply(next: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
    val journey = journeyType(rh)
    if (journey.contains("subscribe-for-cds") && isShuttered) {
      Future.successful(Redirect("/customs/shutter"))
    } else next(rh)
  }

  private def journeyType(request: RequestHeader): Option[String] =
    "(?<=/customs/)([^\\/]*)".r.findFirstIn(request.path)
}
