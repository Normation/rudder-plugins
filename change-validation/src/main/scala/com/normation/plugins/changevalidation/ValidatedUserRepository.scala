package com.normation.plugins.changevalidation

import com.normation.eventlog.EventActor
import net.liftweb.common.Box

/**
  * Read access to validated user
  */
trait RoValidatedUserRepository {

  def getValidatedUsers(): Box[Seq[EventActor]]

  def get(actor: EventActor): Box[Option[EventActor]]

  /**
    * Read access to validated user
    */
  def getUsers(): Box[Set[WorkflowUsers]]

}

/**
  * Write access to validated user
  */
trait WoValidatedUserRepository {

  /**
    * Save a new validated user in the back-end.
    */
  def createUser(newVU: EventActor): Box[EventActor]

  /**
    * Add and remove validated users according to the new list
    */
  def saveWorkflowUsers(actor: List[EventActor]): Box[Set[WorkflowUsers]]

  /**
    * Delete a change request.
    * (whatever the read/write mode is).
    */
  def deleteUser(actor: EventActor): Box[EventActor]

}
