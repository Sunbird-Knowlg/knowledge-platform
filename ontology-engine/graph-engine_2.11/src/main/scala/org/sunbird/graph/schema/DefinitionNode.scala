package org.sunbird.graph.schema

import java.util

import org.apache.commons.collections4.CollectionUtils
import org.sunbird.common.dto.{Request, ResponseHandler}
import org.sunbird.graph.dac.model.{Node, Relation}

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConversions._

object DefinitionNode {

  def validate(request: Request)(implicit ec: ExecutionContext): Future[Node] = {
    val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
    val version: String = request.getContext.get("version").asInstanceOf[String]
    val definition = DefinitionFactory.getDefinition(graphId, request.getObjectType, version)
    val inputNode = definition.getNode(request.getRequest)
    definition.validate(inputNode, "create")
  }

    def getExternalProps(graphId: String, version: String, objectType: String): List[String] = {
        val definition = DefinitionFactory.getDefinition(graphId, objectType, version)
        definition.getExternalProps()
    }

    def fetchJsonProps(graphId: String, version: String, objectType: String): List[String] = {
        val definition = DefinitionFactory.getDefinition(graphId, objectType, version)
        definition.fetchJsonProps()
    }

    def getInRelations(graphId: String, version: String, objectType: String): List[Map[String, AnyRef]] = {
        val definition = DefinitionFactory.getDefinition(graphId, objectType, version)
        definition.getInRelations()
    }

    def getOutRelations(graphId: String, version: String, objectType: String): List[Map[String, AnyRef]] = {
        val definition = DefinitionFactory.getDefinition(graphId, objectType, version)
        definition.getOutRelations()
    }

    def getRelationDefinitionMap(graphId: String, version: String, objectType: String): Map[String, AnyRef] = {
        val definition = DefinitionFactory.getDefinition(graphId, objectType, version)
        definition.getRelationDefinitionMap()
    }

    def getNode(request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val definition = DefinitionFactory.getDefinition(request.getContext.get("graph_id").asInstanceOf[String], request.getObjectType, request.getContext.get("version").asInstanceOf[String])
        definition.getNode(request.get("identifier").asInstanceOf[String], "read", request.get("mode").asInstanceOf[String])
    }
    def validate(identifier: String, request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val definition = DefinitionFactory.getDefinition(graphId, request.getObjectType, version)
        val dbNodeFuture = definition.getNode(identifier, "update", null)
        val validationResult: Future[Node] = dbNodeFuture.map(dbNode => {
            val inputNode: Node = definition.getNode(dbNode.getIdentifier, request.getRequest, dbNode.getNodeType)
	        setRelationship(dbNode,inputNode)
	        dbNode.getMetadata.putAll(inputNode.getMetadata)
	        dbNode.setInRelations(inputNode.getInRelations)
	        dbNode.setOutRelations(inputNode.getOutRelations)
	        dbNode.setExternalData(inputNode.getExternalData)
            definition.validate(dbNode,"update")
        }).flatMap(f => f)
        validationResult
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
  
}

