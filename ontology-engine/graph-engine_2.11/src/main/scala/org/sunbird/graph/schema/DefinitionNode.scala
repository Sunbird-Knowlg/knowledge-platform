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

}
