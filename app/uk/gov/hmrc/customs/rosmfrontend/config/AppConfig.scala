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

package uk.gov.hmrc.customs.rosmfrontend.config

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.duration.Duration

@Singleton
class AppConfig @Inject()(
  config: Configuration,
  servicesConfig: ServicesConfig,
  @Named("appName") val appName: String
) {

  lazy val ttl: Duration = Duration.create(config.get[String]("cds-frontend-cache.ttl"))
  lazy val allowlistReferrers: Seq[String] =
    config.get[String]("allowlist-referrers").split(',').map(_.trim).filter(_.nonEmpty)
  lazy val isShuttered: Boolean = config.get[Boolean]("shutter-get-access-cds")
  lazy val autoCompleteEnabled: Boolean = config.get[Boolean]("autocomplete-enabled")

  lazy val contactBaseUrl = servicesConfig.baseUrl("contact-frontend")

  private lazy val serviceIdentifierGetAnEori =
    config.get[String]("microservice.services.contact-frontend.serviceIdentifierGetEori")
  private lazy val serviceIdentifierGetAccess =
    config.get[String]("microservice.services.contact-frontend.serviceIdentifierGetAccess")

  lazy val feedbackLink = config.get[String]("external-url.feedback-survey")
  lazy val feedbackLinkSubscribe = config.get[String]("external-url.feedback-survey-subscribe")
  
  //get help link feedback for Get an EORI
  val reportAProblemPartialUrlGetAnEori: String =
    s"$contactBaseUrl/contact/problem_reports_ajax?service=$serviceIdentifierGetAnEori"
  val reportAProblemNonJSUrlGetAnEori: String =
    s"$contactBaseUrl/contact/problem_reports_nonjs?service=$serviceIdentifierGetAnEori"

  //get help link feedback for Get access to CDS
  val reportAProblemPartialUrlGetAccess: String =
    s"$contactBaseUrl/contact/problem_reports_ajax?service=$serviceIdentifierGetAccess"
  val reportAProblemNonJSUrlGetAccess: String =
    s"$contactBaseUrl/contact/problem_reports_nonjs?service=$serviceIdentifierGetAccess"

  //email verification service
  lazy val emailVerificationBaseUrl: String = servicesConfig.baseUrl("email-verification")
  lazy val emailVerificationServiceContext: String =
    config.get[String]("microservice.services.email-verification.context")
  lazy val emailVerificationTemplateId: String =
    config.get[String]("microservice.services.email-verification.templateId")

  lazy val emailVerificationLinkExpiryDuration: String =
    config.get[String]("microservice.services.email-verification.linkExpiryDuration")
  //handle subscription service
  lazy val handleSubscriptionBaseUrl: String = servicesConfig.baseUrl("handle-subscription")

  lazy val handleSubscriptionServiceContext: String =
    config.get[String]("microservice.services.handle-subscription.context")

  //pdf generation
  lazy val pdfGeneratorBaseUrl: String = servicesConfig.baseUrl("pdf-generator")
  // tax enrolments
  lazy val taxEnrolmentsBaseUrl: String = servicesConfig.baseUrl("tax-enrolments")

  lazy val taxEnrolmentsServiceContext: String = config.get[String]("microservice.services.tax-enrolments.context")

  lazy val enrolmentStoreProxyBaseUrl: String = servicesConfig.baseUrl("enrolment-store-proxy")

  lazy val enrolmentStoreProxyServiceContext: String =
    config.get[String]("microservice.services.enrolment-store-proxy.context")

  def getServiceUrl(proxyServiceName: String): String = {
    val baseUrl = servicesConfig.baseUrl("customs-hods-proxy")
    val serviceContext = config.get[String](s"microservice.services.customs-hods-proxy.$proxyServiceName.context")
    s"$baseUrl/$serviceContext"
  }
}
