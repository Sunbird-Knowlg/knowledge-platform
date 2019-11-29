package org.sunbird.utils

import java.util
import java.util.Map

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.commons.collections4.CollectionUtils
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.schema.DefinitionNode

import scala.collection.JavaConverters._

object NodeUtil {
    val mapper: ObjectMapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    def serialize(node: Node, fields: util.List[String], schemaName: String): util.Map[String, AnyRef] = {
        val metadataMap = node.getMetadata
        metadataMap.put("identifier", node.getIdentifier)
        if (CollectionUtils.isNotEmpty(fields))
            metadataMap.keySet.retainAll(fields)
        val jsonProps = DefinitionNode.fetchJsonProps(node.getGraphId, "1.0", schemaName)
        val updatedMetadataMap:util.Map[String, AnyRef] = metadataMap.entrySet().asScala.map((entry: util.Map.Entry[String, AnyRef]) => handleKeyNames(entry, fields) ->  convertJsonProperties(entry, jsonProps)).toMap.asJava
        val definitionMap = DefinitionNode.getRelationDefinitionMap(node.getGraphId, "1.0", schemaName).asJava
        val relMap:util.Map[String, util.List[util.Map[String, AnyRef]]] = getRelationMap(node, updatedMetadataMap, definitionMap)
        var finalMetadata = new util.HashMap[String, AnyRef]()
        finalMetadata.putAll(updatedMetadataMap)
        finalMetadata.putAll(relMap)
        finalMetadata
    }

    def handleKeyNames(entry: Map.Entry[String, AnyRef], fields: util.List[String]) = {
        if(CollectionUtils.isEmpty(fields)) {
            entry.getKey.substring(0,1) + entry.getKey.substring(1)
        } else {
            entry.getKey
        }
    }

    def getRelationMap(node: Node, updatedMetadataMap: util.Map[String, AnyRef], relationMap: util.Map[String, AnyRef]):util.Map[String, util.List[util.Map[String, AnyRef]]] = {
        val inRelations:util.List[Relation] = { if (CollectionUtils.isEmpty(node.getInRelations)) new util.ArrayList[Relation] else node.getInRelations }
        val outRelations:util.List[Relation] = { if (CollectionUtils.isEmpty(node.getOutRelations)) new util.ArrayList[Relation] else node.getOutRelations }
        val relMap = new util.HashMap[String, util.List[util.Map[String, AnyRef]]]
        for (rel <- inRelations.asScala) {
            if (relMap.containsKey(relationMap.get(rel.getRelationType + "_in_" + rel.getStartNodeObjectType))) relMap.get(relationMap.get(rel.getRelationType + "_in_" + rel.getStartNodeObjectType)).add(populateRelationMaps(rel, "in"))
            else relMap.put(relationMap.get(rel.getRelationType + "_in_" + rel.getStartNodeObjectType).asInstanceOf[String], new util.ArrayList[util.Map[String, AnyRef]]() {})
        }

        for (rel <- outRelations.asScala) {
            if (relMap.containsKey(relationMap.get(rel.getRelationType + "_out_" + rel.getEndNodeObjectType))) relMap.get(relationMap.get(rel.getRelationType + "_out_" + rel.getEndNodeObjectType)).add(populateRelationMaps(rel, "out"))
            else relMap.put(relationMap.get(rel.getRelationType + "_out_" + rel.getEndNodeObjectType).asInstanceOf[String], new util.ArrayList[util.Map[String, AnyRef]]() {})
        }
        relMap
    }
    def convertJsonProperties(entry: Map.Entry[String, AnyRef], jsonProps: scala.List[String]) = {
        if(jsonProps.contains(entry.getKey)) {
            try {mapper.readTree(entry.getValue.toString)}
            catch { case e: Exception => entry.getValue }
        }
        else entry.getValue
    }

    def populateRelationMaps(rel: Relation, direction: String): util.Map[String, AnyRef] = {
        if("out".equalsIgnoreCase(direction))
            new util.HashMap[String, Object]() {{
                put("identifier", rel.getEndNodeId.replace(".img", ""))
                put("name", rel.getEndNodeName)
                put("objectType", rel.getEndNodeObjectType.replace("Image", ""))
                put("relation", rel.getRelationType)
                put("description", rel.getEndNodeMetadata.get("description"))
                put("status", rel.getEndNodeMetadata.get("status"))
            }}
        else
            new util.HashMap[String, Object]() {{
                put("identifier", rel.getStartNodeId.replace(".img", ""))
                put("name", rel.getStartNodeName)
                put("objectType", rel.getStartNodeObjectType.replace("Image", ""))
                put("relation", rel.getRelationType)
                put("description", rel.getStartNodeMetadata.get("description"))
                put("status", rel.getStartNodeMetadata.get("status"))
            }}
    }
}