/*
*************************************************************************************
* Copyright 2020 Normation SAS
*************************************************************************************
*
* This file is part of Rudder.
*
* Rudder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU General Public License version 3, the copyright holders add
* the following Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
* Public License version 3, when you create a Related Module, this
* Related Module is not considered as a part of the work and may be
* distributed under the license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* Rudder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

*
*************************************************************************************
*/

package com.normation.plugins.openscappolicies.repository

import cats.implicits._
import com.normation.cfclerk.domain.TechniqueName
import com.normation.errors.IOResult
import com.normation.ldap.sdk.{LDAPConnectionProvider, RoLDAPConnection}
import com.normation.rudder.domain.RudderDit
import com.normation.rudder.domain.RudderLDAPConstants._
import com.normation.rudder.domain.policies.ActiveTechnique
import com.normation.rudder.repository.ldap.{LDAPEntityMapper, ScalaReadWriteLock}
import com.normation.ldap.sdk.BuildFilter._

class DirectiveRepository(
     val rudderDit                     : RudderDit
   , val ldap                          : LDAPConnectionProvider[RoLDAPConnection]
   , val mapper                        : LDAPEntityMapper
   , val userLibMutex                  : ScalaReadWriteLock //that's a scala-level mutex to have some kind of consistency with LDAP
) {
  /**
   * Get ActiveTechnique based on a specific technique name
   */
  def getActiveTechniqueByTechniqueName(techniqueName: TechniqueName) : IOResult[Seq[ActiveTechnique]] = {
    userLibMutex.readLock(for {
      con                    <- ldap
      filter                 = AND(IS(OC_ACTIVE_TECHNIQUE), EQ(A_TECHNIQUE_UUID, techniqueName.value))
      allEntries             <- con.searchSub(rudderDit.ACTIVE_TECHNIQUES_LIB.dn, filter)
      activeTechniques       <- allEntries.toList.traverse(entry => mapper.entry2ActiveTechnique(entry).chainError(s"Error when transforming LDAP entry into an Active Technique. Entry: ${entry}")).toIO
    } yield {
      activeTechniques
    })
  }
}
