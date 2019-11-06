package org.sunbird.graph.schema

import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.validator._

class DefinitionDTO(graphId: String, objectType: String, version: String = "1.0") extends BaseDefinitionNode(graphId , objectType, version) with VersionKeyValidator with VersioningNode with RelationValidator with FrameworkValidator with SchemaValidator {

    def getOutRelationObjectTypes: List[String] = outRelationObjectTypes

    def getNode(identifier: String, input: java.util.Map[String, AnyRef]): Node = { // TODO: used for update operations.
        null
    }
}
