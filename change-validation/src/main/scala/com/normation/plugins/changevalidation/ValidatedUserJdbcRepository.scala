package com.normation.plugins.changevalidation

import bootstrap.liftweb.RudderConfig
import bootstrap.liftweb.UserDetailList
import com.normation.eventlog.EventActor
import com.normation.rudder.db.Doobie
import com.normation.rudder.db.Doobie._
import net.liftweb.common.Box
import net.liftweb.common.EmptyBox
import net.liftweb.common.Failure
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import doobie._
import doobie.implicits._

/**
  * userExists indicates if a Validated User is present in user file description
  */
case class WorkflowUsers(actor: EventActor, isValidated: Boolean, userExists: Boolean)

class RoValidatedUserJdbcRepository(
    doobie: Doobie
  , mapper: ValidatedUserMapper
) extends RoValidatedUserRepository with Loggable {

  import doobie._
  import mapper.ValidatedUserMeta

  val SELECT_SQL = "SELECT username FROM change_validation_validated_users"

  override def getValidatedUsers(): Box[Seq[EventActor]] = {
    val q = query[EventActor](SELECT_SQL)
    transactRunBox(xa => q.to[Vector].transact(xa))
  }

  override def getUsers(): Box[Set[WorkflowUsers]] = {
    val userDetails: UserDetailList = RudderConfig.rudderUserListProvider.authConfig
    val usersInFile = userDetails.users.keySet

    getValidatedUsers() match {
      case Full(response) =>
        val validatedUsersNotInFile = response.map{_.name}.toSet
        val inFileAndValidated = usersInFile.intersect(validatedUsersNotInFile)
        val notInFileAndValidated = validatedUsersNotInFile.diff(usersInFile)
        val inFileAndNotValidated = usersInFile.diff(validatedUsersNotInFile)

        val all = {
          val ifv = inFileAndValidated.map(username =>
            WorkflowUsers(EventActor(username), isValidated = true, userExists = true)
          )
          val nfv = notInFileAndValidated.map(username =>
            WorkflowUsers(EventActor(username), isValidated = true, userExists = false)
          )
          val ifnv = inFileAndNotValidated.map(username =>
            WorkflowUsers(EventActor(username), isValidated = false, userExists = true)
          )
          ifv ++ nfv ++ ifnv
        }

        Full(all)
      case eb: EmptyBox =>
        val fail = eb ?~! s"Error when retrieving validated users to get workflow's users"
        fail
    }
  }

  override def get(actor: EventActor): Box[Option[EventActor]] = {
    val q = Query[String, EventActor](SELECT_SQL + " where username = ?", None).toQuery0(actor.name)
    transactRunBox(xa => q.option.transact(xa))
  }
}

class WoValidatedUserJdbcRepository(
     doobie: Doobie
   , mapper: ValidatedUserMapper
   , roRepo: RoValidatedUserRepository
) extends WoValidatedUserRepository with Loggable {

  import doobie._

  override def createUser(newVU: EventActor): Box[EventActor] = {
    roRepo.get(EventActor(newVU.name)) match {
      case Full(response) =>
        response match {
          // already validated
          case Some(_) =>  Full(newVU)
          case None =>
            val q = sql"""INSERT INTO change_validation_validated_users (username)
            VALUES (${newVU.name})""".update
            val linesAffected: Either[Throwable, Int] = transactRun(xa => q.run.transact(xa).attempt)
            linesAffected match {
              case Right(1)          => Full(newVU)
              case Right(0)          =>
                roRepo.get(EventActor(newVU.name)) match {
                  case Full(ea)     => ea match {
                    case Some(_) => Full(newVU)
                    case None    => Failure(s"The validated user saved with username ${newVU.name} was not found back in data base")
                  }
                  case eb: EmptyBox =>
                    val fail = eb ?~! s"Creation of validated user ${newVU.name} have failed"
                    fail
                }
              case Right(x) if x > 1 => Failure(s"The creation of validated user ${newVU.name} have alter $x table lines")
              case Left(e)           => throw e
            }
        }
      case eb: EmptyBox =>
        val fail = eb ?~! s"Creation of validated user ${newVU.name} have failed"
        fail
    }
  }

  override def deleteUser(actor: EventActor): Box[EventActor] = {
    roRepo.get(EventActor(actor.name)) match {
      case Full(response) => response match {
        case Some(_) =>
          val q = sql"""DELETE FROM change_validation_validated_users
              WHERE username = (${actor.name})""".update
          val linesAffected: Either[Throwable, Int] = transactRun(xa => q.run.transact(xa).attempt)
          linesAffected match {
            case Right(1) => Full(actor)
            case Right(0) =>
              roRepo.get(EventActor(actor.name)) match {
                case Full(ea) => ea match {
                  case Some(a) => Failure(s"${a.name} has been found back in data base")
                  case None    => Full(actor)
                }
                case eb: EmptyBox =>
                  val fail = eb ?~! s"Deletion of validated user ${actor.name} have failed"
                  fail
              }
            case Right(x) if x > 1 => Failure(s"Deletion of validated user ${actor.name} have alter $x table lines")
            case Left(e)           => throw e
          }
        case None => Full(actor)
      }
      case _: EmptyBox => Full(actor)
    }
  }

  override def saveWorkflowUsers(actors: List[EventActor]): Box[Set[WorkflowUsers]] = {
    val newUsers = actors.toSet
    roRepo.getValidatedUsers() match {
      case Full(users) =>
        val actualUsers = users.toSet
        val toRemove = actualUsers.diff(newUsers)
        val toAdd = newUsers.diff(actualUsers)

        toRemove.foreach(deleteUser)
        toAdd.foreach(createUser)
//        Full(Map("newValidatedUsers" -> toAdd, "removedValidatedUser" -> toRemove))
        roRepo.getUsers()
      case eb: EmptyBox =>
        val fail = eb ?~! s"Error when trying to get all validated user to save workflow's users"
        fail
    }
  }
}

class ValidatedUserMapper() extends Loggable {
  implicit val ValidatedUserMeta: Meta[EventActor] =
    Meta[String].timap(ea => EventActor(ea))(u => u.name)
}

