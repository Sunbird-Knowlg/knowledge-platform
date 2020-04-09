package org.sunbird.content.util

import java.util

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{DateUtils, JsonUtils}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ServerException}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.parseq.Task
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.HierarchyConstants

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object RetireManager {
    val finalStatus: util.List[String] = util.Arrays.asList("Flagged", "Live", "Unlisted")

    def retire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        validateRequest(request)
        getNodeToRetire(request).flatMap(node => {
            val futureList = Task.parallel[Response](
                handleCollectionToRetire(node, request),
                updateNodesToRetire(request))
            futureList.map(f => {
                val response = ResponseHandler.OK()
                response.put("identifier", request.get("identifier"))
                response.put("node_id", request.get("identifier"))
            })
        })
    }

    private def getNodeToRetire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = DataNode.read(request).map(node => {
        if (StringUtils.equalsIgnoreCase("Retired", node.getMetadata.get("status").asInstanceOf[String]))
            throw new ClientException("ERR_CONTENT_RETIRE", "Content with Identifier " + node.getIdentifier + " is already Retired.")
        node
    })

    private def validateRequest(request: Request) = {
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
        DataNode.bulkUpdate(updateReq).map(node => ResponseHandler.OK())
    }


    private def handleCollectionToRetire(node: Node, request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        if (StringUtils.equalsIgnoreCase("application/vnd.ekstep.content-collection", node.getMetadata.get("mimeType").asInstanceOf[String]) && finalStatus.contains(node.getMetadata.get("status"))) {
            RedisCache.delete("hierarchy_" + node.getIdentifier)
            val req = new Request(request)
            req.getContext.put("schemaName", "collection")
            req.put("identifier", request.get("identifier"))
            ExternalPropsManager.fetchProps(req, List(HierarchyConstants.HIERARCHY)).map(resp => {
                val hierarchyString = resp.getResult.toMap.getOrElse(HierarchyConstants.HIERARCHY, "").asInstanceOf[String]
                if (StringUtils.isNotBlank(hierarchyString)) {
                    val hierarchyMap = JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]])
                    val childIds = getChildrenIdentifiers(hierarchyMap)
                    //TODO : Implement Delete Units from Elastic Search
                    if(CollectionUtils.isNotEmpty(childIds))
                     RedisCache.delete(childIds.map(id => "hierarchy_" + id): _*)
                    hierarchyMap.put("status", "Retired")
                    hierarchyMap.put(HierarchyConstants.LAST_UPDATED_ON, DateUtils.formatCurrentDate)
                    hierarchyMap.put(HierarchyConstants.LAST_STATUS_CHANGED_ON, DateUtils.formatCurrentDate)
                    req.put("hierarchy", ScalaJsonUtils.serialize(hierarchyMap))
                    ExternalPropsManager.saveProps(req)
                }
                ResponseHandler.OK()
            }) recover { case e: ResourceNotFoundException =>
                TelemetryManager.log("No hierarchy is present in cassandra for identifier:" + node.getIdentifier)
                throw new ServerException("ERR_CONTENT_RETIRE", "Unable to fetch Hierarchy for Root Node: [" + node.getIdentifier + "]")
            }
        } else Future(ResponseHandler.OK())
    }

    private def getChildrenIdentifiers(hierarchyMap: util.HashMap[String, AnyRef]): util.List[String] = {
        val childIds: ListBuffer[String] = ListBuffer[String]()
        addChildIds(hierarchyMap.getOrElse(HierarchyConstants.CHILDREN, new util.ArrayList[util.HashMap[String, AnyRef]]()).asInstanceOf[util.ArrayList[util.HashMap[String, AnyRef]]], childIds)
        bufferAsJavaList(childIds)
    }

    private def addChildIds(childrenMaps: util.ArrayList[util.HashMap[String, AnyRef]], childrenIds: ListBuffer[String]): Unit = {
        if (CollectionUtils.isNotEmpty(childrenMaps)) {
            childrenMaps.filter(child => StringUtils.equalsIgnoreCase(HierarchyConstants.PARENT, child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String])).foreach(child => {
                childrenIds += child.get(HierarchyConstants.IDENTIFIER).asInstanceOf[String]
                addChildIds(child.get(HierarchyConstants.CHILDREN).asInstanceOf[util.ArrayList[util.HashMap[String, AnyRef]]], childrenIds)
            })
        }
    }

}
