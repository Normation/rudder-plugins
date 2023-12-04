package com.normation.plugins.changevalidation

import better.files.File
import com.normation.errors.IOResult
import com.normation.eventlog.EventActor
import com.normation.inventory.domain.NodeId
import com.normation.rudder.domain.nodes.NodeGroup
import com.normation.rudder.domain.nodes.NodeGroupCategory
import com.normation.rudder.domain.nodes.NodeGroupCategoryId
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.repository.CategoryAndNodeGroup
import com.normation.rudder.repository.FullNodeGroupCategory
import com.normation.rudder.repository.RoNodeGroupRepository
import com.normation.zio.UnsafeRun
import scala.collection.immutable.SortedMap
import zio.Chunk
import zio.Ref
import zio.syntax._

class MockSupervisedTargets(unsupervisedDir: File, unsupervisedFilename: String, fullNodeGroupCategory: FullNodeGroupCategory) {

  val unsupervisedRepo = new UnsupervisedTargetsRepository(unsupervisedDir.path, unsupervisedFilename)

  object nodeGroupRepo extends RoNodeGroupRepository {

    override def getFullGroupLibrary(): IOResult[FullNodeGroupCategory] = {
      fullNodeGroupCategory.succeed
    }

    override def getNodeGroupOpt(id: NodeGroupId):                      IOResult[Option[(NodeGroup, NodeGroupCategoryId)]]                = ???
    override def getNodeGroupCategory(id: NodeGroupId):                 IOResult[NodeGroupCategory]                                       = ???
    override def getAll():                                              IOResult[Seq[NodeGroup]]                                          = ???
    override def getAllNodeIds():                                       IOResult[Map[NodeGroupId, Set[NodeId]]]                           = ???
    override def getAllNodeIdsChunk():                                  IOResult[Map[NodeGroupId, Chunk[NodeId]]]                         = ???
    override def getGroupsByCategory(
        includeSystem: Boolean
    ): IOResult[SortedMap[List[NodeGroupCategoryId], CategoryAndNodeGroup]] = ???
    override def findGroupWithAnyMember(nodeIds: Seq[NodeId]):          IOResult[Seq[NodeGroupId]]                                        = ???
    override def findGroupWithAllMember(nodeIds: Seq[NodeId]):          IOResult[Seq[NodeGroupId]]                                        = ???
    override def getRootCategory():                                     NodeGroupCategory                                                 = ???
    override def getRootCategoryPure():                                 IOResult[NodeGroupCategory]                                       = ???
    override def getCategoryHierarchy:                                  IOResult[SortedMap[List[NodeGroupCategoryId], NodeGroupCategory]] = ???
    override def getAllGroupCategories(includeSystem: Boolean):         IOResult[Seq[NodeGroupCategory]]                                  = ???
    override def getGroupCategory(id: NodeGroupCategoryId):             IOResult[NodeGroupCategory]                                       = ???
    override def getParentGroupCategory(id: NodeGroupCategoryId):       IOResult[NodeGroupCategory]                                       = ???
    override def getParents_NodeGroupCategory(id: NodeGroupCategoryId): IOResult[List[NodeGroupCategory]]                                 = ???
    override def getAllNonSystemCategories():                           IOResult[Seq[NodeGroupCategory]]                                  = ???
  }

}

class MockValidatedUsers(users: Map[EventActor, WorkflowUsers]) {

  object validatedUserRepo extends RoValidatedUserRepository with WoValidatedUserRepository {

    private val cache: Ref[Map[EventActor, WorkflowUsers]] = Ref.Synchronized.make(users).runNow

    override def getUsers(): IOResult[Set[WorkflowUsers]] = {
      cache.get.map(_.values.toSet)
    }

    override def deleteUser(actor: EventActor): IOResult[EventActor] = {
      cache.update(_.removed(actor)).as(actor)
    }

    override def saveWorkflowUsers(actor: List[EventActor]): IOResult[Set[WorkflowUsers]] = {
      cache
        .updateAndGet(currentUsers => actor.map(a => a -> WorkflowUsers(a, true, currentUsers.values.toList.contains(a))).toMap)
        .map(_.values.toSet)
    }

    override def getValidatedUsers():           IOResult[Seq[EventActor]]    = ???
    override def get(actor: EventActor):        IOResult[Option[EventActor]] = ???
    override def createUser(newVU: EventActor): IOResult[EventActor]         = ???
  }

}
