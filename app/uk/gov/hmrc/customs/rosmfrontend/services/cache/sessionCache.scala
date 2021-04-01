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

package uk.gov.hmrc.customs.rosmfrontend.services.cache

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsSuccess, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.cache._
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.logging.CdsLogger
import uk.gov.hmrc.customs.rosmfrontend.services.Save4LaterService
import uk.gov.hmrc.customs.rosmfrontend.services.cache.CachedData._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

sealed case class CachedData(
  regDetails: Option[RegistrationDetails] = None,
  subDetails: Option[SubscriptionDetails] = None,
  regInfo: Option[RegistrationInfo] = None,
  subscriptionCreateOutcome: Option[SubscriptionCreateOutcome] = None,
  subscriptionStatusOutcome: Option[SubscriptionStatusOutcome] = None,
  registerWithEoriAndIdResponse: Option[RegisterWithEoriAndIdResponse] = None,
  email: Option[String] = None,
  eori: Option[String] = None,
  hasNino: Option[Boolean] = None
) {

  def registrationDetails(sessionId: Id): RegistrationDetails =
    regDetails.getOrElse(throwException(regDetailsKey, sessionId))

  def registerWithEoriAndIdResponse(sessionId: Id): RegisterWithEoriAndIdResponse =
    registerWithEoriAndIdResponse.getOrElse(throwException(registerWithEoriAndIdResponseKey, sessionId))

  def subscriptionStatusOutcome(sessionId: Id): SubscriptionStatusOutcome =
    subscriptionStatusOutcome.getOrElse(throwException(subscriptionStatusOutcomeKey, sessionId))

  def subscriptionCreateOutcome(sessionId: Id): SubscriptionCreateOutcome =
    subscriptionCreateOutcome.getOrElse(throwException(subscriptionCreateOutcomeKey, sessionId))

  def registrationInfo(sessionId: Id): RegistrationInfo =
    regInfo.getOrElse(throwException(regInfoKey, sessionId))

  def subscriptionDetails(sessionId: Id): SubscriptionDetails =
    subDetails.getOrElse(initialEmptySubscriptionDetails)

  def email(sessionId: Id): String =
    email.getOrElse(throwException(emailKey, sessionId))

  def safeId(sessionId: Id) = {
    lazy val mayBeMigration: Option[SafeId] = registerWithEoriAndIdResponse
      .flatMap(_.responseDetail.flatMap(_.responseData.map(_.SAFEID)))
      .map(SafeId(_))
    lazy val mayBeRegistration: Option[SafeId] =
      regDetails.flatMap(s => if (s.safeId.id.nonEmpty) Some(s.safeId) else None)
    mayBeRegistration orElse mayBeMigration getOrElse (throwException(safeIdKey, sessionId))
  }
  // $COVERAGE-OFF$
  private def throwException(name: String, sessionId: Id) =
    throw new IllegalStateException(s"$name is not cached in data for the sessionId: ${sessionId.id}")
  // $COVERAGE-ON$
  private val initialEmptySubscriptionDetails = SubscriptionDetails()
}

object CachedData {
  val regDetailsKey = "regDetails"
  val regInfoKey = "regInfo"
  val subDetailsKey = "subDetails"
  val subscriptionStatusOutcomeKey = "subscriptionStatusOutcome"
  val subscriptionCreateOutcomeKey = "subscriptionCreateOutcome"
  val registerWithEoriAndIdResponseKey = "registerWithEoriAndIdResponse"
  val emailKey = "email"
  val hasNinoKey = "hasNino"
  val safeIdKey = "safeId"
  val groupIdKey = "cachedGroupId"
  val eoriKey = "eori"
  implicit val format = Json.format[CachedData]
}

@Singleton
class SessionCache @Inject()(appConfig: AppConfig, mongo: ReactiveMongoComponent, save4LaterService: Save4LaterService)(
  implicit ec: ExecutionContext
) extends CacheMongoRepository("session-cache", appConfig.ttl.toSeconds)(mongo.mongoConnector.db, ec) {

  private def sessionId(implicit hc: HeaderCarrier): Id =
    hc.sessionId match {
      case None =>
        // $COVERAGE-OFF$
        throw new IllegalStateException("Session id is not available")
      // $COVERAGE-ON$
      case Some(sessionId) => model.Id(sessionId.value)
    }

  def saveRegistrationDetails(rd: RegistrationDetails)(implicit hc: HeaderCarrier): Future[Boolean] =
    createOrUpdate(sessionId, regDetailsKey, Json.toJson(rd)) map (_ => true)

  def saveRegistrationDetails(
    rd: RegistrationDetails,
    internalId: InternalId,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier): Future[Boolean] =
    for {
      _ <- save4LaterService.saveOrgType(internalId, orgType)
      createdOrUpdated <- saveRegistrationDetails(rd)
    } yield createdOrUpdated
  //ROW
  def saveRegistrationDetailsWithoutId(
    rd: RegistrationDetails,
    internalId: InternalId,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier): Future[Boolean] =
    for {
      _ <- save4LaterService.saveSafeId(internalId, rd.safeId)
      _ <- save4LaterService.saveOrgType(internalId, orgType)
      createdOrUpdated <- saveRegistrationDetails(rd)
    } yield createdOrUpdated

  def saveRegisterWithEoriAndIdResponse(
    rd: RegisterWithEoriAndIdResponse
  )(implicit hc: HeaderCarrier): Future[Boolean] =
    createOrUpdate(sessionId, registerWithEoriAndIdResponseKey, Json.toJson(rd)) map (_ => true)

  def saveSubscriptionCreateOutcome(subscribeOutcome: SubscriptionCreateOutcome)(implicit hc: HeaderCarrier): Future[Boolean] =
    createOrUpdate(sessionId, subscriptionCreateOutcomeKey, Json.toJson(subscribeOutcome)) map (_ => true)

  def saveSubscriptionStatusOutcome(subscriptionStatusOutcome: SubscriptionStatusOutcome)(implicit hc: HeaderCarrier): Future[Boolean] =
    createOrUpdate(sessionId, subscriptionStatusOutcomeKey, Json.toJson(subscriptionStatusOutcome)) map (_ => true)

  def saveRegistrationInfo(rd: RegistrationInfo)(implicit hc: HeaderCarrier): Future[Boolean] =
    createOrUpdate(sessionId, regInfoKey, Json.toJson(rd)) map (_ => true)

  def saveSubscriptionDetails(rdh: SubscriptionDetails)(implicit hc: HeaderCarrier): Future[Boolean] =
    createOrUpdate(sessionId, subDetailsKey, Json.toJson(rdh)) map (_ => true)

  def saveEmail(email: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    createOrUpdate(sessionId, emailKey, Json.toJson(email)) map (_ => true)

  def saveHasNino(hasNino: Boolean)(implicit hc: HeaderCarrier): Future[Boolean] =
    createOrUpdate(sessionId, hasNinoKey, Json.toJson(hasNino)) map (_ => true)

  def saveEori(eori: Eori)(implicit hc: HeaderCarrier): Future[Boolean] =
    createOrUpdate(sessionId, eoriKey, Json.toJson(eori.id)) map (_ => true)

  private def getCached[T](sessionId: Id, t: (CachedData, Id) => T)(implicit hc: HeaderCarrier): Future[T] =
    findById(sessionId.id).map {
      case Some(Cache(_, Some(data), _, _)) =>
        Json.fromJson[CachedData](data) match {
          case d: JsSuccess[CachedData] => t(d.value, sessionId)
          case _ => {
            // $COVERAGE-OFF$
            CdsLogger.warn(s"No Session data is cached for the sessionId : ${sessionId.id}")
            throw SessionTimeOutException(s"No Session data is cached for the sessionId : ${sessionId.id}")
            // $COVERAGE-ON$
          }
        }
      case _ => {
        // $COVERAGE-OFF$
        CdsLogger.warn(s"No match session id for signed in user with session: ${sessionId.id}")
        throw SessionTimeOutException(s"No match session id for signed in user with session : ${sessionId.id}")
        // $COVERAGE-ON$
      }
    }

  def subscriptionDetails(implicit hc: HeaderCarrier): Future[SubscriptionDetails] =
    getCached[SubscriptionDetails](sessionId, (cachedData, id) => cachedData.subscriptionDetails(id))

  def eori(implicit hc: HeaderCarrier): Future[Option[String]] =
    getCached[Option[String]](sessionId, (cachedData, id) => cachedData.eori)

  def email(implicit hc: HeaderCarrier): Future[String] =
    getCached[String](sessionId, (cachedData, id) => cachedData.email(id))

  def hasNino(implicit hc: HeaderCarrier): Future[Option[Boolean]] =
    getCached[Option[Boolean]](sessionId, (cachedData, _) => cachedData.hasNino)

  def mayBeEmail(implicit hc: HeaderCarrier): Future[Option[String]] =
    getCached[Option[String]](sessionId, (cachedData, _) => cachedData.email)

  def safeId(implicit hc: HeaderCarrier): Future[SafeId] =
    getCached[SafeId](sessionId, (cachedData, id) => cachedData.safeId(id))

  def name(implicit hc: HeaderCarrier): Future[Option[String]] =
    getCached[Option[String]](sessionId, (cachedData, _) => cachedData.regDetails.map(_.name))

  def registrationDetails(implicit hc: HeaderCarrier): Future[RegistrationDetails] =
    getCached[RegistrationDetails](sessionId, (cachedData, id) => cachedData.registrationDetails(id))

  def registerWithEoriAndIdResponse(implicit hc: HeaderCarrier): Future[RegisterWithEoriAndIdResponse] =
    getCached[RegisterWithEoriAndIdResponse](
      sessionId,
      (cachedData, id) => cachedData.registerWithEoriAndIdResponse(id)
    )

  def subscriptionStatusOutcome(implicit hc: HeaderCarrier): Future[SubscriptionStatusOutcome] =
    getCached[SubscriptionStatusOutcome](sessionId, (cachedData, id) => cachedData.subscriptionStatusOutcome(id))

  def subscriptionCreateOutcome(implicit hc: HeaderCarrier): Future[SubscriptionCreateOutcome] =
    getCached[SubscriptionCreateOutcome](sessionId, (cachedData, id) => cachedData.subscriptionCreateOutcome(id))

  def registrationInfo(implicit hc: HeaderCarrier): Future[RegistrationInfo] =
    getCached[RegistrationInfo](sessionId, (cachedData, id) => cachedData.registrationInfo(id))

  def remove(implicit hc: HeaderCarrier): Future[Boolean] =
    removeById(sessionId.id) map (x => x.writeErrors.isEmpty && x.writeConcernError.isEmpty)
}

case class SessionTimeOutException(errorMessage: String) extends NoStackTrace
