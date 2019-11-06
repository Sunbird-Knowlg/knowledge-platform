package org.sunbird.graph.schema

import org.sunbird.graph.dac.model.{Node, Relation}
import java.util
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.enums.SystemNodeTypes
import org.sunbird.graph.schema.validator._

import scala.collection.JavaConverters._

class DefinitionDTO(graphId: String, objectType: String, version: String = "1.0") extends BaseDefinitionNode(graphId , objectType, version) with VersionKeyValidator with VersioningNode with RelationValidator with FrameworkValidator with SchemaValidator {

    def getOutRelationObjectTypes: List[String] = outRelationObjectTypes

    def getNode(identifier: String, input: java.util.Map[String, AnyRef]): Node = { // TODO: used for update operations.
        null
    }

    def getExternalProps(): List[String] = {
        if (schemaValidator.getConfig.hasPath("externalProperties")) {
            val propsSet = Set.empty ++ schemaValidator.getConfig.getObject("externalProperties").keySet().asScala
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

    private def generateRelationKey(relation: (String, Object)): Map[String, AnyRef] = {
        val relationMetadata = relation._2.asInstanceOf[java.util.HashMap[String, Object]]
        val objects = relationMetadata.get("objects").asInstanceOf[java.util.List[String]].asScala
        objects.flatMap(objectType => Map((relationMetadata.get("type").asInstanceOf[String] + "_" + relationMetadata.get("direction") + "_" + objectType) -> relation._1)).toMap
    }
}
