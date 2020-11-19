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
import com.normation.inventory.domain.NodeId
import com.normation.plugins.datasources.DataSourceSchedule._
import com.normation.rudder.db.Doobie
import com.normation.rudder.db.Doobie._
import com.normation.rudder.domain.eventlog._
import com.normation.rudder.domain.nodes.NodeInfo
import com.normation.utils.StringUuidGenerator
import doobie._
import doobie.implicits._
import org.joda.time.DateTime
import zio.duration._
import com.normation.plugins.PluginStatus
import com.normation.errors._
import com.normation.zio._
import zio._
import zio.clock.Clock
import zio.syntax._
import com.normation.rudder.domain.parameters.GlobalParameter
import zio.console.Console
import zio.interop.catz._


final case class PartialNodeUpdate(
    nodes        : Map[NodeId, NodeInfo] //the node to update
  , policyServers: Map[NodeId, NodeInfo] //there policy servers
  , parameters   : Set[GlobalParameter]
)

trait DataSourceRepository {

  /*
   * Retrieve IDs. This is useful to know what are the reserved
   * node properties names.
   * We only need the id because for now, the semantic is that
   * as soon as the datasource is defined, even if disabled,
   * the property can not be interactively managed.
   */
  def getAllIds: IOResult[Set[DataSourceId]]

  def getAll : IOResult[Map[DataSourceId,DataSource]]

  def get(id : DataSourceId) : IOResult[Option[DataSource]]

  def save(source : DataSource) : IOResult[DataSource]

  def delete(id : DataSourceId) : IOResult[DataSourceId]
}

/*
 * A trait that exposes interactive callbacks for
 * data sources, i.e the method to call when one
 * need to update datasources.
 */
trait DataSourceUpdateCallbacks {

  def onNewNode(node: NodeId): IOResult[Unit]
  def onGenerationStarted(generationTimeStamp: DateTime): IOResult[Unit]
  def onUserAskUpdateAllNodes(actor: EventActor): IOResult[Unit]
  def onUserAskUpdateAllNodesFor(actor: EventActor, datasourceId: DataSourceId): IOResult[Unit]
  def onUserAskUpdateNode(actor: EventActor, nodeId: NodeId): IOResult[Unit]
  def onUserAskUpdateNodeFor(actor: EventActor, nodeId: NodeId, datasourceId: DataSourceId): IOResult[Unit]

  /*
   * Initialise all datasource so that they are ready to schedule their
   * first data fetch or wait for other callbacks.
   *
   * Non periodic data source won't be updated with that call.
   * Periodic one will be updated in a random interval between
   * 1 minute and min(period / 2, 30 minute) to avoid to extenghish
   * all resources on them.
   */
  def startAll(): IOResult[Unit]

  /*
   * Define all datasource scheduler from data sources in backend
   */
  def initialize(): IOResult[Unit]
}

trait NoopDataSourceCallbacks extends DataSourceUpdateCallbacks {
  def onNewNode(node: NodeId): IOResult[Unit] = UIO.unit
  def onGenerationStarted(generationTimeStamp: DateTime): IOResult[Unit] = UIO.unit
  def onUserAskUpdateAllNodes(actor: EventActor): IOResult[Unit] = UIO.unit
  def onUserAskUpdateAllNodesFor(actor: EventActor, datasourceId: DataSourceId): IOResult[Unit] = UIO.unit
  def onUserAskUpdateNode(actor: EventActor, nodeId: NodeId): IOResult[Unit] = UIO.unit
  def onUserAskUpdateNodeFor(actor: EventActor, nodeId: NodeId, datasourceId: DataSourceId): IOResult[Unit] = UIO.unit
  def startAll(): IOResult[Unit] = UIO.unit
  def initialize(): IOResult[Unit] = UIO.unit
}

class MemoryDataSourceRepository extends DataSourceRepository {
  def print(s: String) = ZIO.accessM[Console](_.get.putStrLn(s)).provide(ZioRuntime.environment)

  private[this] val sourcesRef = zio.Ref.make(Map[DataSourceId, DataSource]()).runNow

  def getAllIds = sourcesRef.get.map(_.keySet)

  def getAll = sourcesRef.get

  def get(id : DataSourceId) : IOResult[Option[DataSource]]= sourcesRef.get.map(_.get(id))

  def save(source : DataSource) = sourcesRef.update { sources =>
    sources +  ((source.id,source))
  }*> source.succeed

  def delete(id : DataSourceId) : IOResult[DataSourceId] = sourcesRef.update { sources =>
     sources - (id)
  } *> id.succeed
}

/**
 * This is the higher level repository facade that is managine the "live"
 * instance of datasources, with the scheduling initialisation and update
 * on different repository action.
 *
 * It doesn't deal at all with the serialisation / deserialisation of data source
 * in data base.
 */
class DataSourceRepoImpl(
    backend     : DataSourceRepository
  , clock       : Clock
  , fetch       : QueryDataSourceService
  , uuidGen     : StringUuidGenerator
  , pluginStatus: PluginStatus
) extends DataSourceRepository with DataSourceUpdateCallbacks {

  val dataSourcesLock = Semaphore.make(1).runNow

  /*
   * Be careful, ALL modification to datasource must be synchronized
   */
  private[this] object datasources extends AnyRef {
    private[this] val semaphore = Semaphore.make(1).runNow
    private[this] var internalRef = Ref.make(Map[DataSourceId, DataSourceScheduler]()).runNow

    // utility methods on datasources
    // stop a datasource - must be called when the datasource still in "datasources"
    private[this] def stop(id: DataSourceId) = {
      DataSourceLoggerPure.debug(s"Stopping data source with id '${id.value}'") *>
      internalRef.get.map(_.get(id) match {
        case None      => DataSourceLogger.trace(s"Data source with id ${id.value} was not found running")
        case Some(dss) => dss.cancel()
      })
    }

    def save(dss: DataSourceScheduler) = semaphore.withPermit {
      stop(dss.datasource.id) *>
      internalRef.update(_ + (dss.datasource.id -> dss))
    }
    def delete(id : DataSourceId) = semaphore.withPermit {
      stop(id) *>
      internalRef.update(_ - id)
    }
    //get alls - return an immutable map
    def all(): IOResult[Map[DataSourceId, DataSourceScheduler]] = semaphore.withPermit { internalRef.get }
  }

  // Initialize data sources scheduler, with all sources present in backend
  def initialize(): IOResult[Unit] = {
    getAll.flatMap(sources =>
        ZIO.foreach(sources.toList) { case (_, source) =>
          updateDataSourceScheduler(clock, source, Some(source.runParam.schedule.duration))
        }.unit
    ).chainError("Error when initializing datasources")
  }

  // get datasource scheduler which match the condition
  private[this] def foreachDatasourceScheduler(condition: DataSource => Boolean)(action: DataSourceScheduler => IOResult[Unit]): IOResult[Unit] = {
    datasources.all().flatMap(m => ZIO.foreach(m.toList) { case (_, dss) =>
      if (condition(dss.datasource)) {
        action(dss)
      } else {
        DataSourceLoggerPure.debug(s"Skipping data source '${dss.datasource.name}' (${dss.datasource.id.value}): disabled or trigger not configured")
      }
    }).unit
  }

  private[this] def updateDataSourceScheduler(clock: Clock, source: DataSource, delay: Option[Duration]): IOResult[Unit] = {
    // create live instance
    val dss = new DataSourceScheduler(
          source
        , clock
        , pluginStatus
        , () => ModificationId(uuidGen.newUuid)
        , (cause: UpdateCause) => fetch.queryAll(source, cause).unit
    )
    datasources.save(dss) *> (
      //start new
      delay match {
        case None    =>
          dss.restartScheduleTask()
        case Some(d) =>
          dss.startWithDelay(d)
      }
    )
  }

  ///
  ///         DB READ ONLY
  /// read only method are just forwarder to backend
  ///
  override def getAllIds : IOResult[Set[DataSourceId]] = backend.getAllIds

  override def getAll : IOResult[Map[DataSourceId,DataSource]] = {
    ZIO.when(DataSourceLoggerPure.logEffect.isDebugEnabled()) {
      for {
        all <- datasources.all()
        _   <- DataSourceLoggerPure.debug(s"Live data sources: ${all.map {case(_, dss) =>
                 s"'${dss.datasource.name.value}' (${dss.datasource.id.value}): ${if(dss.datasource.enabled) "enabled" else "disabled"}"
               }.mkString("; ")}")
      } yield ()
    } *> backend.getAll
  }

  override def get(id : DataSourceId) : IOResult[Option[DataSource]] = backend.get(id)

  ///
  ///         DB WRITE ONLY
  /// write methods need to manage the "live" scheduler
  /// write methods need to be synchronised to keep consistancy between
  /// the backend and the live data (the self-consistancy of live data
  /// is ensured in the datasources object).
  /// All the lock are on datasources object.

  /*
   * on update, we need to stop the corresponding optionnaly existing
   * scheduler, and update with the new one.
   */
  override def save(source : DataSource) : IOResult[DataSource] = dataSourcesLock.withPermit {
    //only create/update the "live" instance if the backend succeed
    for {
      _ <- backend.save(source).chainError(s"Error when saving data source '${source.name.value}' (${source.id.value})").foldM(
               err => DataSourceLoggerPure.error(err.fullMsg)
             , ok  => ok.succeed
           )
      _ <- updateDataSourceScheduler(clock, source, delay = None)
      _ <- DataSourceLoggerPure.debug(s"Data source '${source.name.value}' (${source.id.value}) udpated")
    } yield {
      source
    }
  }

  /*
   * delete need to clean existing live resource
   */
  override def delete(id : DataSourceId) : IOResult[DataSourceId] = dataSourcesLock.withPermit {
    //start by cleaning
    datasources.delete(id) *>
    backend.delete(id)
  }

  ///
  ///        CALLBACKS
  ///

  // no need to synchronize callback, they only
  // need a reference to the immutable datasources map.

  override def onNewNode(nodeId: NodeId): IOResult[Unit] = {
    DataSourceLoggerPure.info(s"Fetching data from data source for new node '${nodeId}'") *>
    foreachDatasourceScheduler(ds => ds.enabled && ds.runParam.onNewNode){ dss =>
      val msg = s"Fetching data for data source ${dss.datasource.name.value} (${dss.datasource.id.value}) for new node '${nodeId.value}'"
      DataSourceLoggerPure.debug(msg) *>
      //no scheduler reset for new node
      fetch.queryOne(dss.datasource, nodeId, UpdateCause(
          ModificationId(uuidGen.newUuid)
        , RudderEventActor
        , Some(msg)
      )).unit //error is logged in query
    }
  }

  override def onGenerationStarted(generationTimeStamp: DateTime): IOResult[Unit] = {
    DataSourceLoggerPure.info(s"Fetching data from data source for all node for generation ${generationTimeStamp.toString()}")
    foreachDatasourceScheduler(ds => ds.enabled && ds.runParam.onGeneration){ dss =>
      //for that one, do a scheduler restart
      val msg = s"Getting data for source ${dss.datasource.name.value} for policy generation started at ${generationTimeStamp.toString()}"
      DataSourceLoggerPure.debug(msg) *>
      dss.doActionAndSchedule(fetch.queryAll(dss.datasource, UpdateCause(ModificationId(uuidGen.newUuid), RudderEventActor, Some(msg), true)).unit)
    }
  }

  override def onUserAskUpdateAllNodes(actor: EventActor): IOResult[Unit] = {
    DataSourceLoggerPure.info(s"Fetching data from data sources for all node because ${actor.name} asked for it") *>
    fetchAllNode(actor, None)
  }

  override def onUserAskUpdateAllNodesFor(actor: EventActor, datasourceId: DataSourceId): IOResult[Unit] = {
    DataSourceLoggerPure.info(s"Fetching data from data source '${datasourceId.value}' for all node because ${actor.name} asked for it") *>
    fetchAllNode(actor, Some(datasourceId))
  }

  // just to factorise the same code
  private[this] def fetchAllNode(actor: EventActor, datasourceId: Option[DataSourceId]) = {
    foreachDatasourceScheduler(ds => ds.enabled && datasourceId.fold(true)(id => ds.id == id)){ dss =>
      //for that one, do a scheduler restart
      val msg = s"Refreshing data from data source ${dss.datasource.name.value} on user ${actor.name} request"
      DataSourceLoggerPure.debug(msg) *>
      dss.doActionAndSchedule(fetch.queryAll(dss.datasource, UpdateCause(ModificationId(uuidGen.newUuid), actor, Some(msg))).unit)
    }
  }

  override def onUserAskUpdateNode(actor: EventActor, nodeId: NodeId): IOResult[Unit] = {
    DataSourceLoggerPure.info(s"Fetching data from data source for node '${nodeId.value}' because '${actor.name}' asked for it") *>
    fetchOneNode(actor, nodeId, None)
  }

  override def onUserAskUpdateNodeFor(actor: EventActor, nodeId: NodeId, datasourceId: DataSourceId): IOResult[Unit] = {
    DataSourceLoggerPure.info(s"Fetching data from data source for node '${nodeId.value}' because '${actor.name}' asked for it") *>
    fetchOneNode(actor, nodeId, Some(datasourceId))
  }

  private[this] def fetchOneNode(actor: EventActor, nodeId: NodeId, datasourceId: Option[DataSourceId]) = {
    foreachDatasourceScheduler(ds => ds.enabled && datasourceId.fold(true)(id => ds.id == id)){ dss =>
      //for that one, no scheduler restart
      val msg = s"Fetching data for data source ${dss.datasource.name.value} (${dss.datasource.id.value}) for node '${nodeId.value}' on user '${actor.name}' request"
      DataSourceLoggerPure.debug(msg) *>
      fetch.queryOne(dss.datasource, nodeId, UpdateCause(ModificationId(uuidGen.newUuid), RudderEventActor, Some(msg))).unit
    }
  }

  override def startAll(): IOResult[Unit] = {
    //sort by period (the least frequent the last),
    //then start them every minutes
    val toStart = datasources.all().map(_.values.flatMap { dss =>
      dss.datasource.runParam.schedule match {
        case Scheduled(d) => Some((d, dss))
        case _            => None
      }
    }.toList.sortBy( _._1.toMillis ).zipWithIndex)

    toStart.map(l => ZIO.foreach(l) { case ((period, dss), i) =>
      dss.startWithDelay((i+1).minutes)
    })
  }

}

class DataSourceJdbcRepository(
    doobie    : Doobie
) extends DataSourceRepository {

  import doobie._

  implicit val dataSourceRead: Read[DataSource] = {
    import net.liftweb.json.parse
    Read[(DataSourceId,String)].map(
        tuple => DataSourceExtractor.CompleteJson.extractDataSource(tuple._1,parse(tuple._2)) match {
          case net.liftweb.common.Full(s) => s
          case eb : net.liftweb.common.EmptyBox  =>
            val fail = eb ?~! s"Error when deserializing data source ${tuple._1} from following data: ${tuple._2}"
            throw new RuntimeException(fail.messageChain)
        }
      )
  }
  implicit val dataSourceWrite: Write[DataSource] = {
    import com.normation.plugins.datasources.DataSourceJsonSerializer._
    import net.liftweb.json.compactRender
    Write[(DataSourceId,String)].contramap(source => (source.id, compactRender(serialize(source))))
  }

  override def getAllIds: IOResult[Set[DataSourceId]] = {
    transactIOResult("Error when getting datasource IDs")(xa => query[DataSourceId]("""select id from datasources""").to[Set].transact(xa))
  }

  override def getAll: IOResult[Map[DataSourceId,DataSource]] = {
    transactIOResult("Error when getting datasource")(xa => query[DataSource]("""select id, properties from datasources""").to[Vector].map { _.map( ds => (ds.id,ds)).toMap }.transact(xa))
  }

  override def get(sourceId : DataSourceId): IOResult[Option[DataSource]] = {
    transactIOResult(s"Error when getting datasource '${sourceId.value}'")(xa => sql"""select id, properties from datasources where id = ${sourceId.value}""".query[DataSource].option.transact(xa))
  }

  override def save(source : DataSource): IOResult[DataSource] = {
    import net.liftweb.json.compactRender
    val json = compactRender(DataSourceJsonSerializer.serialize(source))
    val insert = """insert into datasources (id, properties) values (?, ?)"""
    val update = s"""update datasources set properties = ? where id = ?"""
    import cats.implicits._
    val sql = for {
      rowsAffected <- Update[(String,String)](update).run((json, source.id.value))
      result       <- rowsAffected match {
                        case 0 =>
                          DataSourceLogger.debug(s"source ${source.id} is not present in database, creating it")
                          Update[DataSource](insert).run(source)
                        case 1 => 1.pure[ConnectionIO]
                        case n => throw new RuntimeException(s"Expected 0 or 1 change, not ${n} for ${source.id}")
                      }
    } yield {
      result
    }


    DataSource.reservedIds.get(source.id) match {
      case None =>
        transactIOResult(s"Error when saving datasource '${source.id.value}'")(xa => sql.map(_ => source).transact(xa))

      case Some(msg) =>
        Inconsistency(s"You can't use the reserved data sources id '${source.id.value}': ${msg}").fail
    }
  }

  override def delete(sourceId : DataSourceId): IOResult[DataSourceId] = {
    val query = sql"""delete from datasources where id = ${sourceId}"""
    transactIOResult(s"Error when deleting datasource '${sourceId.value}'")(xa => query.update.run.map(_ => sourceId).transact(xa))
  }

}
