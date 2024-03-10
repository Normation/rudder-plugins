package com.normation.plugins.changevalidation

import bootstrap.liftweb.FileUserDetailListProvider
import bootstrap.liftweb.ValidatedUserList
import com.normation.errors.*
import com.normation.eventlog.EventActor
import com.normation.rudder.db.Doobie
import doobie.*
import doobie.implicits.*
import net.liftweb.common.Loggable
import zio.ZIO
import zio.interop.catz.*
import zio.syntax.*

trait RoValidatedUserJdbcRepositorySQL {

  def getSQL(actor: EventActor): Query0[EventActor] = {
    sql"""SELECT username FROM change_validation_validated_users where username = $actor""".query[EventActor]
  }

  def getValidatedUsersSQL: Query0[EventActor] = {
    sql"""SELECT username FROM change_validation_validated_users""".query[EventActor]
  }

}

trait WoValidatedUserJdbcRepositorySQL {

  def createUserSQL(newVU: EventActor): Update0 = {
    sql"""INSERT INTO change_validation_validated_users (username)
            VALUES (${newVU.name})""".update
  }

  def deleteUserSQL(actor: EventActor): Update0 = {
    sql"""DELETE FROM change_validation_validated_users
              WHERE username = (${actor.name})""".update
  }

}

object ValidatedUserJdbcRepositorySQL extends RoValidatedUserJdbcRepositorySQL with WoValidatedUserJdbcRepositorySQL {}

class RoValidatedUserJdbcRepository(
    doobie:           Doobie,
    userListProvider: FileUserDetailListProvider
) extends RoValidatedUserRepository with RoValidatedUserJdbcRepositorySQL with Loggable {

  import doobie.*

  override def getValidatedUsers(): IOResult[Seq[EventActor]] = {
    transactIOResult("Could not get users from the change validation users table")(xa =>
      getValidatedUsersSQL.to[Vector].transact(xa)
    )
  }

  override def getUsers(): IOResult[Set[WorkflowUsers]] = {
    val userDetails: ValidatedUserList = userListProvider.authConfig
    val usersInFile = userDetails.users.keySet

    getValidatedUsers()
      .map(response => {
        val validatedUsersNotInFile = response.map(_.name).toSet
        val inFileAndValidated      = usersInFile.intersect(validatedUsersNotInFile)
        val notInFileAndValidated   = validatedUsersNotInFile.diff(usersInFile)
        val inFileAndNotValidated   = usersInFile.diff(validatedUsersNotInFile)

        val all = {
          val ifv  =
            inFileAndValidated.map(username => WorkflowUsers(EventActor(username), isValidated = true, userExists = true))
          val nfv  =
            notInFileAndValidated.map(username => WorkflowUsers(EventActor(username), isValidated = true, userExists = false))
          val ifnv =
            inFileAndNotValidated.map(username => WorkflowUsers(EventActor(username), isValidated = false, userExists = true))
          ifv ++ nfv ++ ifnv
        }
        all
      })
      .chainError(s"Error when retrieving validated users to get workflow's users")
  }

  override def get(actor: EventActor): IOResult[Option[EventActor]] = {
    transactIOResult(s"Could not get user '${actor.name}' from the change validation users table")(xa =>
      getSQL(actor).option.transact(xa)
    )
  }
}

class WoValidatedUserJdbcRepository(
    doobie: Doobie,
    roRepo: RoValidatedUserRepository
) extends WoValidatedUserRepository with WoValidatedUserJdbcRepositorySQL with Loggable {

  import doobie.*

  override def createUser(newVU: EventActor): IOResult[EventActor] = {
    roRepo
      .get(EventActor(newVU.name))
      .flatMap {
        case Some(EventActor(name)) => newVU.succeed // already validated
        case None                   =>
          transactIOResult(s"Creation of validated user ${newVU.name} have failed")(xa =>
            createUserSQL(newVU).run.transact(xa)
          ).flatMap {
            case 1 => newVU.succeed
            case n =>
              Unexpected(
                s"The creation of validated user ${newVU.name} have failed ($n table lines altered)"
              ).fail
          }

      }
      .chainError(s"Creation of validated user ${newVU.name} have failed")
  }

  override def deleteUser(actor: EventActor): IOResult[EventActor] = {
    roRepo.get(EventActor(actor.name)).flatMap {
      case Some(EventActor(name)) =>
        transactIOResult(s"Deletion of validated user $name have failed")(xa => deleteUserSQL(actor).run.transact(xa)).flatMap {
          case 1 => actor.succeed
          case n =>
            Unexpected(
              s"The validated user $name was found in data base but could not be deleted ($n table lines altered)"
            ).fail
        }
      case None                   => actor.succeed // do nothing
    }
  }

  override def saveWorkflowUsers(actors: List[EventActor]): IOResult[Set[WorkflowUsers]] = {
    val newUsers = actors.toSet
    roRepo
      .getValidatedUsers()
      .flatMap(users => {
        val actualUsers = users.toSet
        val toRemove    = actualUsers.diff(newUsers)
        val toAdd       = newUsers.diff(actualUsers)

        ZIO.foreach(toRemove)(deleteUser(_)) *>
        ZIO.foreach(toAdd)(createUser(_)) *>
        roRepo.getUsers()
      })
      .chainError("Error when trying to get all validated user to save workflow's users")
  }
}
