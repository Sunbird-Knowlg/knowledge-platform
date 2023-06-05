package org.sunbird.managers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mashape.unirest.http.{HttpResponse, Unirest}
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception._
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.{DefinitionNode, ObjectCategoryDefinition}
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}
import org.sunbird.schema.SchemaValidatorFactory
import org.sunbird.utils.{HierarchyBackwardCompatibilityUtil, HierarchyConstants, HierarchyErrorCodes}

import java.util
import java.util.concurrent.CompletionException
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.JavaConverters
import scala.collection.JavaConverters.{asJavaIterableConverter, mapAsScalaMapConverter}
import scala.concurrent.{ExecutionContext, Future}

object FrameworkHierarchyManager {

    val schemaName: String = "framework"
    val schemaVersion: String = "1.0"
    val imgSuffix: String = ".img"
    val hierarchyPrefix: String = "hierarchy_"
    val statusList = List("Live", "Unlisted", "Flagged")

    val keyTobeRemoved: util.List[String] = {
        if(Platform.config.hasPath("framework.hierarchy.removed_props_for_leafNodes"))
            Platform.config.getStringList("framework.hierarchy.removed_props_for_leafNodes")
        else
            java.util.Arrays.asList("framework","children","usedByContent","item_sets","methods","libraries","editorState")
    }

    val mapPrimaryCategoriesEnabled: Boolean = if (Platform.config.hasPath("framework.primarycategories.mapping.enabled")) Platform.config.getBoolean("framework.primarycategories.mapping.enabled") else true
    val objectTypeAsContentEnabled: Boolean = if (Platform.config.hasPath("objecttype.as.content.enabled")) Platform.config.getBoolean("objecttype.as.content.enabled") else true

    def generateFrameworkHierarchy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[util.Map[String, AnyRef]] = {
        val rootNodeFuture = getRootNode(request)
        val categories = new util.ArrayList[util.Map[String, AnyRef]]
        rootNodeFuture.map(rootNode => {
            if (StringUtils.equalsIgnoreCase("Retired", rootNode.getMetadata.getOrDefault("status", "").asInstanceOf[String])) {
                Future(ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name(), "rootId " + request.get("rootId") + " does not exist"))
            }
            val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(rootNode, new util.ArrayList[String](), request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
            val outRelations = rootNode.getOutRelations()
            outRelations.map(relation => {
                val getNodeReq = new Request()
                getNodeReq.setContext(new util.HashMap[String, AnyRef]() {
                    {
                        putAll(request.getContext)
                    }
                })
                getNodeReq.getContext.put("schemaName", relation.getEndNodeObjectType)
                getNodeReq.put("version", schemaVersion)
                getNodeReq.put("identifier", relation.getEndNodeId)
                DataNode.read(getNodeReq).map(nodeData => {
                    val nodeInfo: util.Map[String, AnyRef]= NodeUtil.serialize(nodeData, new util.ArrayList[String](), getNodeReq.getContext.get("schemaName").asInstanceOf[String], getNodeReq.getContext.get("version").asInstanceOf[String])
                    categories.add(nodeInfo)
                })
                relation.getEndNodeId
            })
            metadata.put("categories", categories)
//            val children = outRelations.map(relation => relation.getEndNodeMetadata).map(metadata => {
//                SystemProperties.values().foreach(value => metadata.remove(value.name()))
//                metadata
//            }).toList
            Future(metadata)
        }).flatMap(f => f)
    }

    def getFrameworkHierarchy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Map[String, AnyRef]] = {
        val req = new Request(request)
        req.put("identifier", request.get("identifier"))
        val responseFuture = oec.graphService.readExternalProps(req, List("hierarchy"))
        responseFuture.map(response => {
            if (!ResponseHandler.checkError(response)) {
                val hierarchyString = response.getResult.toMap.getOrDefault("hierarchy", "").asInstanceOf[String]
                if (StringUtils.isNotEmpty(hierarchyString)) {
                    Future(JsonUtils.deserialize(hierarchyString, classOf[java.util.Map[String, AnyRef]]).toMap)
                } else
                    Future(Map[String, AnyRef]())
            } else
                throw new ServerException("ERR_WHILE_FETCHING_HIERARCHY_FROM_CASSANDRA", "Error while fetching hierarchy from cassandra")
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }

    private def getRootNode(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val req = new Request(request)
        req.put("identifier", request.get("rootId").asInstanceOf[String])
        req.put("mode", request.get("mode").asInstanceOf[String])
        req.put("fields",request.get("fields").asInstanceOf[java.util.List[String]])
        DataNode.read(req)
    }

    def convertNodeToMap(leafNodes: List[Node])(implicit oec: OntologyEngineContext, ec: ExecutionContext): java.util.List[java.util.Map[String, AnyRef]] = {
        leafNodes.map(node => {
            val nodeMap: java.util.Map[String, AnyRef] = NodeUtil.serialize(node, null, node.getObjectType.toLowerCase().replace("image", ""), schemaVersion)
            nodeMap.keySet().removeAll(keyTobeRemoved)
            nodeMap
        })
    }

}
