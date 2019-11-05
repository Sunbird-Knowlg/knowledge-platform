package org.sunbird.graph.relations

import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ErrorCodes, ResponseCode}
import org.sunbird.graph.common.enums.GraphDACParams
import org.sunbird.graph.dac.enums.{RelationTypes, SystemNodeTypes}
import org.sunbird.graph.dac.model.Node

class AssociationRelation(graphId: String, startNode: Node, endNode: Node, metadata: java.util.Map[String, AnyRef]) extends AbstractRelation(graphId, startNode, endNode, metadata) {

    override def getRelationType(): String = {
        RelationTypes.ASSOCIATED_TO.relationName()
    }

    override def validate(request: Request): List[String] = {
        val errList:List[String] = List(validateNodeTypes(startNode, List(SystemNodeTypes.DATA_NODE.name())),
            validateNodeTypes(endNode, List(SystemNodeTypes.DATA_NODE.name, SystemNodeTypes.SET.name)),
            validateObjectTypes(startNode.getObjectType, endNode.getObjectType)).filter(err => (null != err && !err.isEmpty))
        errList
    }


}
