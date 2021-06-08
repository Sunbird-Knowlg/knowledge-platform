package org.sunbird.graph.schema.validator


import java.util
import java.util.concurrent.CompletionException

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.enums.SystemNodeTypes
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.relations.{IRelation, RelationHandler}
import org.sunbird.graph.schema.IDefinition
import org.sunbird.graph.validator.NodeValidator

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

trait RelationValidator extends IDefinition {

    @throws[Exception]
    abstract override def validate(node: Node, operation: String, setDefaultValue: Boolean)(implicit ec: ExecutionContext, oec:OntologyEngineContext): Future[Node] = {
        val relations: java.util.List[Relation] = node.getAddedRelations
        if (CollectionUtils.isNotEmpty(relations)) {
            val ids = relations.asScala.map(r => List(r.getStartNodeId, r.getEndNodeId)).flatten
                    .filter(id => StringUtils.isNotBlank(id) && !StringUtils.equals(id, node.getIdentifier)).toList.distinct
            val relationNodes = NodeValidator.validate(node.getGraphId, ids.asJava)
            relationNodes.map(relNodes => {
                node.setNodeType(SystemNodeTypes.DATA_NODE.name)
                relNodes.put(node.getIdentifier, node)
                node.setRelationNodes(relNodes)
                //Behaviour Validation
                relations.asScala.map(relNode => {
                    val iRel:IRelation = RelationHandler.getRelation(node.getGraphId, node.getRelationNode(relNode.getStartNodeId),
                        relNode.getRelationType, node.getRelationNode(relNode.getEndNodeId), relNode.getMetadata)
                    val req = new Request()
                    req.setContext(new util.HashMap[String, AnyRef]() {{
                        put("schemaName", getSchemaName())
                        put("version", getSchemaVersion())
                    }})
                    val errList = iRel.validate(req)
                    if (null != errList && !errList.isEmpty) {
                        throw new ClientException(ResponseCode.CLIENT_ERROR.name, "Error while validating relations :: " + errList)
                    }
                })
                node
            }).map(node => {
                super.validate(node, operation)
            }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause}
        } else {
            super.validate(node, operation)
        }
    }

}
