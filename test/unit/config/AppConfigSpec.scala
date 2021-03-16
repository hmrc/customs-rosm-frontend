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

package unit.config

import java.util.concurrent.TimeUnit

import util.ControllerSpec

import scala.concurrent.duration.Duration

class AppConfigSpec extends ControllerSpec {

  "AppConfig" should {
    "have ttl defined" in {
      appConfig.ttl shouldBe Duration(40, TimeUnit.MINUTES)
    }

    "have allowlistReferrers defined" in {
      appConfig.allowlistReferrers shouldBe Array.empty
    }

    "have emailVerificationBaseUrl defined" in {
      appConfig.emailVerificationBaseUrl shouldBe "http://localhost:9477"
    }

    "have emailVerificationServiceContext defined" in {
      appConfig.emailVerificationServiceContext shouldBe "email-verification"
    }

    "have verifyEmailAddress defined" in {
      appConfig.emailVerificationTemplateId shouldBe "verifyEmailAddress"
    }

    "have emailVerificationLinkExpiryDuration defined" in {
      appConfig.emailVerificationLinkExpiryDuration shouldBe "P1D"
    }

    "have handleSubscriptionBaseUrl defined" in {
      appConfig.handleSubscriptionBaseUrl shouldBe "http://localhost:9829"
    }

    "have handleSubscriptionServiceContext defined" in {
      appConfig.handleSubscriptionServiceContext shouldBe "handle-subscription"
    }

    "have pdfGeneratorBaseUrl defined" in {
      appConfig.pdfGeneratorBaseUrl shouldBe "http://localhost:9852"
    }

    "have taxEnrolmentsBaseUrl defined" in {
      appConfig.taxEnrolmentsBaseUrl shouldBe "http://localhost:9477"
    }

    "have taxEnrolmentsServiceContext defined" in {
      appConfig.taxEnrolmentsServiceContext shouldBe "tax-enrolments"
    }

    "have enrolmentStoreProxyBaseUrl defined" in {
      appConfig.enrolmentStoreProxyBaseUrl shouldBe "http://localhost:9477"
    }

    "have enrolmentStoreProxyServiceContext defined" in {
      appConfig.enrolmentStoreProxyServiceContext shouldBe "enrolment-store-proxy"
    }

    "have feedbackLink defined for register" in {
      appConfig.feedbackLink shouldBe "http://localhost:9514/feedback/CDS"
    }

    "have feedbackLink defined for subscribe" in {
      appConfig.feedbackLinkSubscribe shouldBe "http://localhost:9514/feedback/get-access-cds"
    }

    "have reportAProblemPartialUrl defined for register" in {
      appConfig.reportAProblemPartialUrlGetAnEori shouldBe "http://localhost:9250/contact/problem_reports_ajax?service=CDS"
    }

    "have reportAProblemNonJSUrl defined for register" in {
      appConfig.reportAProblemNonJSUrlGetAnEori shouldBe "http://localhost:9250/contact/problem_reports_nonjs?service=CDS"
    }

    "have reportAProblemPartialUrl defined for subscribe" in {
      appConfig.reportAProblemPartialUrlGetAccess shouldBe "http://localhost:9250/contact/problem_reports_ajax?service=get-access-cds"
    }

    "have reportAProblemNonJSUrl defined for subscribe" in {
      appConfig.reportAProblemNonJSUrlGetAccess shouldBe "http://localhost:9250/contact/problem_reports_nonjs?service=get-access-cds"
    }
  }

  "using getServiceUrl" should {
    "return service url for register-with-id" in {
      appConfig.getServiceUrl("register-with-id") shouldBe "http://localhost:9832/register-with-id"
    }
    "return service url for register-without-id" in {
      appConfig.getServiceUrl("register-without-id") shouldBe "http://localhost:9832/register-without-id"
    }
    "return service url for register-with-eori-and-id" in {
      appConfig.getServiceUrl("register-with-eori-and-id") shouldBe "http://localhost:9832/register-with-eori-and-id"
    }
    "return service url for subscription-status" in {
      appConfig.getServiceUrl("subscription-status") shouldBe "http://localhost:9832/subscription-status"
    }
    "return service url for subscription-display" in {
      appConfig.getServiceUrl("subscription-display") shouldBe "http://localhost:9832/subscription-display"
    }
    "return service url for registration-display" in {
      appConfig.getServiceUrl("registration-display") shouldBe "http://localhost:9832/registration-display"
    }
    "return service url for subscribe" in {
      appConfig.getServiceUrl("subscribe") shouldBe "http://localhost:9832/subscribe"
    }
    "return service url for vat-known-facts-control-list" in {
      appConfig.getServiceUrl("vat-known-facts-control-list") shouldBe "http://localhost:9832/vat-known-facts-control-list"
    }

  }
}
