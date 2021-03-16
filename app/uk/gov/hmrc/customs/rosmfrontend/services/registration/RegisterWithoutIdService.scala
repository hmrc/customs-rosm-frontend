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

package uk.gov.hmrc.customs.rosmfrontend.services.registration

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.customs.rosmfrontend.connector.RegisterWithoutIdConnector
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.{Address, Individual}
import uk.gov.hmrc.customs.rosmfrontend.forms.MatchingForms.createSixLineAddress
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.ContactDetailsModel
import uk.gov.hmrc.customs.rosmfrontend.services.RequestCommonGenerator
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class RegisterWithoutIdService @Inject()(
  connector: RegisterWithoutIdConnector,
  requestCommonGenerator: RequestCommonGenerator,
  detailsCreator: RegistrationDetailsCreator,
  sessionCache: SessionCache
) {

  def registerOrganisation(
    orgName: String,
    address: Address,
    contactDetail: Option[ContactDetailsModel],
    loggedInUser: LoggedInUser,
    internalId: Option[String] = None,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier): Future[RegisterWithoutIDResponse] = {

    val request = RegisterWithoutIDRequest(
      requestCommonGenerator.generate(),
      RegisterWithoutIdReqDetails.organisation(
        OrganisationName(orgName),
        address,
        contactDetail.getOrElse(throw new IllegalStateException("No contact details in cache"))
      )
    )

    for {
      response <- connector.register(request)
      registrationDetails = detailsCreator.registrationDetails(response, orgName, createSixLineAddress(address))
      _ <- save(registrationDetails, loggedInUser, orgType)
    } yield response
  }

  def registerIndividual(
    individualNameAndDateOfBirth: IndividualNameAndDateOfBirth,
    address: Address,
    contactDetail: Option[ContactDetailsModel],
    loggedInUser: LoggedInUser,
    internalId: Option[String] = None,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier): Future[RegisterWithoutIDResponse] = {
    import individualNameAndDateOfBirth._
    val individual =
      Individual.withLocalDate(firstName, middleName, lastName, dateOfBirth)
    val reqDetails = RegisterWithoutIdReqDetails.individual(
      address = address,
      individual = individual,
      contactDetail = contactDetail.getOrElse(throw new IllegalStateException("No contact details in cache"))
    )
    val request =
      RegisterWithoutIDRequest(requestCommonGenerator.generate(), reqDetails)
    for {
      response <- connector.register(request)
      registrationDetails = detailsCreator.registrationDetails(
        response,
        individualNameAndDateOfBirth,
        createSixLineAddress(address)
      )
      _ <- save(registrationDetails, loggedInUser, orgType)

    } yield response

  }

  private def save(
    registrationDetails: RegistrationDetails,
    loggedInUser: LoggedInUser,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier) =
    if (registrationDetails.safeId.id.nonEmpty) {
      sessionCache.saveRegistrationDetailsWithoutId(
        registrationDetails: RegistrationDetails,
        InternalId(loggedInUser.internalId),
        orgType
      )
    } else {
      sessionCache.saveRegistrationDetails(registrationDetails: RegistrationDetails)
    }
}
