package com.normation.plugins.apiauthorizations

import com.normation.errors.IOResult
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.rudder.api.ApiAccount
import com.normation.rudder.api.ApiAccountId
import com.normation.rudder.api.ApiTokenHash
import com.normation.rudder.api.RoApiAccountRepository
import com.normation.rudder.api.TokenGenerator
import com.normation.rudder.api.WoApiAccountRepository
import zio.syntax.*

class MockServices(newToken: String, accounts: Map[ApiAccountId, ApiAccount] = Map.empty) { self =>

  object apiAccountRepository extends RoApiAccountRepository with WoApiAccountRepository {
    override def getById(id: ApiAccountId): IOResult[Option[ApiAccount]] = {
      accounts.get(id).succeed
    }

    override def save(principal: ApiAccount, modId: ModificationId, actor: EventActor): IOResult[ApiAccount] = {
      principal.succeed
    }

    override def delete(id: ApiAccountId, modId: ModificationId, actor: EventActor): IOResult[ApiAccountId] = {
      id.succeed
    }

    override def getAllStandardAccounts: IOResult[Seq[ApiAccount]] = ???
    override def getByToken(token: ApiTokenHash): IOResult[Option[ApiAccount]] = ???
    override def getSystemAccount: ApiAccount = ???
  }

  object tokenGenerator extends TokenGenerator {
    override def newToken(size: Int): String = self.newToken
  }
}
