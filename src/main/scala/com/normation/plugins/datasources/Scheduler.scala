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

import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.plugins.datasources.DataSourceSchedule._
import com.normation.rudder.domain.eventlog._
import monix.eval.Task
import monix.execution.Cancelable
import monix.execution.Scheduler
import monix.reactive.Observable
import net.liftweb.common.Loggable
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal


final case class UpdateCause(modId: ModificationId, actor:EventActor, reason:Option[String])

/**
 * This object represent a statefull scheduler for fetching (or whatever action)
 * data from datasource.
 * Its contract is that:
 * - data source is immutable for that scheduler
 * - action is call periodically accordingly to data source period
 * - the scheduler is initially STOPPED. It can be start with the start() method.
 * - the scheduler can be stopped (when already stopped, it's a noop) with the cancel() method.
 * - there is callback that should be call each time a node is added / a generation is
 *   started - the data source configuration will decide is something is to done or not.
 */
class DataSourceScheduler(
             val datasource: DataSource
  , implicit val scheduler : Scheduler
  ,              newUuid   : ()          => ModificationId
  ,              updateAll : UpdateCause => Unit
) extends Loggable {

  /**
   * So, the idea is to build an observable that tick every period (if period defined)
   * We start it when the datasource is initialized, and stop it/start it around
   * each user-triggered event like "on new node".
   *
   * At each tick, we fetch data.
   */

  //for that datasource, this is the timer
  private[this] val source = (datasource.runParam.schedule match {
      case Scheduled(d)  =>
        if(datasource.enabled) {
          Observable.interval(d)
        } else {
          Observable.empty[Long]
        }
      case NoSchedule(_) => //in that case, our source does produce anything
        Observable.empty[Long]
    }
  //and now, map the actual behavior to produce at each tick
  ).mapAsync { tick =>
    Task{
      val msg = s"Automatically fetching data for data source '${datasource.name.value}' (${datasource.id.value})"
      DataSourceLogger.info(msg)
      DataSourceLogger.trace(s"details: ${datasource}")
      updateAll(UpdateCause(newUuid(), RudderEventActor, Some(msg)))
    }
  }
  // we add an auto restart in case a getData lead to an error
  .onErrorRestart(5)

  // here is the place where we will store the currently
  // running time, so that we are able the stop it and restart
  // it on user action.
  private[this] var scheduledTask = Option.empty[Cancelable]


  /*
   * start scheduling after given delay
   * (so that the first action is actually done after that delay)
   */
  def startWithDelay(delay: FiniteDuration): Unit = {
    Task(restartScheduleTask()).delayExecution(delay).runAsync
  }

  /*
   * This is the main interesting method, seting
   * things up for schedule
   */
  def restartScheduleTask(): Unit = {
    // clean existing
    cancel()
    // actually start the scheduler by subscribing to it
    if(datasource.enabled) {
      DataSourceLogger.debug(s"Scheduling runs for data source with id '${datasource.id.value}'")
      scheduledTask = Some(source.subscribe())
    } else {
      DataSourceLogger.trace(s"The datasource with id '${datasource.id.value}' is disabled. Not scheduling future runs for it.")
    }
  }

  // the cancel method just stop the current time if
  // exists, and clean things up
  def cancel() : Unit = {
    scheduledTask.foreach{ dss =>
      DataSourceLogger.trace(s"Removing (if needed) any future scheduled tasks for data source '${datasource.name}' (${datasource.id.value})")
      dss.cancel()
    }
    scheduledTask = None
  }

  /**
   * This is the method that actually do a fetch data and manage
   * the scheduler restart.
   * We must avoid exceptions.
   */
  def doActionAndSchedule(action: => Unit): Unit = {
    cancel()
    try {
      Task(action).runAsync
    } catch {
      case NonFatal(ex) => logger.error(s"Error when fetching data", ex)
    } finally {
      datasource.runParam.schedule match {
        case Scheduled(p)  => startWithDelay(p)
        case NoSchedule(_) => //nothing
      }
    }
  }

}

