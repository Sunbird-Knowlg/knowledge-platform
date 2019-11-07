package org.sunbird.graph.schema

import org.sunbird.common.dto.Request
import org.sunbird.graph.dac.model.Node

import scala.concurrent.{ExecutionContext, Future}

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
}
