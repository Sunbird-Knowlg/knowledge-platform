package org.sunbird.content.util

import java.util

import org.apache.commons.lang.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.DateUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.utils.HierarchyConstants

import scala.concurrent.{ExecutionContext, Future}

object RetireManager {

    def retire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        validateRequest(request)
        getNodeToRetire(request).flatMap(node => {
            updateNodesToRetire(request)
        })
    }

    def getNodeToRetire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = DataNode.read(request).map(node => {
        if (StringUtils.equalsIgnoreCase("Retired", node.getMetadata.get("status").asInstanceOf[String]))
            throw new ClientException("ERR_CONTENT_RETIRE", "Content with Identifier " + node.getIdentifier + " is already Retired.")
        node
    })

    def validateRequest(request: Request) = {
        val contentId: String = request.get("identifier").asInstanceOf[String]
        if (StringUtils.isBlank(contentId) || StringUtils.endsWithIgnoreCase(contentId, HierarchyConstants.IMAGE_SUFFIX)) {
            throw new ClientException("ERR_INVALID_CONTENT_ID", "Please Provide Valid Content Identifier.")
        }
    }

    private def updateNodesToRetire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        RedisCache.delete(request.get("identifier").asInstanceOf[String])
        val updateReq = new Request(request)
        updateReq.put("identifiers", java.util.Arrays.asList(request.get("identifier").asInstanceOf[String], request.get("identifier").asInstanceOf[String] + HierarchyConstants.IMAGE_SUFFIX))
        updateReq.put("metadata", new util.HashMap[String, AnyRef]() {
            {
                put(HierarchyConstants.STATUS, "Retired")
                put(HierarchyConstants.LAST_UPDATED_ON, DateUtils.formatCurrentDate)
                put(HierarchyConstants.LAST_STATUS_CHANGED_ON, DateUtils.formatCurrentDate)
            }
        })
        DataNode.bulkUpdate(updateReq).map(node => {
            val response = ResponseHandler.OK()
            response.put("identifier", request.get("identifier"))
            response.put("node_id", request.get("identifier"))
        })
    }
}
