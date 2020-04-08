package org.sunbird.content.util

import java.util

import org.apache.commons.lang.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.managers.HierarchyManager

import scala.concurrent.{ExecutionContext, Future}

object AcceptFlagManager {

  def acceptFlag(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    DataNode.read(request).map(node => {
      if (!StringUtils.equals(FlagConstants.CONTENT_OBJECT_TYPE, node.getObjectType) ||
        !StringUtils.equals(FlagConstants.FLAGGED, node.getMetadata.getOrDefault(FlagConstants.STATUS, "").asInstanceOf[String])) {
        Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, FlagConstants.ERR_INVALID_CONTENT, "Invalid Flagged Content! Content Can Not Be Accepted."))
      } else {
        request.getContext.put(FlagConstants.IDENTIFIER, node.getIdentifier)
        createOrUpdateImageNode(request, node).map(imgNode => {
          updateOriginalNode(request).map(response => {
            if (!ResponseHandler.checkError(response)) {
              response.getResult.put(FlagConstants.NODE_ID, node.getIdentifier)
              response.getResult.put(FlagConstants.VERSION_KEY, imgNode.getMetadata.get(FlagConstants.VERSION_KEY))
              response
            } else {
              response
            }
          })
        }).flatMap(f => f)
      }
    }).flatMap(f => f)
  }

  protected def createOrUpdateImageNode(request: Request, node: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val req: Request = new Request(request)
      req.getRequest.put(FlagConstants.STATUS, FlagConstants.FLAG_DRAFT)
      req.getRequest.put(FlagConstants.IDENTIFIER, node.getIdentifier)
      DataNode.update(req).map(node => {
        node
      })
  }

  protected def updateOriginalNode(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    request.getRequest.put(FlagConstants.STATUS, FlagConstants.RETIRED)
    request.getContext.put(FlagConstants.VERSIONING, FlagConstants.DISABLE)
    DataNode.update(request).map(updatedOriginalNode => {
      if (StringUtils.equals(updatedOriginalNode.getMetadata.getOrDefault(FlagConstants.MIME_TYPE, "").asInstanceOf[String], FlagConstants.MIME_TYPE_COLLECTION)) {
        request.put(FlagConstants.ROOT_ID, updatedOriginalNode.getIdentifier)
        updateHierarchy(request, updatedOriginalNode.getMetadata.get(FlagConstants.VERSION_KEY).asInstanceOf[String], updatedOriginalNode.getMetadata.get(FlagConstants.LAST_STATUS_CHANGED_ON).asInstanceOf[String]).map(hierarchyResponse => {
          if (!ResponseHandler.checkError(hierarchyResponse)) {
            ResponseHandler.OK()
          } else {
            hierarchyResponse
          }
        })
      } else {
        Future(ResponseHandler.OK())
      }
    }).flatMap(f => f)
  }

  protected def updateHierarchy(request: Request, versionKey: String, lastStatusChangedOn: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    request.getContext.put(FlagConstants.SCHEMA_NAME, FlagConstants.COLLECTION_SCHEMA_NAME)
    HierarchyManager.getHierarchy(request).map(hierarchyResponse => {
      if (!ResponseHandler.checkError(hierarchyResponse)) {
        val updatedHierarchy = hierarchyResponse.getResult.get(FlagConstants.CONTENT).asInstanceOf[util.Map[String, AnyRef]]
        updatedHierarchy.putAll(new util.HashMap[String, AnyRef]() {
          {
            put(FlagConstants.STATUS, FlagConstants.RETIRED)
            put(FlagConstants.VERSION_KEY, versionKey)
            put(FlagConstants.LAST_STATUS_CHANGED_ON, lastStatusChangedOn)
          }
        })
        val req: Request = new Request(request)
        req.put(FlagConstants.HIERARCHY, ScalaJsonUtils.serialize(updatedHierarchy))
        req.put(FlagConstants.IDENTIFIER, request.get(FlagConstants.ROOT_ID))
        ExternalPropsManager.saveProps(req)
        RedisCache.delete(FlagConstants.HIERARCHY_PREFIX + request.get(FlagConstants.ROOT_ID))
        ResponseHandler.OK
      } else {
        hierarchyResponse
      }
    })
  }
}
