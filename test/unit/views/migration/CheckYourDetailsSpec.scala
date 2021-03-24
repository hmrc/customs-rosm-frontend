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

package unit.views.migration

import org.joda.time.LocalDate
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.forms.models.subscription.{AddressViewModel, ContactDetailsModel}
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.check_your_details
import util.ViewSpec

class CheckYourDetailsSpec extends ViewSpec {

  val view = app.injector.instanceOf[check_your_details]

  private val domIds = Table(
    ("name", "headingId", "changeLinkId"),
    ("eori", "review-tbl__eori-number_heading", "review-tbl__eori-number_change"),
    ("utr", "review-tbl__utr_heading", "review-tbl__utr_change"),
    ("nameAndAddress", "review-tbl__name-and-address_heading", "review-tbl__name-and-address_change"),
    ("contactAddress", "review-tbl__contact-details_heading", "review-tbl__contact-details_change")
  )

  private val organisationType = Some(CdsOrganisationType.Company)
  private val contactDetails = Some(
    ContactDetailsModel(
      "John Doe",
      "email@example.com",
      "11111111111",
      None,
      useAddressFromRegistrationDetails = Some(true),
      Some("Street"),
      Some("City"),
      Some("POSTCODE"),
      Some("GB")
    )
  )
  private val address = Some(AddressViewModel("Street", "City", Some("Postcode"), "GB"))
  private val sicCode = Some("00001")
  private val eori = Some("ZZ123456789112")
  private val email = Some("email@example.com")
  private val utr = Some(Utr("UTRXXXXX"))
  private val nameIdOrg = Some(NameIdOrganisationMatchModel("Name", utr.get.id))
  private val dateTime = Some(LocalDate.now())
  private val nino = Some(Nino("AB123456C"))
  private val nameDobMatchModel = Some(NameDobMatchModel("FName", None, "LName", LocalDate.parse("2003-04-08")))

  private def strim(s: String): String = s.stripMargin.trim.lines mkString " "

  "Check Your Answers Page" should {
    forAll(domIds) { (name, headingId, changeLinkId) =>
      s"have the heading name as non-visual text for $name in the change link" in {
        val headingText = doc().body.getElementById(headingId).text
        val changeLinkText = doc().body.getElementById(changeLinkId).text
        changeLinkText must include(headingText)
      }

      s"have the class 'hidden' for the change $name link" in {
        val changeLink = doc().body.getElementById(changeLinkId).getElementsByTag("span").first
        changeLink.hasClass("visually-hidden") mustBe true
      }
    }
  }

  "Check Your Answers Page" should {
    "display the review page for organisation type 'Company'" in {
      val page = doc(customsId = None)
      page.title must startWith("Check your answers")
      page.getElementById("review-tbl__utr_heading").text mustBe "Corporation Tax UTR number"
      page.getElementById("review-tbl__name-and-address_heading").text mustBe "Company address"
      page.getElementById("review-tbl__contact-details_heading").text mustBe "Contact"
    }

    "display the review page for 'SoleTrader' with 'Nino'" in {
      val page = doc(true, customsId = nino)
      page.title must startWith("Check your answers")
      page.body.getElementById("review-tbl__full-name_heading").text mustBe "Full name"
      page.body.getElementById("review-tbl__full-name").text mustBe "FName LName"

      page.body.getElementById("review-tbl__date-of-birth_heading").text mustBe "Date of birth"
      page.body.getElementById("review-tbl__date-of-birth").text mustBe "8 April 2003"

      page.body.getElementById("review-tbl__nino_heading").text mustBe "National Insurance number"
      page.body.getElementById("review-tbl__nino").text mustBe "AB123456C"

      page.body.getElementById("review-tbl__eori-number_heading").text mustBe "EORI number"
      page.body.getElementById("review-tbl__eori-number").text mustBe "ZZ123456789112"
      page.body
        .getElementById("review-tbl__eori-number_change")
        .attr("href") mustBe "/customs/subscribe-for-cds/matching/what-is-your-eori/review"
      page.body.getElementById("review-tbl__name-and-address_heading").text mustBe "Address"
      page.body.getElementById("review-tbl__name-and-address").text mustBe strim("""
          |Street
          |City
          |Postcode
          |United Kingdom
        """)
      page.body
        .getElementById("review-tbl__name-and-address_change")
        .attr("href") mustBe "/customs/subscribe-for-cds/address/review"
      page.body.getElementById("review-tbl__email_heading").text mustBe "Email address"
      page.body.getElementById("review-tbl__email").text mustBe "email@example.com"
    }

    "display the review page for 'Sole Trader' with 'UTR'" in {
      val page = doc(isIndividualSubscriptionFlow = true, customsId = utr)

      page.title must startWith("Check your answers")
      page.body.getElementById("review-tbl__full-name_heading").text mustBe "Full name"
      page.body.getElementById("review-tbl__full-name").text mustBe "FName LName"

      page.body.getElementById("review-tbl__date-of-birth_heading").text mustBe "Date of birth"
      page.body.getElementById("review-tbl__date-of-birth").text mustBe "8 April 2003"

      page.body.getElementById("review-tbl__utr_heading").text mustBe "UTR number"
      page.body.getElementById("review-tbl__utr").text mustBe "UTRXXXXX"

      page.body.getElementById("review-tbl__eori-number_heading").text mustBe "EORI number"
      page.body.getElementById("review-tbl__eori-number").text mustBe "ZZ123456789112"
      page.body
        .getElementById("review-tbl__eori-number_change")
        .attr("href") mustBe "/customs/subscribe-for-cds/matching/what-is-your-eori/review"

      page.body.getElementById("review-tbl__name-and-address_heading").text mustBe "Address"
      page.body.getElementById("review-tbl__name-and-address").text mustBe strim("""
          |Street
          |City
          |Postcode
          |United Kingdom
        """)
      page.body
        .getElementById("review-tbl__name-and-address_change")
        .attr("href") mustBe "/customs/subscribe-for-cds/address/review"
      page.body.getElementById("review-tbl__email_heading").text mustBe "Email address"
      page.body.getElementById("review-tbl__email").text mustBe "email@example.com"
    }

    "not display the address country code in the UK case" in {
      val page = doc(isIndividualSubscriptionFlow = false, customsId = utr)
      page.getElementById("review-tbl__name-and-address").text mustBe strim("""|Street
           |City
           |Postcode
           |United Kingdom
        """)
    }

    "display the review page for 'SoleTrader' when 'No Nino' is entered" in {
      val page = doc(true, customsId = None)
      page.title must startWith("Check your answers")
      page.body.getElementById("review-tbl__full-name_heading").text mustBe "Full name"
      page.body.getElementById("review-tbl__full-name").text mustBe "FName LName"

      page.body.getElementById("review-tbl__date-of-birth_heading").text mustBe "Date of birth"
      page.body.getElementById("review-tbl__date-of-birth").text mustBe "8 April 2003"

      page.body.getElementById("review-tbl__eori-number_heading").text mustBe "EORI number"
      page.body.getElementById("review-tbl__eori-number").text mustBe "ZZ123456789112"
      page.body
        .getElementById("review-tbl__eori-number_change")
        .attr("href") mustBe "/customs/subscribe-for-cds/matching/what-is-your-eori/review"
      page.body.getElementById("review-tbl__name-and-address_heading").text mustBe "Address"
      page.body.getElementById("review-tbl__name-and-address").text mustBe strim("""
          |Street
          |City
          |Postcode
          |United Kingdom
        """)
      page.body().getElementById("review-tbl__contact-details_heading").text mustBe "Contact"
      page.body().getElementById("review-tbl__contact-details").text mustBe strim("""
          |John Doe
          |11111111111
          |Street
          |City
          |POSTCODE
          |United Kingdom
        """.stripMargin)
      page.body
        .getElementById("review-tbl__name-and-address_change")
        .attr("href") mustBe "/customs/subscribe-for-cds/address/review"
      page.body.getElementById("review-tbl__email_heading").text mustBe "Email address"
      page.body.getElementById("review-tbl__email").text mustBe "email@example.com"
    }

    "display the review page for 'Sole Trader' when 'No UTR' is entered" in {
      val page = doc(isIndividualSubscriptionFlow = true, customsId = None)

      page.title must startWith("Check your answers")
      page.body.getElementById("review-tbl__full-name_heading").text mustBe "Full name"
      page.body.getElementById("review-tbl__full-name").text mustBe "FName LName"

      page.body.getElementById("review-tbl__date-of-birth_heading").text mustBe "Date of birth"
      page.body.getElementById("review-tbl__date-of-birth").text mustBe "8 April 2003"

      page.body.getElementById("review-tbl__utr_heading").text mustBe "UTR number"
      page.body.getElementById("review-tbl__utr").text mustBe "UTRXXXXX"

      page.body.getElementById("review-tbl__eori-number_heading").text mustBe "EORI number"
      page.body.getElementById("review-tbl__eori-number").text mustBe "ZZ123456789112"
      page.body
        .getElementById("review-tbl__eori-number_change")
        .attr("href") mustBe "/customs/subscribe-for-cds/matching/what-is-your-eori/review"

      page.body.getElementById("review-tbl__name-and-address_heading").text mustBe "Address"
      page.body.getElementById("review-tbl__name-and-address").text mustBe strim("""
          |Street
          |City
          |Postcode
          |United Kingdom
        """)
      page.body().getElementById("review-tbl__contact-details_heading").text mustBe "Contact"
      page.body().getElementById("review-tbl__contact-details").text mustBe strim("""
          |John Doe
          |11111111111
          |Street
          |City
          |POSTCODE
          |United Kingdom
        """.stripMargin)
      page.body
        .getElementById("review-tbl__name-and-address_change")
        .attr("href") mustBe "/customs/subscribe-for-cds/address/review"
      page.body.getElementById("review-tbl__email_heading").text mustBe "Email address"
      page.body.getElementById("review-tbl__email").text mustBe "email@example.com"
    }

    "display address label for the following Company organisation types" in {
      val page = doc(orgType = Some(CdsOrganisationType.Company))
      page.body.getElementById("review-tbl__name-and-address_heading").text mustBe "Company address"
    }

    "display address label for the following Partnership organisation types" in {
      val page = doc(orgType = Some(CdsOrganisationType.Partnership))
      page.body.getElementById("review-tbl__name-and-address_heading").text mustBe "Partnership address"
    }

    "display address label for the following LimitedLiabilityPartnership organisation types" in {
      val page = doc(orgType = Some(CdsOrganisationType.LimitedLiabilityPartnership))
      page.body.getElementById("review-tbl__name-and-address_heading").text mustBe "Partnership address"
    }

    "display address label for the following CharityPublicBodyNotForProfit organisation types" in {
      val page = doc(orgType = Some(CdsOrganisationType.CharityPublicBodyNotForProfit))
      page.body.getElementById("review-tbl__name-and-address_heading").text mustBe "Organisation address"
    }

    "display address label for the following EUOrganisation organisation types" in {
      val page = doc(orgType = Some(CdsOrganisationType.EUOrganisation))
      page.body.getElementById("review-tbl__name-and-address_heading").text mustBe "Organisation address"
    }

    "display address label for the following ThirdCountryOrganisation organisation types" in {
      val page = doc(orgType = Some(CdsOrganisationType.ThirdCountryOrganisation))
      page.body.getElementById("review-tbl__name-and-address_heading").text mustBe "Organisation address"
    }

    "not display change link for the following for utr if isThirdCountrySubscription " in {
      val page = doc(
        orgType = Some(CdsOrganisationType.ThirdCountryOrganisation),
        isThirdCountrySubscription = true,
        nameIdOrganisationDetails = None
      )
      page.body.getElementById("review-tbl__utr_change") mustBe null
    }

    "not display change link for the following for nino if isThirdCountrySubscription " in {
      val page = doc(
        customsId = nino,
        orgType = Some(CdsOrganisationType.ThirdCountryOrganisation),
        isThirdCountrySubscription = true,
        nameIdOrganisationDetails = None
      )
      page.body.getElementById("review-tbl__nino_change") mustBe null
    }

    "not display change link if eori is from existing enrolment" in {

      val page = doc(existingEoriNumber = Some("ZZ123456789112"))

      page.body.getElementById("review-tbl__eori-number_change") mustBe null
    }
  }

  def doc(
    isIndividualSubscriptionFlow: Boolean = false,
    customsId: Option[CustomsId] = utr,
    orgType: Option[CdsOrganisationType] = organisationType,
    nameDobMatchModel: Option[NameDobMatchModel] = nameDobMatchModel,
    isThirdCountrySubscription: Boolean = false,
    nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = nameIdOrg,
    existingEoriNumber: Option[String] = None
  ): Document = {

    implicit val request = withFakeCSRF(FakeRequest().withSession(("selected-user-location", "third-country")))

    val result = view(
      isThirdCountrySubscription,
      isIndividualSubscriptionFlow,
      orgType,
      contactDetails,
      address,
      sicCode,
      eori,
      existingEoriNumber,
      email,
      nameIdOrganisationDetails,
      None,
      nameDobMatchModel,
      dateTime,
      None,
      customsId,
      Journey.Migrate
    )
    Jsoup.parse(contentAsString(result))
  }
}
