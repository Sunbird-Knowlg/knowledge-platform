package org.sunbird.content.util

import java.util

import org.apache.commons.lang.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.DateUtils
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
        createOrUpdateImageNode(request, node).map(imgResponse => {
          if (!ResponseHandler.checkError(imgResponse)) {
            updateOriginalNode(request).map(response => {
              response
            })
          } else {
            Future(imgResponse)
          }
        }).flatMap(f => f)
      }
    }).flatMap(f => f)
  }

  protected def createOrUpdateImageNode(request: Request, node: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    val req: Request = new Request(request)
    req.getRequest.put(FlagConstants.STATUS, FlagConstants.FLAG_DRAFT)
    req.getRequest.put(FlagConstants.IDENTIFIER, node.getIdentifier)
    req.getRequest.put(FlagConstants.VERSION_KEY, node.getMetadata.get(FlagConstants.VERSION_KEY))
    DataNode.update(req).map(node => {
      ResponseHandler.OK
    })
  }

  protected def updateOriginalNode(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    val response: Response = ResponseHandler.OK()
    request.getRequest.put(FlagConstants.STATUS, FlagConstants.RETIRED)
    request.getContext.put(FlagConstants.VERSIONING, FlagConstants.DISABLE)
    DataNode.update(request).map(updatedOriginalNode => {
      response.getResult.put(FlagConstants.VERSION_KEY, updatedOriginalNode.getMetadata.get(FlagConstants.VERSION_KEY))
      response.getResult.put(FlagConstants.NODE_ID, updatedOriginalNode.getIdentifier)
      if (StringUtils.equals(updatedOriginalNode.getMetadata.getOrDefault(FlagConstants.MIME_TYPE, "").asInstanceOf[String], FlagConstants.MIME_TYPE_COLLECTION)) {
        request.put(FlagConstants.ROOT_ID, updatedOriginalNode.getIdentifier)
        updateHierarchy(request, updatedOriginalNode.getMetadata.get(FlagConstants.VERSION_KEY).asInstanceOf[String]).map(hierarchyResponse => {
          if (!ResponseHandler.checkError(hierarchyResponse)) {
            response
          } else {
            hierarchyResponse
          }
        })
      } else {
        Future(response)
      }
    }).flatMap(f => f)
  }

  protected def updateHierarchy(request: Request, versionKey: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    request.getContext.put(FlagConstants.SCHEMA_NAME, FlagConstants.COLLECTION_SCHEMA_NAME)
    HierarchyManager.getHierarchy(request).map(hierarchyResponse => {
      if (!ResponseHandler.checkError(hierarchyResponse)) {
        val updatedHierarchy = hierarchyResponse.getResult.get(FlagConstants.CONTENT).asInstanceOf[util.Map[String, AnyRef]]
        updatedHierarchy.putAll(new util.HashMap[String, AnyRef]() {{
          put(FlagConstants.STATUS, FlagConstants.RETIRED)
          put(FlagConstants.VERSION_KEY, versionKey)
          put(FlagConstants.LAST_STATUS_CHANGED_ON, DateUtils.formatCurrentDate())
        }})
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
