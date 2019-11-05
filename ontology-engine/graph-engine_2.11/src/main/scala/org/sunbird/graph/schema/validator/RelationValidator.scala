package org.sunbird.graph.schema.validator

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.graph.dac.enums.SystemNodeTypes
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.IDefinition
import org.sunbird.graph.validator.NodeValidator

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

trait RelationValidator extends IDefinition {

    @throws[Exception]
    abstract override def validate(node: Node, operation: String)(implicit ec: ExecutionContext): Future[Node] = {
        val relations = node.getAddedRelations
        if (CollectionUtils.isNotEmpty(relations)) {
            val ids = relations.asScala.map(r => List(r.getStartNodeId, r.getEndNodeId)).flatten
                    .filter(id => StringUtils.isNotBlank(id) && !StringUtils.equals(id, node.getIdentifier)).toList.distinct
            val relationNodes = NodeValidator.validate(node.getGraphId, ids.asJava)
            relationNodes.map(relNodes => {
                node.setNodeType(SystemNodeTypes.DATA_NODE.name)
                relNodes.put(node.getIdentifier, node)
                node.setRelationNodes(relNodes)
                node
            }).map(node => {
                super.validate(node, operation)
            }).flatMap(f => f)
            // TODO: behavior validation should be here.
        } else {
            super.validate(node, operation)
        }
    }

}
