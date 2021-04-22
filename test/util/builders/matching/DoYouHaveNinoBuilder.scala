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

package util.builders.matching

import uk.gov.hmrc.customs.rosmfrontend.domain.Nino

object DoYouHaveNinoBuilder {

  val validNino = Nino(NinoFormBuilder.Nino)

  val yesNinoSubmitData = Map("have-nino" -> "true", "nino" -> NinoFormBuilder.Nino)

  val yesNoNinoEmptyData: Map[String, String] = Map[String, String]()

  val yesNinoNotProvidedSubmitData = Map("have-nino" -> "true", "nino" -> "")

  val yesNinoWrongFormatSubmitData = Map("have-nino" -> "true", "nino" -> "ABZ")

  val noNinoSubmitData = Map("have-nino" -> "false")
}
