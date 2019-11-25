package org.sunbird.graph.schema

import java.util

import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ErrorCodes}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.validator.{BaseDefinitionNode, VersioningNode}
import org.sunbird.graph.validator.NodeValidator
import org.sunbird.schema.SchemaValidatorFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConversions._

class GraphDTO(graphId: String, objectType: String, version: String = "1.0") extends BaseDefinitionNode(graphId , objectType, version) with VersioningNode{

    def validateHierarchy(request: Request)(implicit ec: ExecutionContext) = {
        val rootId: String = request.get("rootId").asInstanceOf[String]
        val unitId: String = request.get("unitId").asInstanceOf[String]
        val leafNodes: List[String] = request.get("leafNodes").asInstanceOf[util.List[String]].toList
        val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val leafNodesFuture = NodeValidator.getDataNodes(graphId, leafNodes)
        leafNodesFuture.map(nodeList => {
            val nodes:util.List[Node] = nodeList.toList.filter(node => (!"Retired".equalsIgnoreCase(node.getMetadata.get("status").asInstanceOf[String])))
            nodes
        }).map(nodes => {
            val nodeIds = nodes.map(node => node.getIdentifier).toList
            if(nodeIds.length != leafNodes.length) {
                val invalidNodes = leafNodes.filter(id => !nodeIds.contains(id))
                throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), "Leafnodes " + invalidNodes + " does not exist")
            }
            else {
                val definition = DefinitionFactory.getDefinition(graphId, request.getObjectType, version)
                val rootNode = definition.getNode(rootId, "update", null)
                //val hierarchy =
            }
        })
    }

}
