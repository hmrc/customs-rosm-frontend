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

package unit.controllers.registration

import java.util.UUID

import common.pages.matching.NameIdOrganisationPage._
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.NameIdOrganisationController
import uk.gov.hmrc.customs.rosmfrontend.domain.Utr
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.matching.Organisation
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.registration.MatchingService
import uk.gov.hmrc.customs.rosmfrontend.views.html.registration.match_name_id_organisation
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.matching.NameIdOrganisationFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NameUtrOrganisationControllerSpec extends ControllerSpec with MockitoSugar with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockMatchingService = mock[MatchingService]

  private val matchNameIdOrganisationView = app.injector.instanceOf[match_name_id_organisation]

  private val controller =
    new NameIdOrganisationController(app, mockAuthConnector, mcc, matchNameIdOrganisationView, mockMatchingService)

  private val utrDescriptionPartnership = "Partnership Self Assessment Unique Taxpayer Reference (UTR) number"
  private val utrDescriptionCorporationTax = "Corporation Tax Unique Taxpayer Reference (UTR) number"
  private val utrDescriptionOrganisation = "Organisation Self Assessment Unique Taxpayer Reference (UTR) number"

  private val organisationTypeOrganisations =
    Table(
      ("organisationType", "organisation", "nameDescription", "utrDescription"),
      ("company", CompanyOrganisation, "Registered business", utrDescriptionCorporationTax),
      ("partnership", PartnershipOrganisation, "Registered partnership", utrDescriptionPartnership),
      (
        "limited-liability-partnership",
        LimitedLiabilityPartnershipOrganisation,
        "Registered partnership",
        utrDescriptionPartnership
      ),
      (
        "charity-public-body-not-for-profit",
        CharityPublicBodyNotForProfitOrganisation,
        "Organisation",
        utrDescriptionOrganisation
      )
    )
  private val NameMaxLength = 105

  private val UtrInvalidError = "Enter a valid Unique Taxpayer Reference"
  private val UtrWrongLengthError = "The Unique Taxpayer Reference must be 10 numbers"
  private val BusinessNotMatchedError =
    "Your business details have not been found. Check that your details are correct and try again."

  private def defaultOrganisationType: String = organisationTypeOrganisations.head._1

  private def invalidOrganisationTypeMessage(organisationType: String): String =
    s"Invalid organisation type '$organisationType'."

  private def nameError(nameDescription: String): String =
    s"Enter the $nameDescription name"

  private def utrError(utrDescription: String): String =
    s"Enter the $utrDescription"

  override def beforeEach: Unit =
    reset(mockMatchingService)

  "Viewing the Name and Utr Organisation Matching form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.form(defaultOrganisationType, Journey.GetYourEORI)
    )

    "display the form" in {
      showForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe empty
        page.getElementsText(fieldLevelErrorUtr) shouldBe empty
        page.getElementsText(fieldLevelErrorName) shouldBe empty

      }
    }

    forAll(organisationTypeOrganisations) { (organisationType, _, _, _) =>
      {

        s"ensure the labels are correct for $organisationType" in {
          submitForm(form = ValidNameUtrRequest + ("name" -> ""), organisationType) { result =>
            status(result) shouldBe BAD_REQUEST
            val page = CdsPage(bodyOf(result))
            val labelForName = organisationType match {
              case "partnership" => "Registered partnership name Error: Enter your registered partnership name"
              case "limited-liability-partnership" =>
                "Registered partnership name This is on your certificate of incorporation from Companies House. Error: Enter your registered partnership name"
              case "charity-public-body-not-for-profit" =>
                "Organisation name This is on your certificate of incorporation from Companies House. Error: Enter your registered organisation name"
              case _ =>
                "Registered company name This is on your certificate of incorporation from Companies House. Error: Enter your registered organisation name"
            }
            val labelForUtr = organisationType match {
              case "partnership" => "Partnership Self Assessment Unique Taxpayer Reference (UTR) number"
              case "limited-liability-partnership" =>
                "Partnership Self Assessment Unique Taxpayer Reference (UTR) number"
              case "charity-public-body-not-for-profit" =>
                "Organisation Self Assessment Unique Taxpayer Reference (UTR) number"
              case _ => "Corporation Tax Unique Taxpayer Reference (UTR) number"
            }

            val UtrHintText = organisationType match {
              case "partnership" | "limited-liability-partnership" =>
                " This is 10 numbers, for example 1234567890. It will be on partnership tax returns and other letters about your partnership. It may be called 'reference', 'UTR' or 'official use'. You can find a lost UTR number (opens in a new window or tab)."
              case _ =>
                " This is 10 numbers, for example 1234567890. It will be on tax returns and other letters about Corporation Tax. It may be called 'reference', 'UTR' or 'official use'. You can find a lost UTR number (opens in a new window or tab)."
            }

            val UtrHintTextLink = "https://www.gov.uk/find-lost-utr-number"

            page.getElementsText(labelForNameXpath) shouldBe labelForName
            page.getElementsText(labelForUtrXpath) shouldBe labelForUtr + UtrHintText
            page.getElementsHref(linkInUtrHintTextXpath) shouldBe UtrHintTextLink
          }
        }
      }
    }

    "ensure a valid Organisation Type has been passed" in {
      val invalidOrganisationType = UUID.randomUUID.toString
      val thrown = intercept[IllegalArgumentException] {
        showForm(invalidOrganisationType) { result =>
          await(result)
        }
      }
      thrown.getMessage should endWith(invalidOrganisationTypeMessage(invalidOrganisationType))
    }

    "show the option for registering with name and address instead of UTR when organisation type is Charity, public body or not for profit organisation" in {
      showForm(organisationType = "charity-public-body-not-for-profit") { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(registerWithNameAndAddressLink) should startWith(
          "If you are not registered for Self Assessment"
        )
        page.getElementAttributeHref(registerWithNameAndAddressLinkAnchor) shouldBe "/customs/register-for-cds/matching/address/charity-public-body-not-for-profit"
      }
    }

    "hide the option for registering with name and address instead of UTR when organisation type is not Charity, public body or not for profit organisation" in {
      showForm(organisationType = "limited-liability-partnership") { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(registerWithNameAndAddressLink) shouldBe empty
      }
    }
  }

  "Submitting the form for Organisation Types that have a UTR" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(defaultOrganisationType, Journey.GetYourEORI)
    )

    "ensure a valid Organisation Type has been passed" in {
      val invalidOrganisationType = UUID.randomUUID.toString
      val thrown = intercept[IllegalArgumentException] {
        showForm(invalidOrganisationType) { result =>
          await(result)
        }
      }
      thrown.getMessage should endWith(invalidOrganisationTypeMessage(invalidOrganisationType))
    }

    forAll(organisationTypeOrganisations) { (organisationType, organisation, nameDescription, utrDescription) =>
      {
        s"ensure name has been entered when organisation type is $organisationType" in {
          submitForm(form = ValidNameUtrRequest + ("name" -> ""), organisationType) { result =>
            status(result) shouldBe BAD_REQUEST
            val page = CdsPage(bodyOf(result))
            if (organisationType == "partnership" || organisationType == "limited-liability-partnership") {
              page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your registered partnership name"
              page.getElementsText(fieldLevelErrorName) shouldBe "Enter your registered partnership name"
            } else {
              page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your registered organisation name"
              page.getElementsText(fieldLevelErrorName) shouldBe "Enter your registered organisation name"
            }
            page.getElementsText("title") should startWith("Error: ")
          }
        }

        s"ensure UTR has been entered when organisation type is $organisationType" in {
          submitForm(form = ValidNameUtrRequest + ("utr" -> ""), organisationType) { result =>
            status(result) shouldBe BAD_REQUEST
            val page = CdsPage(bodyOf(result))
            page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your Unique Taxpayer Reference"
            page.getElementsText(fieldLevelErrorUtr) shouldBe "Enter your Unique Taxpayer Reference"
            page.getElementsText("title") should startWith("Error: ")
          }
        }

        s"ensure name does not exceed maximum length when organisation type is $organisationType" in {
          submitForm(form = ValidNameUtrRequest + ("name" -> oversizedString(NameMaxLength)), organisationType) {
            result =>
              status(result) shouldBe BAD_REQUEST
              val page = CdsPage(bodyOf(result))
              if (organisationType == "partnership" || organisationType == "limited-liability-partnership") {
                page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The partnership name must be 105 characters or less"
                page.getElementsText(fieldLevelErrorName) shouldBe "The partnership name must be 105 characters or less"
              } else {
                page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The organisation name must be 105 characters or less"
                page.getElementsText(fieldLevelErrorName) shouldBe "The organisation name must be 105 characters or less"
              }
              page.getElementsText("title") should startWith("Error: ")
          }
        }

        s"ensure when UTR is present it is of expected length when organisation type is $organisationType" in {
          submitForm(form = ValidNameUtrRequest + ("utr" -> "123456789"), organisationType) { result =>
            status(result) shouldBe BAD_REQUEST
            val page = CdsPage(bodyOf(result))
            page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe UtrWrongLengthError
            page.getElementsText(fieldLevelErrorUtr) shouldBe UtrWrongLengthError
            page.getElementsText("title") should startWith("Error: ")
          }
        }

        s"ensure when UTR is correctly formatted it is a valid UTR when organisation type is $organisationType" in {
          val invalidUtr = "0123456789"
          submitForm(form = ValidNameUtrRequest + ("utr" -> invalidUtr), organisationType) { result =>
            status(result) shouldBe BAD_REQUEST
            val page = CdsPage(bodyOf(result))
            page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe UtrInvalidError
            page.getElementsText(fieldLevelErrorUtr) shouldBe UtrInvalidError
            page.getElementsText("title") should startWith("Error: ")
          }
        }

        s"send a request to the business matching service when organisation type is $organisationType" in {
          when(
            mockMatchingService.matchBusiness(any[Utr], any[Organisation], any[Option[LocalDate]], any())(
              any[Request[AnyContent]],
              any[HeaderCarrier]
            )
          ).thenReturn(Future.successful(true))
          submitForm(ValidNameUtrRequest, organisationType) { result =>
            await(result)
            verify(mockMatchingService).matchBusiness(meq(ValidUtr), meq(organisation), meq(None), any())(
              any[Request[AnyContent]],
              any[HeaderCarrier]
            )
          }
        }
      }
    }

    "allow UTR of length 11 with suffix of K" in {
      when(
        mockMatchingService.matchBusiness(any[Utr], any[Organisation], any[Option[LocalDate]], any())(
          any[Request[AnyContent]],
          any[HeaderCarrier]
        )
      ).thenReturn(Future.successful(true))

      val utr = "1111111111K"
      submitForm(Map("name" -> "My company name", "utr" -> utr)) { result =>
        await(result)
        verify(mockMatchingService).matchBusiness(meq(Utr(utr)), any[Organisation], meq(None), any())(
          any[Request[AnyContent]],
          any[HeaderCarrier]
        )
      }
    }

    "allow UTR of length 11 with suffix of lowercase K" in {
      when(
        mockMatchingService.matchBusiness(any[Utr], any[Organisation], any[Option[LocalDate]], any())(
          any[Request[AnyContent]],
          any[HeaderCarrier]
        )
      ).thenReturn(Future.successful(true))

      val utr = "1111111111k"
      submitForm(Map("name" -> "My company name", "utr" -> utr)) { result =>
        await(result)
        verify(mockMatchingService).matchBusiness(meq(Utr(utr)), any[Organisation], meq(None), any())(
          any[Request[AnyContent]],
          any[HeaderCarrier]
        )
      }
    }

    "not allow UTR with length 11 and suffix k and K" in {
      when(
        mockMatchingService.matchBusiness(any[Utr], any[Organisation], any[Option[LocalDate]], any())(
          any[Request[AnyContent]],
          any[HeaderCarrier]
        )
      ).thenReturn(Future.successful(true))

      val utr = "516081700kK"
      submitForm(Map("name" -> "My company name", "utr" -> utr)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe UtrInvalidError
        page.getElementsText(fieldLevelErrorUtr) shouldBe UtrInvalidError
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "not allow UTR with length 12 and suffix K" in {
      when(
        mockMatchingService.matchBusiness(any[Utr], any[Organisation], any[Option[LocalDate]], any())(
          any[Request[AnyContent]],
          any[HeaderCarrier]
        )
      ).thenReturn(Future.successful(true))

      val utr = "51348170012K"
      submitForm(Map("name" -> "My company name", "utr" -> utr)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe UtrWrongLengthError
        page.getElementsText(fieldLevelErrorUtr) shouldBe UtrWrongLengthError
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "return a Bad Request when business match is unsuccessful" in {
      when(
        mockMatchingService.matchBusiness(meq(ValidUtr), meq(CompanyOrganisation), meq(None), any())(
          any[Request[AnyContent]],
          any[HeaderCarrier]
        )
      ).thenReturn(Future.successful(false))
      submitForm(ValidNameUtrRequest) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe BusinessNotMatchedError
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "redirect to the confirm page when match is successful" in {
      when(
        mockMatchingService.matchBusiness(meq(ValidUtr), meq(CompanyOrganisation), meq(None), any())(
          any[Request[AnyContent]],
          any[HeaderCarrier]
        )
      ).thenReturn(Future.successful(true))
      submitForm(ValidNameUtrRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs/register-for-cds/matching/confirm")
      }
    }
  }

  def showForm(organisationType: String = defaultOrganisationType, userId: String = defaultUserId)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)

    test(controller.form(organisationType, Journey.GetYourEORI).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  def submitForm(
    form: Map[String, String],
    organisationType: String = defaultOrganisationType,
    userId: String = defaultUserId
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    test(
      controller
        .submit(organisationType, Journey.GetYourEORI)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }
}
