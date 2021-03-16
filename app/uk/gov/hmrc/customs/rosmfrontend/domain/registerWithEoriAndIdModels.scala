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

package uk.gov.hmrc.customs.rosmfrontend.domain

import java.lang.reflect.Field
import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging._
import uk.gov.hmrc.customs.rosmfrontend.forms.FormValidation.{postCodeMandatoryCountryCodes, postcodeRegex}
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.customs.rosmfrontend.services.countries.Country

case class GovGatewayCredentials(email: String) extends CaseClassAuditHelper

object GovGatewayCredentials {
  implicit val format = Json.format[GovGatewayCredentials]
}

case class RegisterWithEoriAndIdRequestCommon(
  receiptDate: DateTime,
  acknowledgementReference: String,
  requestParameters: Option[Seq[RequestParameter]] = None
)

object RegisterWithEoriAndIdRequestCommon extends CommonHeader {
  implicit val format = Json.format[RegisterWithEoriAndIdRequestCommon]
  implicit val requestParamFormat = Json.format[RequestParameter]
}

trait AddressValidator {
  def postalCode: Option[String]
  def countryCode: String

  def isValidCountry: List[Country] => Boolean =
    (countries: List[Country]) => countries.exists(_.countryCode == this.countryCode)
  def hasValidPostcode: Boolean = this.postalCode.exists(_.matches(postcodeRegex.regex))
  def isPostcodeRequired: Boolean = postCodeMandatoryCountryCodes.contains(this.countryCode)

  def isValidAddress(countries: List[Country]): Boolean =
    if (isValidCountry(countries)) {
      if (isPostcodeRequired) hasValidPostcode else true
    } else false
}

case class EstablishmentAddress(
  streetAndNumber: String,
  city: String,
  postalCode: Option[String] = None,
  countryCode: String
) extends CaseClassAuditHelper with AddressValidator {
  def fromCapturedAddress(address: AddressViewModel, countries: List[Country]): EstablishmentAddress =
    if (isValidCountry(countries)) {
      this
    } else {
      this.copy(countryCode = address.countryCode)
    }
}

object EstablishmentAddress {
  implicit val jsonFormat = Json.format[EstablishmentAddress]

  def createEstablishmentAddress(address: Address): EstablishmentAddress = {
    val fourLineAddress = AddressViewModel(address)
    new EstablishmentAddress(
      fourLineAddress.street,
      fourLineAddress.city,
      address.postalCode.filterNot(p => p.isEmpty),
      fourLineAddress.countryCode
    )
  }

}

case class RegisterModeEori(EORI: String, fullName: String, address: EstablishmentAddress)
    extends CaseClassAuditHelper {
  val ignoredFields = List("address")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val am = prefixMapKey("address.", address.toMap())
    m ++ am
  }
}

object RegisterModeEori {
  implicit val format = Json.format[RegisterModeEori]
}

case class RegisterWithEoriAndIdOrganisation(name: String, `type`: String) extends CaseClassAuditHelper

object RegisterWithEoriAndIdOrganisation {
  implicit val formats = Json.format[RegisterWithEoriAndIdOrganisation]
}

case class RegisterModeId(
  IDType: String,
  IDNumber: String,
  isNameMatched: Boolean,
  individual: Option[Individual] = None,
  organisation: Option[RegisterWithEoriAndIdOrganisation] = None
) extends CaseClassAuditHelper {
  require(individual.isDefined ^ organisation.isDefined)

  val ignoredFields = List("individual", "organisation")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val om = prefixMapKey("organisation.", organisation.fold(Map.empty[String, String])(_.toMap()))
    val im = prefixMapKey("individual.", individual.fold(Map.empty[String, String])(_.toMap()))
    m ++ om ++ im
  }
}

object RegisterModeId {
  implicit val format = Json.format[RegisterModeId]
}

case class RegisterWithEoriAndIdDetail(
  registerModeEORI: RegisterModeEori,
  registerModeID: RegisterModeId,
  govGatewayCredentials: Option[GovGatewayCredentials]
) extends CaseClassAuditHelper {
  def keyValueMap(): Map[String, String] = {
    val rem = registerModeEORI.keyValueMap()
    val rim = registerModeID.keyValueMap()
    val gcm = govGatewayCredentials.fold(Map.empty[String, String])(_.toMap())
    rem ++ rim ++ gcm
  }
}

object RegisterWithEoriAndIdDetail {
  implicit val format = Json.format[RegisterWithEoriAndIdDetail]
}

case class RegisterWithEoriAndIdRequest(requestCommon: RequestCommon, requestDetail: RegisterWithEoriAndIdDetail)
    extends CaseClassAuditHelper {
  def keyValueMap(): Map[String, String] = {
    val rcm = requestCommon.keyValueMap()
    val rdm = requestDetail.keyValueMap()
    rcm ++ rdm
  }
}

object RegisterWithEoriAndIdRequest {
  implicit val format = Json.format[RegisterWithEoriAndIdRequest]
}

case class RegisterWithEoriAndIdRequestHolder(registerWithEORIAndIDRequest: RegisterWithEoriAndIdRequest)

object RegisterWithEoriAndIdRequestHolder {
  implicit val format = Json.format[RegisterWithEoriAndIdRequestHolder]
}

case class VatIds(countryCode: String, vatNumber: String) extends CaseClassAuditHelper

object VatIds {
  implicit val format = Json.format[VatIds]
}

case class ContactDetail(
  address: EstablishmentAddress,
  contactName: String,
  phone: Option[String],
  fax: Option[String],
  email: Option[String]
) extends CaseClassAuditHelper {
  val ignoredFields = List("address")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val addressMap = prefixMapKey("address.", address.toMap())
    m ++ addressMap
  }

}

object ContactDetail {
  implicit val format = Json.format[ContactDetail]
}

case class Trader(fullName: String, shortName: String) extends CaseClassAuditHelper

object Trader {
  implicit val format = Json.format[Trader]
}

case class ResponseData(
  SAFEID: String,
  trader: Trader,
  establishmentAddress: EstablishmentAddress,
  contactDetail: Option[ContactDetail] = None,
  VATIDs: Option[Seq[VatIds]] = None,
  hasInternetPublication: Boolean,
  principalEconomicActivity: Option[String] = None,
  hasEstablishmentInCustomsTerritory: Option[Boolean] = None,
  legalStatus: Option[String] = None,
  thirdCountryIDNumber: Option[Seq[String]] = None,
  dateOfEstablishmentBirth: Option[String] = None,
  personType: Option[Int] = None,
  startDate: String,
  expiryDate: Option[String] = None
) extends CaseClassAuditHelper {

  val ignoredFields = List("establishmentAddress", "contactDetail", "VATIDs", "thirdCountryIDNumber", "trader")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val t = prefixMapKey("trader.", trader.toMap())
    val a = prefixMapKey("address.", establishmentAddress.toMap())
    val cd = prefixMapKey("contactDetail.", contactDetail.fold(Map.empty[String, String])(_.keyValueMap()))
    val tc = thirdCountryIDNumber.fold(Map.empty[String, String])(x => prefixMapKey("thirdCountryIDNumber.", x))
    val vds = VATIDs.fold(Map.empty[String, String])(x => prefixMapKey("VATIDs.", convertToMap(x.map(_.toMap()))))
    m ++ t ++ a ++ cd ++ tc ++ vds
  }
}

object ResponseData {
  implicit val format = Json.format[ResponseData]
}

case class RegisterWithEoriAndIdResponseDetail(
  outcome: Option[String],
  caseNumber: Option[String],
  responseData: Option[ResponseData] = None
) extends CaseClassAuditHelper {
  val ignoredFields = List("responseData")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val rdpm = responseData.fold(Map.empty[String, String])(_.keyValueMap())
    m ++ rdpm
  }
}

object RegisterWithEoriAndIdResponseDetail {
  implicit val format = Json.format[RegisterWithEoriAndIdResponseDetail]
}

case class AdditionalInformation(id: CustomsId, isIndividual: Boolean)
object AdditionalInformation {
  implicit val format = Json.format[AdditionalInformation]
}

case class RegisterWithEoriAndIdResponse(
  responseCommon: ResponseCommon,
  responseDetail: Option[RegisterWithEoriAndIdResponseDetail],
  additionalInformation: Option[AdditionalInformation] = None
) extends CaseClassAuditHelper {
  def keyValueMap(): Map[String, String] = {
    val rc = responseCommon.keyValueMap()
    val rm = responseDetail.fold(Map.empty[String, String])(_.keyValueMap())
    rc ++ rm
  }

  def isDoE: Boolean = {
    val doe = for {
      res <- responseDetail
      data <- res.responseData
      doe <- data.dateOfEstablishmentBirth
    } yield doe
    doe.isDefined
  }

  def isPersonType: Boolean = {
    val pt = for {
      res <- responseDetail
      data <- res.responseData
      pt <- data.personType
    } yield pt
    pt.isDefined
  }

  def isResponseData: Boolean = {
    val data = for {
      res <- responseDetail
      data <- res.responseData
    } yield data
    data.isDefined
  }
  def withPersonType(typeOfPerson: String): RegisterWithEoriAndIdResponse = {
    val mayBeResponseData = responseDetail.flatMap { res =>
      res.responseData.map(_.copy(personType = Some(typeOfPerson.toInt)))
    }
    this.copy(responseDetail = responseDetail.map(_.copy(responseData = mayBeResponseData)))
  }

  def withDateOfEstablishment(dob: String): RegisterWithEoriAndIdResponse = {
    val mayBeResponseData = responseDetail.flatMap { res =>
      res.responseData.map(_.copy(dateOfEstablishmentBirth = Some(dob)))
    }
    this.copy(responseDetail = responseDetail.map(_.copy(responseData = mayBeResponseData)))
  }

  def withAdditionalInfo(request: RegisterModeId): RegisterWithEoriAndIdResponse = {
    val customsId = CustomsId(request.IDType, request.IDNumber)
    val additionalInformation = AdditionalInformation(customsId, request.individual.isDefined)
    this.copy(additionalInformation = Some(additionalInformation))
  }
}

object RegisterWithEoriAndIdResponse {
  implicit val format = Json.format[RegisterWithEoriAndIdResponse]
  val EoriAlreadyLinked = "600 - EORI already linked to a different ID"
  val IDLinkedWithEori = "602 - ID already linked to a different EORI"
  val RejectedPreviouslyAndRetry = "601 - Rejected previously and retry failed"
}

case class RegisterWithEoriAndIdResponseHolder(registerWithEORIAndIDResponse: RegisterWithEoriAndIdResponse)

object RegisterWithEoriAndIdResponseHolder {
  implicit val format = Json.format[RegisterWithEoriAndIdResponseHolder]
}

trait CaseClassAuditHelper {

  def toMap(caseClassObject: AnyRef = this, ignoredFields: List[String] = List.empty): Map[String, String] =
    (Map[String, String]() /: caseClassObject.getClass.getDeclaredFields
      .filterNot(field => ignoredFields.contains(field.getName))) {

      def getKeyValue(acc: Map[String, String], value: Any) =
        value match {
          case v: CaseClassAuditHelper => v.toMap()
          case _                       => acc
        }

      def fetchValue(acc: Map[String, String], f: Field, value: Any) =
        if (isLeafNode(value)) {
          acc + (f.getName -> value.toString)
        } else {
          getKeyValue(acc, value)
        }

      (acc, f) =>
        f.setAccessible(true)
        val value = f.get(caseClassObject)
        if (value != null) {
          if (isScalaOption(value)) {
            val option = value.asInstanceOf[Option[Any]]
            if (option.isDefined) {
              fetchValue(acc, f, option.get)
            } else {
              acc
            }
          } else {
            fetchValue(acc, f, value)
          }
        } else {
          acc
        }
    }

  def prefixMapKey(prefix: String, map: Map[String, String]): Map[String, String] =
    map.map(x => prefix + x._1 -> x._2)

  def prefixMapKey(prefix: String, list: Seq[String]): Map[String, String] =
    list.zipWithIndex.map(kv => prefix + (kv._2 + 1) -> kv._1).toMap

  def convertToMap(list: Seq[Map[String, String]]): Map[String, String] =
    list.zipWithIndex
      .flatMap(
        kv =>
          kv._1.map(x => {
            (x._1 + "." + kv._2) -> x._2
          })
      )
      .toMap

  private def isLeafNode(value: Any) =
    value match {
      case _: String     => true
      case _: Int        => true
      case _: Long       => true
      case _: Boolean    => true
      case _: Double     => true
      case _: BigDecimal => true
      case _: Float      => true
      case _: DateTime   => true
      case _             => false
    }

  private def isScalaOption(value: Object): Boolean = value.getClass.getSuperclass.equals(Class.forName("scala.Option"))
}
