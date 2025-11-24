package org.sunbird.content.actors

import javax.inject.Inject
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.managers.{HierarchyManager, UpdateHierarchyManager}
import org.sunbird.utils.HierarchyConstants

import scala.concurrent.{ExecutionContext, Future}

class CollectionActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        request.getContext.put(HierarchyConstants.SCHEMA_NAME, HierarchyConstants.COLLECTION_SCHEMA_NAME)
        request.getOperation match {
            case "addHierarchy" => HierarchyManager.addLeafNodesToHierarchy(request)
            case "removeHierarchy" => HierarchyManager.removeLeafNodesFromHierarchy(request)
            case "updateHierarchy" => UpdateHierarchyManager.updateHierarchy(request)
            case "getHierarchy" => HierarchyManager.getHierarchy(request)
            case _ => ERROR(request.getOperation)
        }
    }

}
