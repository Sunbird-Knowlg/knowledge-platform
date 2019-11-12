package org.sunbird.graph.schema

import java.util

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.enums.SystemNodeTypes
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.validator._

class DefinitionDTO(graphId: String, objectType: String, version: String = "1.0") extends BaseDefinitionNode(graphId , objectType, version) with VersionKeyValidator with VersioningNode with RelationValidator with FrameworkValidator with SchemaValidator {

    def getOutRelationObjectTypes: List[String] = outRelationObjectTypes

    def getNode(identifier: String, input: java.util.Map[String, AnyRef], nodeType: String): Node = {
        val result = schemaValidator.getStructuredData(input)
        val node = new Node(identifier, objectType, nodeType)
        node.setGraphId(graphId)
        node.setNodeType(SystemNodeTypes.DATA_NODE.name)
        node.setObjectType(objectType)
        if (MapUtils.isNotEmpty(input)) node.setMetadata(input) else node.setMetadata(new util.HashMap[String, AnyRef]())
        if (StringUtils.isBlank(node.getIdentifier)) node.setIdentifier(Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp))
        setRelations(node, result.getRelations)
        if (MapUtils.isNotEmpty(result.getExternalData)) node.setExternalData(result.getExternalData) else node.setExternalData(new util.HashMap[String, AnyRef]())
        node
    }
}
