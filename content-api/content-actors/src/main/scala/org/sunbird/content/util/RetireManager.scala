package org.sunbird.content.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.mashape.unirest.http.{HttpResponse, Unirest}

import java.util
import java.util.{Date, UUID}

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
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

import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object RetireManager {
    val finalStatus: util.List[String] = util.Arrays.asList("Flagged", "Live", "Unlisted")
    private val kfClient = new KafkaClient

    def retire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
        validateRequest(request)
        getNodeToRetire(request).flatMap(node => {
            val shallowIds = getShallowCopiedIds(node.getIdentifier)
            if(CollectionUtils.isNotEmpty(shallowIds)){
                throw new ClientException(ContentConstants.ERR_CONTENT_RETIRE, s"Content With Identifier [" + request.get(ContentConstants.IDENTIFIER) + "] Can Not Be Retired. It Has Been Adopted By Other Users.")
            } else { 
                val updateMetadataMap = Map[String, AnyRef](ContentConstants.STATUS -> "Retired", HierarchyConstants.LAST_UPDATED_ON -> DateUtils.formatCurrentDate, HierarchyConstants.LAST_STATUS_CHANGED_ON -> DateUtils.formatCurrentDate)
                val futureList = Task.parallel[Response](
                    handleCollectionToRetire(node, request, updateMetadataMap),
                    updateNodesToRetire(request, updateMetadataMap.asJava))
                futureList.map(f => {
                    val response = ResponseHandler.OK()
                    response.put(ContentConstants.IDENTIFIER, request.get(ContentConstants.IDENTIFIER))
                    response.put("node_id", request.get(ContentConstants.IDENTIFIER))
                })
            }
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
            oec.graphService.readExternalProps(req, List(HierarchyConstants.HIERARCHY)).flatMap(resp => {
                val hierarchyString = resp.getResult.asScala.toMap.getOrElse(HierarchyConstants.HIERARCHY, "").asInstanceOf[String]
                if (StringUtils.isNotBlank(hierarchyString)) {
                    val hierarchyMap = JsonUtils.deserialize(hierarchyString, classOf[util.HashMap[String, AnyRef]])
                    val childIds = getChildrenIdentifiers(hierarchyMap)
                    if (CollectionUtils.isNotEmpty(childIds)) {
                        val topicName = Platform.getString("kafka.topics.graph.event", "sunbirddev.learning.graph.events")
                        childIds.asScala.foreach(id => kfClient.send(ScalaJsonUtils.serialize(getLearningGraphEvent(request, id)), topicName))
                        RedisCache.delete(childIds.asScala.map(id => "hierarchy_" + id).toSeq: _*)
                    }
                    hierarchyMap.putAll(updateMetadataMap.asJava)
                    req.put(HierarchyConstants.HIERARCHY, ScalaJsonUtils.serialize(hierarchyMap))
                    oec.graphService.saveExternalProps(req)
                } else Future(ResponseHandler.OK())
            }) recover { case e: ResourceNotFoundException =>
                TelemetryManager.log("No hierarchy is present in cassandra for identifier:" + node.getIdentifier)
                throw new ServerException("ERR_CONTENT_RETIRE", "Unable to fetch Hierarchy for Root Node: [" + node.getIdentifier + "]")
            }
        } else Future(ResponseHandler.OK())
    }

    def getShallowCopiedIds(rootId: String)(implicit ec: ExecutionContext) = {
        val result = new util.ArrayList[String]()
        val mapper: ObjectMapper = new ObjectMapper()
        val searchRequest: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {
            put("request", new util.HashMap[String, AnyRef]() {
                put("filters", new util.HashMap[String, AnyRef]() {
                    put("objectType", "Content")
                    put("status", new util.ArrayList[String]())
                    put("origin", rootId)
                })
                put("fields", new util.ArrayList[String]() {
                    add("identifier")
                    add("originData")
                    add("status")
                })
                put("exists", new util.ArrayList[String]() {
                    add("originData")
                })
            })
        }
        val url: String = if (Platform.config.hasPath("composite.search.url")) Platform.config.getString("composite.search.url") else "https://dev.sunbirded.org/action/composite/v3/search"
        val httpResponse: HttpResponse[String] = Unirest.post(url).header("Content-Type", "application/json").body(mapper.writeValueAsString(searchRequest)).asString
        if (httpResponse.getStatus == 200) {
            val response: Response = JsonUtils.deserialize(httpResponse.getBody, classOf[Response])
            if(response.get("count").asInstanceOf[Integer] > 0){
                response.get("content").asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]].asScala.map(content => {
                    val originData = ScalaJsonUtils.deserialize[Map[String, AnyRef]](content.get("originData").asInstanceOf[String])
                    val copyType = originData.getOrElse("copyType", "").asInstanceOf[String]
                    if(StringUtils.isNotBlank(copyType) && StringUtils.equalsIgnoreCase(copyType , "shallow")){
                        result.add(content.get("identifier").asInstanceOf[String])
                    } else { 
                        Future(new util.HashMap[String, AnyRef]()) 
                    }
                })
            }
        } else {
            throw new ServerException("SERVER_ERROR", "Recevied Invalid Search Response For Shallow Copy.")
        }
        result
    }


    private def getChildrenIdentifiers(hierarchyMap: util.HashMap[String, AnyRef]): util.List[String] = {
        val childIds: ListBuffer[String] = ListBuffer[String]()
        addChildIds(hierarchyMap.getOrDefault(HierarchyConstants.CHILDREN, new util.ArrayList[util.HashMap[String, AnyRef]]()).asInstanceOf[util.ArrayList[util.HashMap[String, AnyRef]]], childIds)
        childIds.toList.asJava
    }

    private def addChildIds(childrenMaps: util.ArrayList[util.HashMap[String, AnyRef]], childrenIds: ListBuffer[String]): Unit = {
        if (CollectionUtils.isNotEmpty(childrenMaps)) {
            childrenMaps.asScala.filter(child => StringUtils.equalsIgnoreCase(HierarchyConstants.PARENT, child.get(HierarchyConstants.VISIBILITY).asInstanceOf[String])).foreach(child => {
                childrenIds += child.get(HierarchyConstants.IDENTIFIER).asInstanceOf[String]
                addChildIds(child.get(HierarchyConstants.CHILDREN).asInstanceOf[util.ArrayList[util.HashMap[String, AnyRef]]], childrenIds)
            })
        }
    }

    private def getLearningGraphEvent(request: Request, id: String): Map[String, Any] = Map("ets" -> System.currentTimeMillis(), "channel" -> request.getContext.get(ContentConstants.CHANNEL), "mid" -> UUID.randomUUID.toString, "nodeType" -> "DATA_NODE", "userId" -> "Ekstep", "createdOn" -> DateUtils.format(new Date()), "objectType" -> "Content", "nodeUniqueId" -> id, "operationType" -> "DELETE", "graphId" -> request.getContext.get("graph_id"))

}
