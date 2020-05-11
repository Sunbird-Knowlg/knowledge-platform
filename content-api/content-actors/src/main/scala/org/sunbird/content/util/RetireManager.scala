package org.sunbird.content.util

import java.util
import java.util.{Date, UUID}

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ServerException}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.kafka.client.KafkaClient
import org.sunbird.parseq.Task
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.HierarchyConstants

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object RetireManager {
    val finalStatus: util.List[String] = util.Arrays.asList("Flagged", "Live", "Unlisted")
    private val kfClient = new KafkaClient

    def retire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        validateRequest(request)
        getNodeToRetire(request).flatMap(node => {
            val updateMetadataMap = Map(ContentConstants.STATUS -> "Retired", HierarchyConstants.LAST_UPDATED_ON -> DateUtils.formatCurrentDate, HierarchyConstants.LAST_STATUS_CHANGED_ON -> DateUtils.formatCurrentDate)
            val futureList = Task.parallel[Response](
                handleCollectionToRetire(node, request, updateMetadataMap),
                updateNodesToRetire(request, mapAsJavaMap[String,AnyRef](updateMetadataMap)))
            futureList.map(f => {
                val response = ResponseHandler.OK()
                response.put(ContentConstants.IDENTIFIER, request.get(ContentConstants.IDENTIFIER))
                response.put("node_id", request.get(ContentConstants.IDENTIFIER))
            })
        })
    }

    private def getNodeToRetire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = DataNode.read(request).map(node => {
        if (StringUtils.equalsIgnoreCase("Retired", node.getMetadata.get(ContentConstants.STATUS).asInstanceOf[String]))
            throw new ClientException(ContentConstants.ERR_CONTENT_RETIRE, "Content with Identifier " + node.getIdentifier + " is already Retired.")
        node
    })

    private def validateRequest(request: Request) = {
        val contentId: String = request.get(ContentConstants.IDENTIFIER).asInstanceOf[String]
        if (StringUtils.isBlank(contentId) || StringUtils.endsWithIgnoreCase(contentId, HierarchyConstants.IMAGE_SUFFIX))
            throw new ClientException(ContentConstants.ERR_INVALID_CONTENT_ID, "Please Provide Valid Content Identifier.")
    }

    private def updateNodesToRetire(request: Request, updateMetadataMap: util.Map[String, AnyRef])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        RedisCache.delete(request.get(ContentConstants.IDENTIFIER).asInstanceOf[String])
        val updateReq = new Request(request)
        updateReq.put(ContentConstants.IDENTIFIERS, java.util.Arrays.asList(request.get(ContentConstants.IDENTIFIER).asInstanceOf[String], request.get(ContentConstants.IDENTIFIER).asInstanceOf[String] + HierarchyConstants.IMAGE_SUFFIX))
        updateReq.put(ContentConstants.METADATA, updateMetadataMap)
        DataNode.bulkUpdate(updateReq).map(node => ResponseHandler.OK())
    }


    private def handleCollectionToRetire(node: Node, request: Request, updateMetadataMap: Map[String, AnyRef])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        if (StringUtils.equalsIgnoreCase(ContentConstants.COLLECTION_MIME_TYPE, node.getMetadata.get(ContentConstants.MIME_TYPE).asInstanceOf[String]) && finalStatus.contains(node.getMetadata.get(ContentConstants.STATUS))) {
            RedisCache.delete("hierarchy_" + node.getIdentifier)
            val req = new Request(request)
            req.getContext.put(ContentConstants.SCHEMA_NAME, ContentConstants.COLLECTION_SCHEMA_NAME)
            req.put(ContentConstants.IDENTIFIER, request.get(ContentConstants.IDENTIFIER))
            ExternalPropsManager.fetchProps(req, List(HierarchyConstants.HIERARCHY)).flatMap(resp => {
                val hierarchyString = resp.getResult.toMap.getOrElse(HierarchyConstants.HIERARCHY, "").asInstanceOf[String]
                if (StringUtils.isNotBlank(hierarchyString)) {
                    val hierarchyMap = JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]])
                    val childIds = getChildrenIdentifiers(hierarchyMap)
                    if (CollectionUtils.isNotEmpty(childIds)) {
                        val topicName = Platform.getString("kafka.topics.graph.event", "sunbirddev.learning.graph.events")
                        childIds.foreach(id => kfClient.send(ScalaJsonUtils.serialize(getLearningGraphEvent(request, id)), topicName))
                        RedisCache.delete(childIds.map(id => "hierarchy_" + id): _*)
                    }
                    hierarchyMap.putAll(updateMetadataMap)
                    req.put(HierarchyConstants.HIERARCHY, ScalaJsonUtils.serialize(hierarchyMap))
                    ExternalPropsManager.saveProps(req)
                } else Future(ResponseHandler.OK())
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

    private def getLearningGraphEvent(request: Request, id: String): Map[String, Any] = Map("ets" -> System.currentTimeMillis(), "channel" -> request.getContext.get(ContentConstants.CHANNEL), "mid" -> UUID.randomUUID.toString, "nodeType" -> "DATA_NODE", "userId" -> "Ekstep", "createdOn" -> DateUtils.format(new Date()), "objectType" -> "Content", "nodeUniqueId" -> id, "operationType" -> "DELETE", "graphId" -> request.getContext.get("graph_id"))

}
