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

package uk.gov.hmrc.customs.rosmfrontend.logging

object CdsLogger {
  lazy val logger = play.api.Logger("customs-rosm-frontend")

  def debug(s: String): Unit = logger.debug(s)
  def info(s: String): Unit = logger.info(s)
  def warn(s: String): Unit = logger.warn(s)
  def warn(s: String, e: Throwable): Unit = logger.warn(s, e)
  def error(s: String): Unit = logger.error(s)
  def error(s: String, e: Throwable): Unit = logger.error(s, e)
}
