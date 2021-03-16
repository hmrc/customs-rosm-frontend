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

package util.builders

import uk.gov.hmrc.customs.rosmfrontend.domain.subscription._
import uk.gov.hmrc.customs.rosmfrontend.forms.subscription.SubscriptionForm
import org.joda.time.LocalDate

object SubscriptionAmendCompanyDetailsFormBuilder {

  val withoutShortName = "false"
  val withShortName = "true"
  val ShortName = "Shortened Name"
  val sic = "99996"
  val DateEstablishedString = "2015-06-22"
  val DateEstablished = LocalDate.parse(DateEstablishedString)
  val DateEstablishedForPublicBody = LocalDate.parse("1900-01-01")
  val EoriNumber = "GB123456789000"
  val Email = "test@test.com"

  val mandatoryShortNameFieldsMap = Map("use-short-name" -> withoutShortName)
  val mandatoryShortNameFields: CompanyShortNameViewModel =
    SubscriptionForm.subscriptionCompanyShortNameForm.bind(mandatoryShortNameFieldsMap).value.get
  val mandatoryShortNameFieldsAsShortName = BusinessShortName(mandatoryShortNameFields.shortName)

  val allShortNameFieldsMap = mandatoryShortNameFieldsMap + (
    "use-short-name" -> withShortName,
    "short-name" -> ShortName
  )

  val emptyShortNameFieldsMap = Map("use-short-name" -> "", "short-name" -> "")
  val allShortNameFields: CompanyShortNameViewModel =
    SubscriptionForm.subscriptionCompanyShortNameForm.bind(allShortNameFieldsMap).value.get
  val allShortNameFieldsAsShortName = BusinessShortName(allShortNameFields.shortName)

  val mandatoryFieldsMap = Map(
    "use-short-name" -> withoutShortName,
    "date-established.day" -> DateEstablished.dayOfMonth.getAsString,
    "date-established.month" -> DateEstablished.monthOfYear.getAsString,
    "date-established.year" -> DateEstablished.year.getAsString,
    "sic" -> sic,
    "eori-number" -> EoriNumber
  )

  val mandatoryFieldsMapWithoutDateOfEstablishment = Map("use-short-name" -> withoutShortName, "sic" -> sic)

  val populatedSicCodeFieldsMap = Map("sic" -> sic)

  val unpopulatedSicCodeFieldsMap = Map("sic" -> "")

  val populatedEoriNumberFieldsMap = Map("eori-number" -> EoriNumber)

  val unpopulatedEoriNumberFieldsMap = Map("eori-number" -> "")

}
