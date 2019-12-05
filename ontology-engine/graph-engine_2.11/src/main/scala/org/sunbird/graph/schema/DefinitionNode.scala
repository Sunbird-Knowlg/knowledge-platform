package org.sunbird.graph.schema

import java.util
import java.util.concurrent.CompletionException

import com.fasterxml.jackson.databind.ObjectMapper
import jdk.internal.org.objectweb.asm.TypeReference
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.Request
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

object DefinitionNode {
    val mapper = new ObjectMapper()
    val liveStatus = List("Live", "Unlisted")

  def validate(request: Request)(implicit ec: ExecutionContext): Future[Node] = {
      val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
      val version: String = request.getContext.get("version").asInstanceOf[String]
      val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
      val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
      val inputNode = definition.getNode(request.getRequest)
      definition.validate(inputNode, "create") recoverWith { case e: CompletionException => throw e.getCause}
  }

    def getExternalProps(graphId: String, version: String, schemaName: String): List[String] = {
        val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
        definition.getExternalProps()
    }

    def fetchJsonProps(graphId: String, version: String, schemaName: String): List[String] = {
        val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
        definition.fetchJsonProps()
    }

    def getInRelations(graphId: String, version: String, schemaName: String): List[Map[String, AnyRef]] = {
        val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
        definition.getInRelations()
    }

    def getOutRelations(graphId: String, version: String, schemaName: String): List[Map[String, AnyRef]] = {
        val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
        definition.getOutRelations()
    }

    def getRelationDefinitionMap(graphId: String, version: String, schemaName: String): Map[String, AnyRef] = {
        val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
        definition.getRelationDefinitionMap()
    }

    def getRestrictedProperties(graphId: String, version: String, operation: String, schemaName: String): List[String] = {
      val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
      definition.getRestrictPropsConfig(operation)
    }

    def getNodeWithFallback(request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val mode: String = request.get("mode").asInstanceOf[String]
        val definition = DefinitionFactory.getDefinition(request.getContext.get("graph_id").asInstanceOf[String],
            request.getContext.get("schemaName").asInstanceOf[String],
            request.getContext.get("version").asInstanceOf[String])
        if (StringUtils.isNotEmpty(mode) && StringUtils.equalsIgnoreCase(mode, "edit"))
            getNode(request)
        else
            getNodeFromCache(request, definition.isCacheEnabled())
    }

    def getNodeFromCache(request: Request, cacheEnabled: Boolean)(implicit ec: ExecutionContext): Future[Node] = {
        val cacheString: String = RedisCacheUtil.getString(request.get("identifier").asInstanceOf[String])
        if((StringUtils.isNotEmpty(cacheString) || StringUtils.isNoneBlank(cacheString)) && cacheEnabled) {
            try {
                val cacheMap = mapper.readValue(cacheString, classOf[java.util.Map[String, Object]])
                val definition = DefinitionFactory.getDefinition(request.getContext.get("graph_id").asInstanceOf[String],
                    request.getContext.get("schemaName").asInstanceOf[String],
                    request.getContext.get("version").asInstanceOf[String])
                Future(definition.getNode(cacheMap))
            } catch {
                case e: Exception =>
                    TelemetryManager.error("Exception occurred while converting cache data to map"+ e.getMessage)
                    getNode(request)
            }
        } else {
            TelemetryManager.log("No data found in cache, fallback to getting node from main DB.")
            getNode(request)
        }
    }

    def saveToCache(node: Node)(implicit ec: ExecutionContext): Unit = try
        RedisCacheUtil.saveString(node.getIdentifier, new ObjectMapper().writeValueAsString(node.getMetadata), 0)
    catch {
        case e: Exception => TelemetryManager.error("Exception occurred while saving data to cache " + e.getMessage)
    }

    def getNode(request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val definition = DefinitionFactory.getDefinition(request.getContext.get("graph_id").asInstanceOf[String]
            , schemaName, request.getContext.get("version").asInstanceOf[String])
        val cacheEnabled: Boolean = definition.isCacheEnabled()
        val futureNode:Future[Node] = definition.getNode(request.get("identifier").asInstanceOf[String], "read", request.get("mode").asInstanceOf[String])
        futureNode.map(node => {
            if (cacheEnabled && CollectionUtils.containsAny(liveStatus, node.getMetadata.get("status")) )
                saveToCache(node)
        })
        futureNode
    }

    @throws[Exception]
    def validate(identifier: String, request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val skipValidation: Boolean = {if(request.getContext.containsKey("skipValidation")) request.getContext.get("skipValidation").asInstanceOf[Boolean] else false}
        val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
        val dbNodeFuture = definition.getNode(identifier, "update", null)
        val validationResult: Future[Node] = dbNodeFuture.map(dbNode => {
            resetJsonProperties(dbNode, graphId, version, schemaName)
            val inputNode: Node = definition.getNode(dbNode.getIdentifier, request.getRequest, dbNode.getNodeType)
            setRelationship(dbNode,inputNode)
            dbNode.getMetadata.putAll(inputNode.getMetadata)
            dbNode.setInRelations(inputNode.getInRelations)
            dbNode.setOutRelations(inputNode.getOutRelations)
            dbNode.setExternalData(inputNode.getExternalData)
            if(!skipValidation)
                definition.validate(dbNode,"update")
            else Future{dbNode}
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause}
        validationResult
    }

	def postProcessor(request: Request, node: Node)(implicit ec: ExecutionContext): Node = {
		val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
		val version: String = request.getContext.get("version").asInstanceOf[String]
		val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
		val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
		val edgeKey = definition.getEdgeKey()
		if (null != edgeKey && !edgeKey.isEmpty) {
			val metadata = node.getMetadata
			val cacheKey = "edge_" + request.getObjectType.toLowerCase()
			val data = metadata.containsKey(edgeKey) match {
				case true => List[String](metadata.get(edgeKey).asInstanceOf[String])
				case _ => List[String]()
			}
			if (!data.isEmpty) {
				metadata.get("status") match {
					case "Live" => RedisCacheUtil.saveToList(cacheKey, data)
					case "Retired" => RedisCacheUtil.deleteFromList(cacheKey, data)
				}
			}
		}
		node
	}

    private def setRelationship(dbNode: Node, inputNode: Node): Unit = {
        var addRels: util.List[Relation] = new util.ArrayList[Relation]()
        var delRels: util.List[Relation] = new util.ArrayList[Relation]()
        val inRel: util.List[Relation] = dbNode.getInRelations
        val outRel: util.List[Relation] = dbNode.getOutRelations
        val inRelReq: util.List[Relation] = inputNode.getInRelations
        val outRelReq: util.List[Relation] = inputNode.getOutRelations
        if (CollectionUtils.isNotEmpty(inRelReq))
            getNewRelationsList(inRel, inRelReq, addRels, delRels)
        if (CollectionUtils.isNotEmpty(outRelReq))
            getNewRelationsList(outRel, outRelReq, addRels, delRels)
        if (CollectionUtils.isNotEmpty(addRels))
            dbNode.setAddedRelations(addRels)
        if (CollectionUtils.isNotEmpty(delRels))
            dbNode.setDeletedRelations(delRels)
    }

    private def getNewRelationsList(dbRelations: util.List[Relation], newRelations: util.List[Relation], addRels: util.List[Relation], delRels: util.List[Relation]): Unit = {
        val relList = new util.ArrayList[String]
        for (rel <- newRelations) {
            addRels.add(rel)
            val relKey = rel.getStartNodeId + rel.getRelationType + rel.getEndNodeId
            if (!relList.contains(relKey)) relList.add(relKey)
        }
        if (null != dbRelations && !dbRelations.isEmpty) {
            for (rel <- dbRelations) {
                val relKey = rel.getStartNodeId + rel.getRelationType + rel.getEndNodeId
                if (!relList.contains(relKey)) delRels.add(rel)
            }
        }
    }

    def resetJsonProperties(node: Node, graphId: String, version: String, schemaName: String):Node = {
        val jsonPropList = fetchJsonProps(graphId, version, schemaName)
        if(!jsonPropList.isEmpty){
            node.getMetadata.entrySet().map(entry => {
                if(jsonPropList.contains(entry.getKey)){
                    entry.setValue(JsonUtils.deserialize(entry.getValue.asInstanceOf[String], classOf[Object]))
                }
            })
        }
        node
    }
}

