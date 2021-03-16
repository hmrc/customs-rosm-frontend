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

package uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.libs.json.Json
import uk.gov.hmrc.customs.rosmfrontend.domain.EstablishmentAddress._
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.ContactInformation._
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.SubscriptionRequest.principalEconomicActivityLength
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.{RequestCommon, RequestParameter}
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.customs.rosmfrontend.services.countries.Country
import uk.gov.hmrc.customs.rosmfrontend.services.mapping.{CdsToEtmpOrganisationType, OrganisationTypeConfiguration}

import java.time.Clock
import java.util.UUID
case class SubscriptionCreateRequest(requestCommon: RequestCommon, requestDetail: RequestDetail)
    extends CaseClassAuditHelper {
  def keyValueMap(): Map[String, String] = {
    val m = requestCommon.keyValueMap()
    val rd = requestDetail.keyValueMap()
    m ++ rd
  }
}

object SubscriptionCreateRequest {

  implicit val jsonFormat = Json.format[SubscriptionCreateRequest]

  def apply(
    registration: RegistrationDetails,
    subscription: SubscriptionDetails,
    email: Option[String],
    cdsOrganisationType: Option[CdsOrganisationType]
  ): SubscriptionCreateRequest =
    registration match {
      case RegistrationDetailsIndividual(Some(Eori(eori)), _, safeId, name, _, dob) =>
        mandatoryFieldsReq(
          eori,
          safeId,
          name,
          fourFieldAddress(subscription, registration),
          dob,
          CdsToEtmpOrganisationType(cdsOrganisationType),
          subscription
        )

      case RegistrationDetailsOrganisation(Some(Eori(eori)), _, safeId, name, _, Some(dateOfEstablishment), _) =>
        mandatoryFieldsReq(
          eori,
          safeId,
          name,
          fourFieldAddress(subscription, registration),
          dateOfEstablishment,
          CdsToEtmpOrganisationType(cdsOrganisationType),
          subscription
        )

      case _ =>
        throw new IllegalArgumentException("Invalid Registration Details. Unable to create SubscriptionCreateRequest.")
    }

  def apply(
    data: ResponseData,
    subscription: SubscriptionDetails,
    email: String,
    countries: List[Country]
  ): SubscriptionCreateRequest = {

    val establishmentAddress = subscription.addressDetails.map { address =>
      data.establishmentAddress.fromCapturedAddress(address, countries)
    }.getOrElse(throw new IllegalStateException("Register with Eori and Id EstablishmentAddress cannot be empty"))

    SubscriptionCreateRequest(
      generateWithOriginatingSystem(),
      RequestDetail(
        SAFE = data.SAFEID,
        EORINo = subscription.eoriNumber,
        CDSFullName = data.trader.fullName,
        CDSEstablishmentAddress = establishmentAddress,
        establishmentInTheCustomsTerritoryOfTheUnion =
          data.hasEstablishmentInCustomsTerritory.map(bool => if (bool) "1" else "0"),
        typeOfLegalEntity = data.legalStatus,
        contactInformation = data.contactDetail.map(cd => createContactInformation(cd).withEmail(email)),
        vatIDs =
          data.VATIDs.map(_.map(vs => VatId(countryCode = Some(vs.countryCode), vatID = Some(vs.vatNumber))).toList),
        consentToDisclosureOfPersonalData = None,
        shortName = Some(data.trader.shortName),
        dateOfEstablishment = handleEmptyDate(data.dateOfEstablishmentBirth),
        typeOfPerson = data.personType.map(_.toString),
        principalEconomicActivity = data.principalEconomicActivity
      )
    )
  }

  def apply(
    reg: RegistrationDetails,
    sub: SubscriptionDetails,
    cdsOrgType: Option[CdsOrganisationType],
    dateEstablished: LocalDate
  ): SubscriptionRequest = {
    val org = CdsToEtmpOrganisationType(cdsOrgType) orElse CdsToEtmpOrganisationType(reg)
    val ukVatId: Option[VatIdentification] =
      sub.ukVatDetails.map(vd => VatIdentification(Some("GB"), Some(vd.normalizeVATNumber)))
    val euVatIds = sub.vatIdentificationList

    SubscriptionRequest(
      SubscriptionCreateRequest(
        generateWithOriginatingSystem(),
        RequestDetail(
          SAFE = reg.safeId.id,
          EORINo = None,
          CDSFullName = reg.name,
          CDSEstablishmentAddress = fourFieldAddress(sub, reg),
          establishmentInTheCustomsTerritoryOfTheUnion = None,
          typeOfLegalEntity = org.map(_.legalStatus),
          contactInformation = sub.contactDetails.map(c => createContactInformation(c.contactDetails)),
          vatIDs = createVatIds(Some(ukVatId ++: euVatIds)),
          consentToDisclosureOfPersonalData = sub.personalDataDisclosureConsent.map(bool => if (bool) "1" else "0"),
          shortName = sub.businessShortName flatMap (_.shortName),
          dateOfEstablishment = Some(dateEstablished),
          typeOfPerson = org.map(_.typeOfPerson),
          principalEconomicActivity = sub.sicCode.map(_.take(principalEconomicActivityLength))
        )
      )
    )
  }

  private def mandatoryFieldsReq(
    eori: String,
    safeId: SafeId,
    fullName: String,
    establishmentAddress: EstablishmentAddress,
    dateOfEstablishment: LocalDate,
    etmpTypeOfPerson: Option[OrganisationTypeConfiguration],
    sub: SubscriptionDetails
  ) =
    SubscriptionCreateRequest(
      generateWithOriginatingSystem(),
      RequestDetail(
        SAFE = safeId.id,
        EORINo = Some(eori),
        CDSFullName = fullName,
        CDSEstablishmentAddress = establishmentAddress,
        establishmentInTheCustomsTerritoryOfTheUnion = None,
        typeOfLegalEntity = etmpTypeOfPerson.map(_.legalStatus),
        contactInformation = sub.contactDetails.map(c => createContactInformation(c.contactDetails)),
        vatIDs = None,
        consentToDisclosureOfPersonalData = None,
        shortName = None,
        dateOfEstablishment = Some(dateOfEstablishment),
        typeOfPerson = etmpTypeOfPerson.map(_.typeOfPerson),
        principalEconomicActivity = None
      )
    )

  private def dashForEmpty(s: String): String =
    if (s.isEmpty) "-" else s

  private def fourFieldAddress(subscription: SubscriptionDetails, registration: RegistrationDetails) = {

    val address = subscription.addressDetails match {
      case Some(a) =>
        EstablishmentAddress(streetAndNumber = a.street, city = a.city, a.postcode.filter(_.nonEmpty), a.countryCode)
      case _ => createEstablishmentAddress(registration.address)
    }
    address.copy(city = dashForEmpty(address.city))
  }

  def generateWithOriginatingSystem(requestParameters: Option[Seq[RequestParameter]] = None): RequestCommon =
    RequestCommon(
      regime = "CDS",
      receiptDate = new DateTime(Clock.systemUTC().instant.toEpochMilli, DateTimeZone.UTC),
      acknowledgementReference = UUID.randomUUID().toString.replace("-", ""),
      originatingSystem = Some("MDTP"),
      requestParameters = requestParameters
    )

  private def handleEmptyDate(date: Option[String]): Option[LocalDate] = date match {
    case Some(d) => Some(LocalDate.parse(d, DateTimeFormat.forPattern("yyyy-MM-dd")))
    case None =>
      CdsLogger.warn("No establishment date returned from Register with Eori and Id")
      None
  }

  private def createVatIds(vis: Option[List[VatIdentification]]): Option[List[VatId]] = {
    def removeEmpty: List[VatIdentification] => List[VatId] = _.flatMap {
      case VatIdentification(None, None) => None
      case VatIdentification(cc, n)      => Some(VatId(cc, n))
    }

    def removeEmptyList: List[VatId] => Option[List[VatId]] = {
      case Nil => None
      case vs  => Some(vs)
    }

    vis map removeEmpty flatMap removeEmptyList
  }
}
