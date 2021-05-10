package org.sunbird.graph.schema

import java.util

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.dac.enums.SystemNodeTypes
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.validator._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class DefinitionDTO(graphId: String, schemaName: String, version: String = "1.0", ocd: ObjectCategoryDefinition = ObjectCategoryDefinition())(implicit ec: ExecutionContext, oec: OntologyEngineContext) extends BaseDefinitionNode(graphId, schemaName, version, ocd) with VersionKeyValidator with VersioningNode with RelationValidator with FrameworkValidator with PropAsEdgeValidator with SchemaValidator {

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

    def getAllCopySchemes(): List[String] = schemaValidator.getConfig.hasPath("copy.scheme") match {
        case true => val copySchemeSet = Set.empty ++ schemaValidator.getConfig.getObject("copy.scheme").keySet().asScala
            (for (prop <- copySchemeSet) yield prop) (collection.breakOut)
        case false => List()
    }

    def getCopySchemeMap(request: Request): java.util.HashMap[String, Object] =
        (StringUtils.isNotEmpty(request.getContext.getOrDefault("copyScheme", "").asInstanceOf[String])
            && schemaValidator.getConfig.hasPath("copy.scheme" + "." + request.getContext.get("copyScheme"))) match {
            case true => schemaValidator.getConfig.getAnyRef("copy.scheme" + "." + request.getContext.get("copyScheme")).asInstanceOf[java.util.HashMap[String, Object]]
            case false => new java.util.HashMap[String, Object]()
        }

    private def generateRelationKey(relation: (String, Object)): Map[String, AnyRef] = {
        val relationMetadata = relation._2.asInstanceOf[java.util.HashMap[String, Object]]
        val objects = relationMetadata.get("objects").asInstanceOf[java.util.List[String]].asScala
        objects.flatMap(objectType => Map((relationMetadata.get("type").asInstanceOf[String] + "_" + relationMetadata.get("direction") + "_" + objectType) -> relation._1)).toMap
    }
    
    def validateRequest(request: Request) = {
        if(schemaValidator.getConfig.hasPath("schema_restrict_api") && schemaValidator.getConfig.getBoolean("schema_restrict_api")){
            val propsList: List[String] = schemaValidator.getAllProps.asScala.toList
            //Todo:: Remove this after v4 apis
            val invalidProps: List[String] = request.getRequest.keySet().asScala.toList.filterNot(key => propsList.contains(key) || StringUtils.endsWith(key, "batch_count"))

            if(null != invalidProps && !invalidProps.isEmpty)
                throw new ClientException(ResponseCode.CLIENT_ERROR.name, "Invalid request", java.util.Arrays.asList("Invalid Props are : " + invalidProps.asJavaCollection))
        }
    }

}
