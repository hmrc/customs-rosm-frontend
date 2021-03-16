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

package util.externalservices

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import play.api.libs.json.Json
import play.mvc.Http.HeaderNames.CONTENT_TYPE
import play.mvc.Http.MimeTypes.JSON
import play.mvc.Http.Status.OK

object RegistrationInfoMessagingService {

  import util.builders.RegistrationInfoResponseHolderBuilder._

  val individualResponse: String = Json.toJson(registrationInfoResponseHolder(AnIndividual, None)).toString
  val organisationResponse: String = Json.toJson(registrationInfoResponseHolder(None, AnOrganisation)).toString

  private def registrationPath(eori: String): String = s"/registration-display?regime=CDS&idType=EORI&idValue=$eori"

  def returnTheResponseWhenReceiveRequestForAnIndividual(eori: String): Unit =
    returnTheResponseWhenReceiveRequest(registrationPath(eori), individualResponse)

  def returnOrganisationRegistrationInfoWhenReceiveRequest(eori: String): Unit =
    returnTheResponseWhenReceiveRequest(registrationPath(eori), organisationResponse)

  private def registrationPathForSafe(safeId: String): String =
    s"/registration-display?regime=CDS&idType=SAFE&idValue=$safeId"

  def returnTheResponseWhenReceiveRequestForAnIndividualForSafe(safeId: String): Unit =
    returnTheResponseWhenReceiveRequest(registrationPathForSafe(safeId), individualResponse)

  def returnTheResponseWhenReceiveRequestForAnOrganisationForSafe(safeId: String): Unit =
    returnTheResponseWhenReceiveRequest(registrationPathForSafe(safeId), organisationResponse)

  def returnTheResponseWhenReceiveRequest(url: String, response: String): Unit =
    returnTheResponseWhenReceiveRequest(url, response, OK)

  def returnTheResponseWhenReceiveRequest(url: String, response: String, status: Int): Unit =
    stubFor(
      get(urlEqualTo(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(response)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )
}
