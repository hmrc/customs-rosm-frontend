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

package uk.gov.hmrc.customs.rosmfrontend.domain.subscription

import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.SubscriptionFlowConfig
import uk.gov.hmrc.customs.rosmfrontend.domain.CdsOrganisationType._
import uk.gov.hmrc.customs.rosmfrontend.models.Journey

object SubscriptionFlows {

  private val individualFlowConfig = createFlowConfig(
    Journey.GetYourEORI,
    List(ContactDetailsSubscriptionFlowPageGetEori,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori,
      ContactDetailsAddressSubscriptionFlowPageGetEori,
      EoriConsentSubscriptionFlowPage)
  )

  private val soleTraderFlowConfig = createFlowConfig(
    Journey.GetYourEORI,
    List(
      ContactDetailsSubscriptionFlowPageGetEori,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori,
      ContactDetailsAddressSubscriptionFlowPageGetEori,
      SicCodeSubscriptionFlowPage,
      VatRegisteredUkSubscriptionFlowPage,
      VatDetailsSubscriptionFlowPage,
      VatRegisteredEuSubscriptionFlowPage,
      VatEUIdsSubscriptionFlowPage,
      VatEUConfirmSubscriptionFlowPage,
      EoriConsentSubscriptionFlowPage
    )
  )

  private val corporateFlowConfig = createFlowConfig(
    Journey.GetYourEORI,
    List(
      DateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageGetEori,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori,
      ContactDetailsAddressSubscriptionFlowPageGetEori,
      BusinessShortNameSubscriptionFlowPage,
      SicCodeSubscriptionFlowPage,
      VatRegisteredUkSubscriptionFlowPage,
      VatDetailsSubscriptionFlowPage,
      VatRegisteredEuSubscriptionFlowPage,
      VatEUIdsSubscriptionFlowPage,
      VatEUConfirmSubscriptionFlowPage,
      EoriConsentSubscriptionFlowPage
    )
  )
  private val partnershipFlowConfig = createFlowConfig(
    Journey.GetYourEORI,
    List(
      DateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageGetEori,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori,
      ContactDetailsAddressSubscriptionFlowPageGetEori,
      BusinessShortNameSubscriptionFlowPage,
      SicCodeSubscriptionFlowPage,
      VatRegisteredUkSubscriptionFlowPage,
      VatDetailsSubscriptionFlowPage,
      VatRegisteredEuSubscriptionFlowPage,
      VatEUIdsSubscriptionFlowPage,
      VatEUConfirmSubscriptionFlowPage,
      EoriConsentSubscriptionFlowPage
    )
  )

  private val thirdCountryIndividualFlowConfig = createFlowConfig(
    Journey.GetYourEORI,
    List(ContactDetailsSubscriptionFlowPageGetEori,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori,
      ContactDetailsAddressSubscriptionFlowPageGetEori,
      EoriConsentSubscriptionFlowPage)
  )

  private val thirdCountrySoleTraderFlowConfig = createFlowConfig(
    Journey.GetYourEORI,
    List(
      ContactDetailsSubscriptionFlowPageGetEori,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori,
      ContactDetailsAddressSubscriptionFlowPageGetEori,
      SicCodeSubscriptionFlowPage,
      VatRegisteredUkSubscriptionFlowPage,
      VatDetailsSubscriptionFlowPage,
      VatRegisteredEuSubscriptionFlowPage,
      VatEUIdsSubscriptionFlowPage,
      VatEUConfirmSubscriptionFlowPage,
      EoriConsentSubscriptionFlowPage
    )
  )

  private val thirdCountryCorporateFlowConfig = createFlowConfig(
    Journey.GetYourEORI,
    List(
      DateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageGetEori,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori,
      ContactDetailsAddressSubscriptionFlowPageGetEori,
      BusinessShortNameSubscriptionFlowPage,
      SicCodeSubscriptionFlowPage,
      VatRegisteredUkSubscriptionFlowPage,
      VatDetailsSubscriptionFlowPage,
      VatRegisteredEuSubscriptionFlowPage,
      VatEUIdsSubscriptionFlowPage,
      VatEUConfirmSubscriptionFlowPage,
      EoriConsentSubscriptionFlowPage
    )
  )

  private val soleTraderRegExistingEoriFlowConfig = createFlowConfig(
    Journey.Migrate,
    List(
      EoriNumberSubscriptionFlowPage,
      NameDobDetailsSubscriptionFlowPage,
      ConfirmYourIdentityControllerFlowPage,
      WhatIsYourIdentifierControllerFlowPage,
      AddressDetailsSubscriptionFlowPage
    )
  )

  private val corporateRegExistingEoriFlowConfig = createFlowConfig(
    Journey.Migrate,
    List(
      EoriNumberSubscriptionFlowPage,
      NameUtrDetailsSubscriptionFlowPage,
      DateOfEstablishmentSubscriptionFlowPageMigrate,
      AddressDetailsSubscriptionFlowPage
    )
  )

  private val migrationEoriRowSoleTraderAndIndividualFlowConfig = createFlowConfig(
    Journey.Migrate,
    List(
      EoriNumberSubscriptionFlowPage,
      NameDobDetailsSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageMigrate,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageMigrate,
      ContactDetailsAddressSubscriptionFlowPageMigrate
    )
  )

  private val migrationEoriRowSoleTraderAndIndividualFlowConfigUtrNinoEnabled = createFlowConfig(
    Journey.Migrate,
    List(
      EoriNumberSubscriptionFlowPage,
      NameDobDetailsSubscriptionFlowPage,
      UtrSubscriptionFlowPage,
      NinoSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageMigrate,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageMigrate,
      ContactDetailsAddressSubscriptionFlowPageMigrate
    )
  )

  private val migrationEoriRowCorporateFlowConfig = createFlowConfig(
    Journey.Migrate,
    List(
      EoriNumberSubscriptionFlowPage,
      NameDetailsSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage,
      RowDateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageMigrate,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageMigrate,
      ContactDetailsAddressSubscriptionFlowPageMigrate
    )
  )

  private val migrationEoriRowCorporateFlowConfigUtrNinoEnabled = createFlowConfig(
    Journey.Migrate,
    List(
      EoriNumberSubscriptionFlowPage,
      NameDetailsSubscriptionFlowPage,
      UtrSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage,
      RowDateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageMigrate,
      ContactDetailsIsThisRightAddressSubscriptionFlowPageMigrate,
      ContactDetailsAddressSubscriptionFlowPageMigrate
    )
  )

  val flows: Map[SubscriptionFlow, SubscriptionFlowConfig] = Map(
    OrganisationSubscriptionFlow -> corporateFlowConfig,
    PartnershipSubscriptionFlow -> partnershipFlowConfig,
    SoleTraderSubscriptionFlow -> soleTraderFlowConfig,
    IndividualSubscriptionFlow -> individualFlowConfig,
    ThirdCountryOrganisationSubscriptionFlow -> thirdCountryCorporateFlowConfig,
    ThirdCountrySoleTraderSubscriptionFlow -> thirdCountrySoleTraderFlowConfig,
    ThirdCountryIndividualSubscriptionFlow -> thirdCountryIndividualFlowConfig,
    MigrationEoriOrganisationSubscriptionFlow -> corporateRegExistingEoriFlowConfig,
    MigrationEoriPartnershipSubscriptionFlow -> corporateRegExistingEoriFlowConfig,
    MigrationEoriSoleTraderSubscriptionFlow -> soleTraderRegExistingEoriFlowConfig,
    MigrationEoriIndividualSubscriptionFlow -> soleTraderRegExistingEoriFlowConfig,
    MigrationEoriRowOrganisationSubscriptionFlow -> migrationEoriRowCorporateFlowConfig,
    MigrationEoriRowSoleTraderSubscriptionFlow -> migrationEoriRowSoleTraderAndIndividualFlowConfig,
    MigrationEoriRowIndividualSubscriptionFlow -> migrationEoriRowSoleTraderAndIndividualFlowConfig,
    MigrationEoriRowOrganisationSubscriptionUtrNinoEnabledFlow -> migrationEoriRowCorporateFlowConfigUtrNinoEnabled,
    MigrationEoriRowIndividualsSubscriptionUtrNinoEnabledFlow -> migrationEoriRowSoleTraderAndIndividualFlowConfigUtrNinoEnabled
  )

  private def createFlowConfig(journey: Journey.Value, flowStepList: List[SubscriptionPage]): SubscriptionFlowConfig =
    journey match {
      case Journey.Migrate =>
        SubscriptionFlowConfig(
          pageBeforeFirstFlowPage = RegistrationConfirmPage,
          flowStepList,
          pageAfterLastFlowPage = ReviewDetailsPageSubscription
        )
      case _ =>
        SubscriptionFlowConfig(
          pageBeforeFirstFlowPage = RegistrationConfirmPage,
          flowStepList,
          pageAfterLastFlowPage = ReviewDetailsPageGetYourEORI
        )
    }

  def apply(subscriptionFlow: SubscriptionFlow): SubscriptionFlowConfig = flows(subscriptionFlow)
}

case class SubscriptionFlowInfo(stepNumber: Int, totalSteps: Int, nextPage: SubscriptionPage)

sealed abstract class SubscriptionFlow(val name: String, val isIndividualFlow: Boolean)

case object OrganisationSubscriptionFlow extends SubscriptionFlow("Organisation", isIndividualFlow = false)

case object PartnershipSubscriptionFlow extends SubscriptionFlow("Partnership", isIndividualFlow = false)

case object IndividualSubscriptionFlow extends SubscriptionFlow("Individual", isIndividualFlow = true)

case object ThirdCountryOrganisationSubscriptionFlow
    extends SubscriptionFlow(ThirdCountryOrganisation.id, isIndividualFlow = false)

case object ThirdCountrySoleTraderSubscriptionFlow
    extends SubscriptionFlow(ThirdCountrySoleTrader.id, isIndividualFlow = true)

case object ThirdCountryIndividualSubscriptionFlow
    extends SubscriptionFlow(ThirdCountryIndividual.id, isIndividualFlow = true)

case object SoleTraderSubscriptionFlow extends SubscriptionFlow(SoleTrader.id, isIndividualFlow = true)

case object MigrationEoriOrganisationSubscriptionFlow
    extends SubscriptionFlow("migration-eori-Organisation", isIndividualFlow = false)

case object MigrationEoriIndividualSubscriptionFlow
    extends SubscriptionFlow("migration-eori-Individual", isIndividualFlow = true)

case object MigrationEoriSoleTraderSubscriptionFlow
    extends SubscriptionFlow("migration-eori-sole-trader", isIndividualFlow = true)

case object MigrationEoriPartnershipSubscriptionFlow
    extends SubscriptionFlow("migration-eori-Partnership", isIndividualFlow = false)

case object MigrationEoriRowOrganisationSubscriptionFlow
    extends SubscriptionFlow("migration-eori-row-Organisation", isIndividualFlow = false)

case object MigrationEoriRowSoleTraderSubscriptionFlow
    extends SubscriptionFlow("migration-eori-row-sole-trader", isIndividualFlow = true)

case object MigrationEoriRowIndividualSubscriptionFlow
    extends SubscriptionFlow("migration-eori-row-Individual", isIndividualFlow = true)

case object MigrationEoriRowOrganisationSubscriptionUtrNinoEnabledFlow
    extends SubscriptionFlow("migration-eori-row-utrNino-enabled-Organisation", isIndividualFlow = false)

case object MigrationEoriRowIndividualsSubscriptionUtrNinoEnabledFlow
    extends SubscriptionFlow("migration-eori-row-utrNino-enabled-Individual", isIndividualFlow = true)

object SubscriptionFlow {
  def apply(flowName: String): SubscriptionFlow =
    SubscriptionFlows.flows.keys
      .find(_.name == flowName)
      .fold(throw new IllegalStateException(s"Incorrect Subscription flowname $flowName"))(identity)
}

sealed abstract class SubscriptionPage(val url: String)

case object ContactDetailsSubscriptionFlowPageGetEori
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.ContactDetailsController
        .createForm(journey = Journey.GetYourEORI)
        .url
    )

case object ContactDetailsIsThisRightAddressSubscriptionFlowPageGetEori
  extends SubscriptionPage(
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.ContactDetailsIsRightAddressController
      .createForm(journey = Journey.GetYourEORI)
      .url
  )

case object ContactDetailsAddressSubscriptionFlowPageGetEori
  extends SubscriptionPage(
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.ContactDetailsAddressController
      .createForm(journey = Journey.GetYourEORI)
      .url
  )
case object ContactDetailsSubscriptionFlowPageMigrate
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.ContactDetailsController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object ContactDetailsIsThisRightAddressSubscriptionFlowPageMigrate
  extends SubscriptionPage(
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.ContactDetailsIsRightAddressController
      .createForm(journey = Journey.Migrate)
      .url
  )

case object ContactDetailsAddressSubscriptionFlowPageMigrate
  extends SubscriptionPage(
    uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.ContactDetailsAddressController
      .createForm(journey = Journey.Migrate)
      .url
  )

case object UtrSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.HaveUtrSubscriptionController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object NinoSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.HaveNinoSubscriptionController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object AddressDetailsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.routes.AddressController.createForm(journey = Journey.Migrate).url
    )

case object NameUtrDetailsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.NameIDOrgController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object NameDetailsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.NameOrgController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object NameDobDetailsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.NameDobSoleTraderController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object HowCanWeIdentifyYouSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.HowCanWeIdentifyYouController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object ConfirmYourIdentityControllerFlowPage
  extends SubscriptionPage(
    uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.ConfirmYourIdentityController
      .form(journey = Journey.Migrate)
      .url
  )
case object WhatIsYourIdentifierControllerFlowPage
  extends SubscriptionPage(
    uk.gov.hmrc.customs.rosmfrontend.controllers.migration.routes.WhatIsYourIdentifierController
      .form(journey = Journey.Migrate)
      .url
  )

case object RowDateOfEstablishmentSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.DateOfEstablishmentController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object DateOfEstablishmentSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.DateOfEstablishmentController
        .createForm(journey = Journey.GetYourEORI)
        .url
    )

case object DateOfEstablishmentSubscriptionFlowPageMigrate
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.DateOfEstablishmentController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object VatRegisteredUkSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.VatRegisteredUkController
        .createForm(journey = Journey.GetYourEORI)
        .url
    )

//case object VatGroupFlowPage extends SubscriptionPage(uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.VatGroupController.createForm(journey = Journey.GetYourEORI).url)

case object BusinessShortNameSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.BusinessShortNameController
        .createForm(journey = Journey.GetYourEORI)
        .url
    )

case object VatDetailsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.VatDetailsController
        .createForm(journey = Journey.GetYourEORI)
        .url
    )

case object VatRegisteredEuSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.VatRegisteredEuController
        .createForm(journey = Journey.GetYourEORI)
        .url
    )

case object VatEUIdsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.VatDetailsEuController
        .createForm(journey = Journey.GetYourEORI)
        .url
    )

case object VatEUConfirmSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.VatDetailsEuConfirmController
        .createForm(journey = Journey.GetYourEORI)
        .url
    )

case object EoriConsentSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.DisclosePersonalDetailsConsentController
        .createForm(journey = Journey.GetYourEORI)
        .url
    )

case object SicCodeSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.SicCodeController
        .createForm(journey = Journey.GetYourEORI)
        .url
    )

case object EoriNumberSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.WhatIsYourEoriController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object EmailSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.email.routes.WhatIsYourEmailController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object CheckYourEmailSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.email.routes.CheckYourEmailController
        .createForm(journey = Journey.Migrate)
        .url
    )

case object ReviewDetailsPageGetYourEORI
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController
        .determineRoute(journey = Journey.GetYourEORI)
        .url
    )

case object ReviewDetailsPageSubscription
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.routes.DetermineReviewPageController
        .determineRoute(journey = Journey.Migrate)
        .url
    )

case object RegistrationConfirmPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.ConfirmContactDetailsController
        .form(journey = Journey.GetYourEORI)
        .url
    )

case object ConfirmIndividualTypePage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes.ConfirmIndividualTypeController
        .form(journey = Journey.GetYourEORI)
        .url
    )

case object UserLocationPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.UserLocationController
        .form(journey = Journey.Migrate)
        .url
    )

case object BusinessDetailsRecoveryPage
    extends SubscriptionPage(
      uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes.BusinessDetailsRecoveryController
        .form(journey = Journey.GetYourEORI)
        .url
    )

case class PreviousPage(override val url: String) extends SubscriptionPage(url)
