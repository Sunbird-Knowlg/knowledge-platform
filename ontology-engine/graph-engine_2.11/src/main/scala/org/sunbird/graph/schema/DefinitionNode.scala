package org.sunbird.graph.schema

import java.util
import java.util.concurrent.CompletionException

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.Request
import org.sunbird.graph.dac.model.{Node, Relation}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

object DefinitionNode {

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

    def getNode(request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val definition = DefinitionFactory.getDefinition(request.getContext.get("graph_id").asInstanceOf[String]
            , schemaName, request.getContext.get("version").asInstanceOf[String])
        definition.getNode(request.get("identifier").asInstanceOf[String], "read", request.get("mode").asInstanceOf[String])
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
            if (dbNode.getIdentifier.endsWith(".img") && StringUtils.equalsAnyIgnoreCase("Yes", dbNode.getMetadata.get("isImageNodeCreated").asInstanceOf[String])) {
                inputNode.getMetadata.put("versionKey", dbNode.getMetadata.get("versionKey"))
                dbNode.getMetadata.remove("isImageNodeCreated")
            }
            dbNode.getMetadata.putAll(inputNode.getMetadata)
            if(MapUtils.isNotEmpty(inputNode.getExternalData)){
                if(MapUtils.isNotEmpty(dbNode.getExternalData))
                    dbNode.getExternalData.putAll(inputNode.getExternalData)
                else
                    dbNode.setExternalData(inputNode.getExternalData)
            }
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

