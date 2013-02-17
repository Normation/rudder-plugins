package com.normation.plugins.helloworld.service

import java.sql.Timestamp

import org.joda.time.DateTime
import org.squeryl.{ Schema, Session, SessionFactory }
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode.{ _ }
import org.squeryl.adapters.PostgreSqlAdapter
import org.squeryl.annotations.Column

import net.liftweb.common.Loggable

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
class LogAccessInDb(dbUrl:String, dbUser:String, dbPass:String) extends Loggable {

  import org.squeryl.SessionFactory

  Class.forName("org.postgresql.Driver");

  SessionFactory.concreteFactory = Some{ ()=>
    val s = Session.create(
      java.sql.DriverManager.getConnection(dbUrl, dbUser, dbPass),
      new PostgreSqlAdapter
    )
    s.setLogger(s => logger.info(s))
    s
  }


  def init() : Unit = {

    logger.info("Droping and creting table in database with given schema:")
    inTransaction {
      HelloWorldDb.printDdl( (s:String) => logger.info(s) )
      HelloWorldDb.drop
      HelloWorldDb.create
    }

  }

  def logAccess(user:String) : Unit = {
    transaction {
      HelloWorldDb.logs.insert(AccessLog(user, new Timestamp(System.currentTimeMillis)))
    }
  }

  def getLog(fromTime:DateTime, toTime: DateTime) : Seq[AccessLog] = {
    import HelloWorldDb._
    def toDate(d:DateTime) : Timestamp = new Timestamp(d.getMillis)
    val q = from(logs)(log =>
      where(log.date.gt(toDate(fromTime)) and log.date.lt(toDate(toTime)))
      select(log)
      orderBy(log.date.desc)
    )

    transaction {
      //force retrieval of the result - we don't want a lazy think here, as the session is not bound to the
      //HTTP request but only to that piece of code
      q.toList
    }
  }

}


//// here are some utility classes to use with the service ////

case class AccessLog(
    @Column("HELLO_PLUG_LOG_USER")
    user:String,
    @Column("HELLO_PLUG_LOG_Date")
    date:Timestamp
) extends KeyedEntity[Long] {
  @Column("HELLO_PLUG_LOG_ID")
  val id = 0L
}

object HelloWorldDb extends Schema {
  val logs = table[AccessLog]("helloPlugLog")
}