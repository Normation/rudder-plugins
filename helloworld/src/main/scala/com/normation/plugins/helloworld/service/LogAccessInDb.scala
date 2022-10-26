package com.normation.plugins.helloworld.service

import net.liftweb.common.Loggable
import org.joda.time.DateTime

/**
 *
 * That service show how the plugin can write information in
 * a DataBase.
 *
 * That service is just a simple CRUD facade to the DB.
 *
 * DB management/query are done thanks to Squeryl.
 *
 * We can see that the service has a "one time init" method that
 * is used to create DataBase tables. It has to handle itself the
 * test that check if the init was already done.
 */
case class AccessLog(id: Int, user: String, date: DateTime)

class LogAccessInDb(dbUrl: String, dbUser: String, dbPass: String) extends Loggable {

  var base = List[AccessLog]()

  def init(): Unit = {

    logger.info("Droping and creting table in database with given schema:")
    base = Nil
  }

  def logAccess(user: String): Unit = {
    base = AccessLog(base.size, user, DateTime.now) :: base
  }

  def getLog(fromTime: DateTime, toTime: DateTime): Seq[AccessLog] = {
    base.filter(x => x.date.isAfter(fromTime) && x.date.isBefore(toTime))
  }

}
