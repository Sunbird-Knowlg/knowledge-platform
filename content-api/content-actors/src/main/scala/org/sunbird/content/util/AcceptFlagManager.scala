package org.sunbird.content.util

import java.util

import org.apache.commons.lang.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{DateUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.service.common.DACConfigurationConstants
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.managers.HierarchyManager

import scala.concurrent.{ExecutionContext, Future}

object AcceptFlagManager {

  def acceptFlag(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    DataNode.read(request).map(node => {
      if (!StringUtils.equals(ContentConstants.FLAGGED, node.getMetadata.getOrDefault(ContentConstants.STATUS, "").asInstanceOf[String])) {
        Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, ContentConstants.ERR_INVALID_CONTENT, "Invalid Flagged Content! Content Can Not Be Accepted."))
      } else {
        request.getContext.put(ContentConstants.IDENTIFIER, node.getIdentifier)
        createOrUpdateImageNode(request, node).map(imgNode => {
          updateOriginalNode(request, node).map(response => {
            if (!ResponseHandler.checkError(response)) {
              response.put(ContentConstants.NODE_ID, node.getIdentifier)
              response.put(ContentConstants.IDENTIFIER, node.getIdentifier)
              response.put(ContentConstants.VERSION_KEY, imgNode.getMetadata.get(ContentConstants.VERSION_KEY))
              response
            } else {
              response
            }
          })
        }).flatMap(f => f)
      }
    }).flatMap(f => f)
  }

  private def createOrUpdateImageNode(request: Request, node: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val req: Request = new Request(request)
    req.put(ContentConstants.STATUS, ContentConstants.FLAG_DRAFT)
    req.put(ContentConstants.IDENTIFIER, node.getIdentifier)
    DataNode.update(req).map(node => {
      node
    })
  }

  private def updateOriginalNode(request: Request, node: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    val currentDate = DateUtils.formatCurrentDate
    request.put(ContentConstants.STATUS, ContentConstants.RETIRED)
    request.put(ContentConstants.LAST_STATUS_CHANGED_ON, currentDate)
    request.put(ContentConstants.LAST_UPDATED_ON, currentDate)
    request.put(ContentConstants.VERSION_KEY, Platform.config.getString(DACConfigurationConstants.PASSPORT_KEY_BASE_PROPERTY))

    request.getContext.put(ContentConstants.VERSIONING, ContentConstants.DISABLE)

    if (StringUtils.equals(node.getMetadata.getOrDefault(ContentConstants.MIME_TYPE, "").asInstanceOf[String], ContentConstants.COLLECTION_MIME_TYPE)) {
      request.getContext.put(ContentConstants.SCHEMA_NAME, ContentConstants.COLLECTION_SCHEMA_NAME)
      request.put(ContentConstants.ROOT_ID, request.get(ContentConstants.IDENTIFIER))
      HierarchyManager.getHierarchy(request).map(hierarchyResponse => {
        if (!ResponseHandler.checkError(hierarchyResponse)) {
          val updatedHierarchy = hierarchyResponse.get(ContentConstants.CONTENT).asInstanceOf[util.Map[String, AnyRef]]
          updatedHierarchy.putAll(new util.HashMap[String, AnyRef]() {
            {
              put(ContentConstants.STATUS, ContentConstants.RETIRED)
              put(ContentConstants.LAST_STATUS_CHANGED_ON, currentDate)
              put(ContentConstants.LAST_UPDATED_ON, currentDate)
            }
          })
          request.put(ContentConstants.HIERARCHY, ScalaJsonUtils.serialize(updatedHierarchy))
          RedisCache.delete(ContentConstants.HIERARCHY_PREFIX + request.get(ContentConstants.IDENTIFIER).asInstanceOf[String])
          updateNode(request)
        } else {
          Future(hierarchyResponse)
        }
      }).flatMap(f => f)
    } else {
      updateNode(request)
    }
  }

  private def updateNode(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    DataNode.update(request).map(updatedOriginalNode => {
      RedisCache.delete(request.get(ContentConstants.IDENTIFIER).asInstanceOf[String])
      ResponseHandler.OK()
    })
  }

}