package org.sunbird.graph.schema

import java.util
import java.util.concurrent.CompletionException

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Relation}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

object DefinitionNode {
    val mapper: ObjectMapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

  def validate(request: Request, setDefaultValue: Boolean = true)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
      val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
      val version: String = request.getContext.get("version").asInstanceOf[String]
      val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
      val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
      val inputNode = definition.getNode(request.getRequest)
	  updateRelationMetadata(inputNode)
      definition.validate(inputNode, "create", setDefaultValue) recoverWith { case e: CompletionException => throw e.getCause}
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

    def getRelationsMap(request: Request): java.util.HashMap[String, AnyRef] = {
        val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
        definition.getRelationsMap()
    }

    def getRestrictedProperties(graphId: String, version: String, operation: String, schemaName: String): List[String] = {
      val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
      definition.getRestrictPropsConfig(operation)
    }

    def getNode(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val definition = DefinitionFactory.getDefinition(request.getContext.get("graph_id").asInstanceOf[String]
            , schemaName, request.getContext.get("version").asInstanceOf[String])
        definition.getNode(request.get("identifier").asInstanceOf[String], "read", if(request.getRequest.containsKey("mode")) request.get("mode").asInstanceOf[String] else "read")
    }

    @throws[Exception]
    def validate(identifier: String, request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
        val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val reqVersioning: String = request.getContext.getOrDefault("versioning", "").asInstanceOf[String]
        val versioning = if(StringUtils.isBlank(reqVersioning)) None else Option(reqVersioning)
	      val req:util.HashMap[String, AnyRef] = new util.HashMap[String, AnyRef](request.getRequest)
        val skipValidation: Boolean = {if(request.getContext.containsKey("skipValidation")) request.getContext.get("skipValidation").asInstanceOf[Boolean] else false}
        val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
        definition.getNode(identifier, "update", null, versioning).map(dbNode => {
            resetJsonProperties(dbNode, graphId, version, schemaName)
            val inputNode: Node = definition.getNode(dbNode.getIdentifier, request.getRequest, dbNode.getNodeType)
            val dbRels = getDBRelations(graphId, schemaName, version, req, dbNode)
            setRelationship(dbNode, inputNode, dbRels)
            if (dbNode.getIdentifier.endsWith(".img") && StringUtils.equalsAnyIgnoreCase("Yes", dbNode.getMetadata.get("isImageNodeCreated").asInstanceOf[String])) {
                inputNode.getMetadata.put("versionKey", dbNode.getMetadata.get("versionKey"))
                dbNode.getMetadata.remove("isImageNodeCreated")
            }
            dbNode.getMetadata.putAll(inputNode.getMetadata)
            if (MapUtils.isNotEmpty(inputNode.getExternalData)) {
                if (MapUtils.isNotEmpty(dbNode.getExternalData))
                    dbNode.getExternalData.putAll(inputNode.getExternalData)
                else
                    dbNode.setExternalData(inputNode.getExternalData)
            }
            if (!skipValidation)
                definition.validate(dbNode, "update")
            else Future (dbNode)

        }).flatMap(f => f)
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
					case "Live" => RedisCache.addToList(cacheKey, data)
					case "Retired" => RedisCache.removeFromList(cacheKey, data)
				}
			}
		}
		node
	}

    private def setRelationship(dbNode: Node, inputNode: Node, dbRels:util.Map[String, util.List[Relation]]): Unit = {
	    var addRels: util.List[Relation] = new util.ArrayList[Relation]()
        var delRels: util.List[Relation] = new util.ArrayList[Relation]()
        val inRel: util.List[Relation] = dbNode.getInRelations
        val outRel: util.List[Relation] = dbNode.getOutRelations
        val inRelReq: util.List[Relation] = if(CollectionUtils.isNotEmpty(inputNode.getInRelations)) new util.ArrayList[Relation](inputNode.getInRelations) else new util.ArrayList[Relation]()
        val outRelReq: util.List[Relation] = if(CollectionUtils.isNotEmpty(inputNode.getOutRelations)) new util.ArrayList[Relation](inputNode.getOutRelations) else new util.ArrayList[Relation]()
        if (CollectionUtils.isNotEmpty(inRelReq)) {
	        if(CollectionUtils.isNotEmpty(dbRels.get("in"))){
		        inRelReq.addAll(dbRels.get("in"))
		        inputNode.setInRelations(inRelReq)
	        }
            getNewRelationsList(inRel, inRelReq, addRels, delRels)
        }
	    if (CollectionUtils.isNotEmpty(outRelReq)) {
		    if(CollectionUtils.isNotEmpty(dbRels.get("out"))){
			    outRelReq.addAll(dbRels.get("out"))
			    inputNode.setOutRelations(outRelReq)
		    }
            getNewRelationsList(outRel, outRelReq, addRels, delRels)
	    }
	    if (CollectionUtils.isNotEmpty(addRels)) {
            dbNode.setAddedRelations(addRels)
	        updateRelationMetadata(dbNode)
        }
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

	def updateRelationMetadata(node: Node): Unit = {
		var relOcr = new util.HashMap[String, Integer]()
		val rels = node.getAddedRelations
		for (rel <- rels) {
			val relKey = rel.getStartNodeObjectType + rel.getRelationType + rel.getEndNodeObjectType
			if (relOcr.containsKey(relKey))
				relOcr.put(relKey, relOcr.get(relKey) + 1)
			else relOcr.put(relKey, 1)

			if (relKey.contains("hasSequenceMember")) {
				rel.setMetadata(new util.HashMap[String, AnyRef]() {{
						put("IL_SEQUENCE_INDEX", relOcr.get(relKey));
					}})
			} else rel.setMetadata(new util.HashMap[String, AnyRef]())
		}
		node.setAddedRelations(rels)
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

	def getDBRelations(graphId:String, schemaName:String, version:String, request: util.Map[String, AnyRef], dbNode: Node):util.Map[String, util.List[Relation]] = {
		val inRelations = new util.ArrayList[Relation]()
		val outRelations = new util.ArrayList[Relation]()
		val relDefMap = getRelationDefinitionMap(graphId, version, schemaName);
		if (null != dbNode) {
			if (CollectionUtils.isNotEmpty(dbNode.getInRelations)) {
				for (inRel <- dbNode.getInRelations()) {
					val key = inRel.getRelationType() + "_in_" + inRel.getStartNodeObjectType()
					if (relDefMap.containsKey(key)) {
						val value = relDefMap.get(key).get
						if (!request.containsKey(value)) {
							inRelations.add(inRel)
						}
					}
				}
			}
			if (CollectionUtils.isNotEmpty(dbNode.getOutRelations)) {
				for (outRel <- dbNode.getOutRelations()) {
					val key = outRel.getRelationType() + "_out_" + outRel.getEndNodeObjectType()
					if (relDefMap.containsKey(key)) {
						val value = relDefMap.get(key).get
						if (!request.containsKey(value)) {
							outRelations.add(outRel)
						}
					}
				}
			}
		}
		new util.HashMap[String, util.List[Relation]](){{
			put("in", inRelations)
			put("out",outRelations)
		}}
	}

    def validateContentNodes(nodes: List[Node], graphId: String, schemaName: String, version: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[List[Node]] = {
        val definition = DefinitionFactory.getDefinition(graphId, schemaName, version)
        val futures = nodes.map(node => {
            definition.validate(node, "update") recoverWith { case e: CompletionException => throw e.getCause }
        })
        Future.sequence(futures)
    }
    def updateJsonPropsInNodes(nodes: List[Node], graphId: String, schemaName: String, version: String) = {
        val jsonProps = fetchJsonProps(graphId, version, schemaName)
        nodes.map(node => {
            val metadata = node.getMetadata
            metadata.filter(entry => jsonProps.contains(entry._1)).map(entry => node.getMetadata.put(entry._1, convertJsonProperties(entry, jsonProps)))
        })
    }
    def convertJsonProperties(entry: (String, AnyRef), jsonProps: scala.List[String]) = {
        try {
            mapper.readTree(entry._2.asInstanceOf[String])
        } catch {
            case e: Exception => entry._2
        }
    }
}

