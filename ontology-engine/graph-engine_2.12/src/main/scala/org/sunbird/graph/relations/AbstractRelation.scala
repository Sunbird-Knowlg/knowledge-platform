package org.sunbird.graph.relations

import org.apache.commons.lang3.{BooleanUtils, StringUtils}
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{MiddlewareException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.GraphDACParams
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.exception.GraphErrorCodes
import org.sunbird.graph.schema.DefinitionFactory
import org.sunbird.graph.service.operation.{Neo4JBoltGraphOperations, Neo4JBoltSearchOperations}

import scala.concurrent.ExecutionContext

abstract class AbstractRelation(graphId: String, startNode: Node, endNode: Node, metadata: java.util.Map[String, AnyRef]) extends IRelation {

    override def createRelation(req: Request): String = {
        val request = new Request(req)
        request.put(GraphDACParams.start_node_id.name, startNode.getIdentifier)
        request.put(GraphDACParams.relation_type.name, getRelationType)
        request.put(GraphDACParams.end_node_id.name, endNode.getIdentifier)
        request.put(GraphDACParams.metadata.name, metadata)
        try {
            Neo4JBoltGraphOperations.createRelation(graphId, startNode.getIdentifier, endNode.getIdentifier, getRelationType, request)
        } catch {
            case e: Exception =>
                e.getMessage;
        }
        null
    }

    def validateNodeTypes(node: Node, nodeTypes: List[String]): String = {
        if(!nodeTypes.contains(startNode.getNodeType)) "Node " + node.getIdentifier + " is not a " + nodeTypes
        else null
    }

    def validateObjectTypes(startNodeObjectType: String, endNodeObjectType: String, schemaName: String, schemaVersion: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): String = {
        if(StringUtils.isNotBlank(startNodeObjectType) && StringUtils.isNotBlank(endNodeObjectType)) {
            val objectTypes = DefinitionFactory.getDefinition("domain", schemaName, schemaVersion).getOutRelationObjectTypes
            if(!objectTypes.contains(getRelationType + ":" + endNodeObjectType)) getRelationType + " is not allowed between " + startNodeObjectType + " and " + endNodeObjectType
            else null
        }
        else null
    }

    def checkCycle(req: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): String = try {
        val request = new Request(req)
        request.put(GraphDACParams.start_node_id.name, this.endNode.getIdentifier)
        request.put(GraphDACParams.relation_type.name, getRelationType)
        request.put(GraphDACParams.end_node_id.name, this.startNode.getIdentifier)
        val result = oec.graphService.checkCyclicLoop(graphId, this.endNode.getIdentifier,this.startNode.getIdentifier, getRelationType())
        val loop = result.get(GraphDACParams.loop.name).asInstanceOf[Boolean]
        if (BooleanUtils.isTrue(loop)) {
            result.get(GraphDACParams.message.name).asInstanceOf[String]
        } else if (StringUtils.equals(startNode.getIdentifier, endNode.getIdentifier)) {
            "Relation '" + getRelationType + "' cannot be created between: " + startNode.getIdentifier + " and " + endNode.getIdentifier
        } else {
            null
        }
    } catch {
        case ex: MiddlewareException =>
            ex.printStackTrace()
            throw ex;
        case e: Exception =>
            e.printStackTrace()
            throw new ServerException(GraphErrorCodes.ERR_RELATION_VALIDATE.toString, "Error occurred while validating the relation", e)
    }
}
