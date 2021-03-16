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

package unit.domain

import play.api.libs.json.Json
import uk.gov.hmrc.customs.rosmfrontend.domain.GroupIds
import util.UnitSpec

class EnrolmentsProxyGroupIdSpec extends UnitSpec {
  val responseWithGroups = """{
                                          |    "principalGroupIds": [
                                          |       "ABCEDEFGI1234567",
                                          |       "ABCEDEFGI1234568"
                                          |    ],
                                          |    "delegatedGroupIds": [
                                          |       "ABCEDEFGI1234567",
                                          |       "ABCEDEFGI1234568"
                                          |    ]
                                          |}""".stripMargin

  "GroupIds Response  Json" should {
    "transform correctly to valid GroupIds object" in {
      val groupIds = Json.parse(responseWithGroups).as[GroupIds]
      groupIds.principalGroupIds shouldBe Seq("ABCEDEFGI1234567", "ABCEDEFGI1234568")
    }
  }
}
