package org.sunbird.graph.schema

import java.util

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.enums.SystemNodeTypes
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.validator._

import scala.collection.JavaConverters._

class DefinitionDTO(graphId: String, schemaName: String, version: String = "1.0") extends BaseDefinitionNode(graphId, schemaName, version) with VersionKeyValidator with VersioningNode with RelationValidator with FrameworkValidator with PropAsEdgeValidator with SchemaValidator {

    def getOutRelationObjectTypes: List[String] = outRelationObjectTypes

    def getNode(identifier: String, input: java.util.Map[String, AnyRef], nodeType: String): Node = {
        val result = schemaValidator.getStructuredData(input)
        val objectType = schemaValidator.getConfig.getString("objectType")
        val node = new Node(identifier, objectType, nodeType)
        node.setGraphId(graphId)
        node.setNodeType(SystemNodeTypes.DATA_NODE.name)
        node.setObjectType(objectType)
        if (MapUtils.isNotEmpty(input)) node.setMetadata(result.getMetadata) else node.setMetadata(new util.HashMap[String, AnyRef]())
        if (StringUtils.isBlank(node.getIdentifier)) node.setIdentifier(Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp))
        setRelations(node, result.getRelations)
        if (MapUtils.isNotEmpty(result.getExternalData)) node.setExternalData(result.getExternalData) else node.setExternalData(new util.HashMap[String, AnyRef]())
        node
    }

    def getExternalProps(): List[String] = {
        if (schemaValidator.getConfig.hasPath("external.properties")) {
            val propsSet = Set.empty ++ schemaValidator.getConfig.getObject("external.properties").keySet().asScala
            (for (prop <- propsSet) yield prop) (collection.breakOut)
        }
        else
            List()
    }

    def fetchJsonProps(): List[String] = {
        val jsonProps = schemaValidator.getJsonProps.asScala
        jsonProps.toList
    }

    def getInRelations(): List[Map[String, AnyRef]] = {
        if (schemaValidator.getConfig.hasPath("relations"))
            schemaValidator.getConfig
                .getAnyRef("relations").asInstanceOf[java.util.HashMap[String, Object]].asScala
                .filter(e => StringUtils.equals(e._2.asInstanceOf[java.util.HashMap[String, Object]].get("direction").asInstanceOf[String], "in"))
                .map(e => Map(e._1 -> e._2)).toList
        else
            List()
    }

    def getOutRelations(): List[Map[String, AnyRef]] = {
        if (schemaValidator.getConfig.hasPath("relations")) {
            schemaValidator.getConfig
              .getAnyRef("relations").asInstanceOf[java.util.HashMap[String, Object]].asScala
              .filter(e => StringUtils.equals(e._2.asInstanceOf[java.util.HashMap[String, Object]].get("direction").asInstanceOf[String], "out"))
              .map(e => Map(e._1 -> e._2)).toList
        } else
            List()
    }

    def getRelationDefinitionMap(): Map[String, AnyRef] = {
        if (schemaValidator.getConfig.hasPath("relations"))
            schemaValidator.getConfig
              .getAnyRef("relations").asInstanceOf[java.util.HashMap[String, Object]].asScala
              .map(e => generateRelationKey(e)).flatten.toMap
        else
            Map()
    }

    def getRestrictPropsConfig(operation: String): List[String] = {
        if (schemaValidator.getConfig.hasPath("restrictProps")) {
            val restrictProps = schemaValidator.getConfig.getAnyRef("restrictProps")
                                    .asInstanceOf[java.util.HashMap[String, Object]].getOrDefault(operation, new util.ArrayList[String]()).asInstanceOf[java.util.ArrayList[String]]
            restrictProps.asScala.toList
        } else
            List()
    }

    def getEdgeKey(): String = {
        schemaValidator.getConfig.hasPath("edge.key") match {
            case true => schemaValidator.getConfig.getString("edge.key")
            case _ => ""
        }
    }

    def getRelationsMap(): java.util.HashMap[String, AnyRef] = {
        schemaValidator.getConfig
            .getAnyRef("relations").asInstanceOf[java.util.HashMap[String, AnyRef]]
    }

    private def generateRelationKey(relation: (String, Object)): Map[String, AnyRef] = {
        val relationMetadata = relation._2.asInstanceOf[java.util.HashMap[String, Object]]
        val objects = relationMetadata.get("objects").asInstanceOf[java.util.List[String]].asScala
        objects.flatMap(objectType => Map((relationMetadata.get("type").asInstanceOf[String] + "_" + relationMetadata.get("direction") + "_" + objectType) -> relation._1)).toMap
    }

}
