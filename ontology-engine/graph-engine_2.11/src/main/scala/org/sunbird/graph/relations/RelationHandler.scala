package org.sunbird.graph.relations

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.enums.RelationTypes
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.exception.GraphRelationErrorCodes

object RelationHandler {

    def getRelation(graphId: String, startNode: Node, relationType: String, endNode: Node, metadata: java.util.Map[String, AnyRef]): IRelation = {
        if (StringUtils.isNotBlank(relationType) && RelationTypes.isValidRelationType(relationType)) {
            if (StringUtils.equals(RelationTypes.ASSOCIATED_TO.relationName, relationType))
                new AssociationRelation(graphId, startNode, endNode, metadata)
            else if (StringUtils.equals(RelationTypes.SEQUENCE_MEMBERSHIP.relationName, relationType))
                new SequenceMembershipRelation(graphId, startNode, endNode, metadata)
            else {
                throw new ClientException(GraphRelationErrorCodes.ERR_RELATION_CREATE.name, "UnSupported Relation: " + relationType)
            }
        } else {
            throw new ClientException(GraphRelationErrorCodes.ERR_RELATION_CREATE.name, "UnSupported Relation: " + relationType)
        }

    }
}