package org.sunbird.graph.relations

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ErrorCodes, ResponseCode, ServerException}
import org.sunbird.graph.common.enums.GraphDACParams
import org.sunbird.graph.dac.enums.RelationTypes
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.exception.GraphRelationErrorCodes

class SequenceMembershipRelation(graphId: String, startNode: Node, endNode: Node, metadata: java.util.Map[String, AnyRef]) extends AbstractRelation(graphId, startNode, endNode, metadata) {

    override def getRelationType(): String = {
        RelationTypes.SEQUENCE_MEMBERSHIP.relationName()

    }

    override def validate(request: Request): List[String] = try {
        val errList = List(checkCycle(request)).filter(err => StringUtils.isNotBlank(err))
        errList
    } catch {
        case e: Exception =>
            throw new ServerException(GraphRelationErrorCodes.ERR_RELATION_VALIDATE.name, e.getMessage, e)
    }


}
