package com.normation.plugins.usermanagement

import better.files.File
import bootstrap.liftweb.FileUserDetailListProvider
import bootstrap.liftweb.UserFile
import com.normation.errors.IOResult
import com.normation.rudder.rest.AuthorizationApiMapping
import com.normation.rudder.rest.RoleApiMapping
import com.normation.rudder.users.EventTrace
import com.normation.rudder.users.SessionId
import com.normation.rudder.users.UserAuthorisationLevel
import com.normation.rudder.users.UserInfo
import com.normation.rudder.users.UserRepository
import com.normation.rudder.users.UserSession
import com.normation.rudder.users.UserStatus
import java.nio.charset.StandardCharsets
import org.apache.commons.io.IOUtils
import org.joda.time.DateTime
import zio.ZIO
import zio.json.ast.Json
import zio.syntax.*

class MockServices(userInfos: List[UserInfo], userSessions: List[UserSession], usersFile: File) {

  object userRepo extends UserRepository {

    override def logStartSession(
        userId:            String,
        permissions:       List[String],
        authz:             List[String],
        tenants:           String,
        sessionId:         SessionId,
        authenticatorName: String,
        date:              DateTime
    ): IOResult[Unit] = ???

    override def logCloseSession(userId: String, date: DateTime, cause: String): IOResult[Unit] = ???

    override def closeAllOpenSession(endDate: DateTime, endCause: String): IOResult[Unit] = ???

    override def getLastPreviousLogin(userId: String, closedSessionsOnly: Boolean): IOResult[Option[UserSession]] = {
      userSessions.find(_.userId == userId).succeed
    }

    override def deleteOldSessions(olderThan: DateTime): IOResult[Unit] = ???

    override def setExistingUsers(origin: String, users: List[String], trace: EventTrace): IOResult[Unit] = ???

    override def disable(
        userId:            List[String],
        notLoggedSince:    Option[DateTime],
        excludeFromOrigin: List[String],
        trace:             EventTrace
    ): IOResult[List[String]] = {
      userId.succeed
    }

    override def delete(
        userId:            List[String],
        notLoggedSince:    Option[DateTime],
        excludeFromOrigin: List[String],
        trace:             EventTrace
    ): IOResult[List[String]] = {
      userId.succeed
    }

    override def purge(
        userId:            List[String],
        deletedSince:      Option[DateTime],
        excludeFromOrigin: List[String],
        trace:             EventTrace
    ): IOResult[List[String]] = ???

    override def setActive(userId: List[String], trace: EventTrace): IOResult[Unit] = {
      ZIO.unit
    }

    override def updateInfo(
        id:        String,
        name:      Option[Option[String]],
        email:     Option[Option[String]],
        otherInfo: Option[Json.Obj]
    ): IOResult[Unit] = {
      ZIO.unit
    }

    override def getAll(): IOResult[List[UserInfo]] = userInfos.succeed

    override def get(userId: String, isCaseSensitive: Boolean): IOResult[Option[UserInfo]] = {
      userInfos.find(_.id == userId).succeed
    }

    override def getStatuses(userIds: List[String]): IOResult[Map[String, UserStatus]] = ???
  }

  val usersInputStream = () => IOUtils.toInputStream(usersFile.contentAsString, StandardCharsets.UTF_8)

  val userService = {
    val usersFile = UserFile("test-users.xml", usersInputStream)

    val authLevel      = new UserAuthorisationLevel {
      override def userAuthEnabled: Boolean = true
      override def name:            String  = "Test user auth level"
    }
    val roleApiMapping = new RoleApiMapping(AuthorizationApiMapping.Core)

    val res = new FileUserDetailListProvider(roleApiMapping, authLevel, usersFile)
    res.reload()
    res
  }

  val userManagementService =
    new UserManagementService(userRepo, userService, UserFile(usersFile.pathAsString, usersInputStream).succeed)
}
