package com.normation.plugins.changevalidation

import com.normation.errors.IOResult
import com.normation.eventlog.EventActor

/**
  * Read access to validated user
  */
trait RoValidatedUserRepository {

  def getValidatedUsers(): IOResult[Seq[EventActor]]

  def get(actor: EventActor): IOResult[Option[EventActor]]

  /**
    * Read access to validated user
    */
  def getUsers(): IOResult[Set[WorkflowUsers]]

}

/**
  * Write access to validated user
  */
trait WoValidatedUserRepository {

  /**
    * Save a new validated user in the back-end.
    */
  def createUser(newVU: EventActor): IOResult[EventActor]

  /**
    * Add and remove validated users according to the new list
    */
  def saveWorkflowUsers(actor: List[EventActor]): IOResult[Set[WorkflowUsers]]

  /**
    * Delete a change request.
    * (whatever the read/write mode is).
    */
  def deleteUser(actor: EventActor): IOResult[EventActor]

}
