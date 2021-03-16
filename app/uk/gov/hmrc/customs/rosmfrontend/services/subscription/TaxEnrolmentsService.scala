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

package uk.gov.hmrc.customs.rosmfrontend.services.subscription

import javax.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import uk.gov.hmrc.customs.rosmfrontend.connector.TaxEnrolmentsConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.TaxEnrolmentsRequest._
import uk.gov.hmrc.customs.rosmfrontend.domain.{Eori, KeyValue, SafeId, TaxEnrolmentsRequest}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxEnrolmentsService @Inject()(taxEnrolmentsConnector: TaxEnrolmentsConnector) {

  private val serviceName = "HMRC-CUS-ORG"

  def doesEnrolmentExist(safeId: SafeId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    taxEnrolmentsConnector.getEnrolments(safeId.id).map { enrolments =>
      enrolments.exists(_.serviceName == serviceName)
    }

  def issuerCall(formBundleId: String, eori: Eori, dateOfEstablishment: Option[LocalDate])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Int] = {
    val identifiers = List(KeyValue(key = "EORINUMBER", value = eori.id))
    val verifiers =
      dateOfEstablishment.map(doe => List(KeyValue(key = "DATEOFESTABLISHMENT", value = doe.toString(pattern))))
    val taxEnrolmentsRequest =
      TaxEnrolmentsRequest(identifiers = identifiers, verifiers = verifiers)
    taxEnrolmentsConnector.enrol(taxEnrolmentsRequest, formBundleId)
  }
}
