/*
*************************************************************************************
* Copyright 2016 Normation SAS
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

package com.normation.plugins.datasources

import com.normation.errors.IOResult
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.plugins.datasources.DataSourceSchedule._
import com.normation.rudder.domain.eventlog._
import com.normation.plugins.PluginStatus
import zio._
import zio.syntax._
import com.normation.zio._
import zio.clock.Clock
import zio.duration._

final case class UpdateCause(modId: ModificationId, actor:EventActor, reason:Option[String], triggeredByGeneration: Boolean = false)

/**
 * This object represent a statefull scheduler for fetching (or whatever action)
 * data from datasource.
 * Its contract is that:
 * - data source is immutable for that scheduler
 * - action is call periodically accordingly to data source period
 * - the scheduler is initially STOPPED. It can be start with the start() method.
 * - the scheduler can be stopped (when already stopped, it's a noop) with the cancel() method.
 * - there is callback that should be call each time a node is added / a generation is
 *   started - the data source configuration will decide if something has to be done or not.
 */
class DataSourceScheduler(
    val datasource  : DataSource
  ,     clock       : Clock
  ,     pluginStatus: PluginStatus
  ,     newUuid     : ()          => ModificationId
  ,     updateAll   : UpdateCause => IOResult[Unit]
) {

  /**
   * So, the idea is to build an observable that tick every period (if period defined)
   * We start it when the datasource is initialized, and stop it/start it around
   * each user-triggered event like "on new node".
   *
   * At each tick, we fetch data.
   */
  private[this] val semaphore = Semaphore.make(1).runNow

  //for that datasource, this is the timer
  private[this] val source : UIO[Unit] = {
    val never: Schedule[Any, Any, Nothing] = Schedule((_, _) => UIO.never)

    val schedule = datasource.runParam.schedule match {
      case Scheduled(d)  =>
        if(datasource.enabled) {
          DataSourceLoggerPure.Scheduler.info(s"Datasource '${datasource.name.value}' (${datasource.id.value}) is enabled and scheduled every ${d.asScala.toMinutes.toString} minutes") *>
          Schedule.spaced(d).succeed
        } else {
          DataSourceLoggerPure.Scheduler.info(s"Datasource '${datasource.name.value}' (${datasource.id.value}) is disabled") *>
          never.succeed
        }
      case NoSchedule(_) => //in that case, our source doesn't produce anything
        DataSourceLoggerPure.Scheduler.info(s"Datasource '${datasource.name.value}' (${datasource.id.value}) is enabled and but no schedule is configured") *>
        never.succeed
    }

    val msg = s"Automatically fetching data for data source '${datasource.name.value}' (${datasource.id.value}): ${schedule}"

    // The full action with loggin. We don't want it to be able to fail, because it would stop
    // futur update. So we catch all error and log them (in debug because they are (should) already log in error, we
    // only want to be sure to have them)
    val prog = (DataSourceLoggerPure.info(msg) *> DataSourceLoggerPure.trace(s"details: ${datasource}") *>
                 updateAll(UpdateCause(newUuid(), RudderEventActor, Some(msg)))
               ).catchAll(err => DataSourceLoggerPure.debug(err.fullMsg))

    for {
      s <- schedule
      p <- prog.repeat(s).provide(clock).unit
    } yield {
      p
    }
  }

  // here is the place where we will store the currently
  // running task, so that we are able the stop it and restart
  // it on user action.
  private[this] val scheduledTask : Ref[Option[Fiber[_,_]]] = Ref.make(Option.empty[Fiber[_, _]]).runNow


  /*
   * start scheduling after given delay
   * (so that the first action is actually done after that delay)
   */
  def startWithDelay(delay: Duration): IOResult[Unit] = {
    // don't forget to fork is you don't want to block for "delay"!
    restartScheduleTask().delay(delay).provide(clock).forkDaemon.unit
  }

  /*
   * This is the main interesting method, seting
   * things up for schedule
   */
  def restartScheduleTask(): IOResult[Unit] = {
    // clean existing
    cancel() *> (
    // actually start the scheduler by subscribing to it
    if(datasource.enabled) {
      if(pluginStatus.isEnabled()) {
        for {
          _     <- DataSourceLoggerPure.debug(s"Scheduling runs for data source with id '${datasource.id.value}'")
          fiber <- source.forkDaemon
          _     <- scheduledTask.set(Some(fiber))
        } yield ()
      } else {
        // the plugin is disabled, does nothing
        DataSourceLoggerPure.warn(s"The datasource with id '${datasource.id.value}' is enabled but the plugin is disabled (reason: ${pluginStatus.current}). Not scheduling future runs for it.")
      }
    } else {
      DataSourceLoggerPure.trace(s"The datasource with id '${datasource.id.value}' is disabled. Not scheduling future runs for it.")
    })
  }

  // the cancel method just stop the current time if
  // exists, and clean things up
  def cancel() : IOResult[Unit] = { semaphore.withPermit {
    for {
      _   <- DataSourceLoggerPure.trace(s"Removing (if needed) any future scheduled tasks for data source '${datasource.name.value}' (${datasource.id.value})")
      opt <- scheduledTask.get
      _   <- opt match {
               case None        => None.succeed
               case Some(fiber) => fiber.interrupt *> None.succeed
             }
      _   <- scheduledTask.set(None)
    } yield ()
  } }

  /**
   * This is the method that actually do a fetch data and manage
   * the scheduler restart.
   * We must avoid exceptions.
   */
  def doActionAndSchedule(action: IOResult[Unit]): IOResult[Unit] = {
    cancel() *> action *> (datasource.runParam.schedule match {
        case Scheduled(p)  => startWithDelay(p)
        case NoSchedule(_) => UIO.unit//nothing
      })
  }
}

