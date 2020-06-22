package org.sunbird.graph.utils

import java.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.schema.DefinitionNode

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object NodeUtil {
    val mapper: ObjectMapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    def serialize(node: Node, fields: util.List[String], schemaName: String, schemaVersion: String, withoutRelations: Boolean = false): util.Map[String, AnyRef] = {
        val metadataMap = node.getMetadata
        val jsonProps = DefinitionNode.fetchJsonProps(node.getGraphId, schemaVersion, schemaName)
        val updatedMetadataMap:util.Map[String, AnyRef] = metadataMap.entrySet().asScala.filter(entry => null != entry.getValue).map((entry: util.Map.Entry[String, AnyRef]) => handleKeyNames(entry, fields) ->  convertJsonProperties(entry, jsonProps)).toMap.asJava
        val definitionMap = DefinitionNode.getRelationDefinitionMap(node.getGraphId, schemaVersion, schemaName).asJava
        val finalMetadata = new util.HashMap[String, AnyRef]()
        finalMetadata.put("objectType",node.getObjectType)
        finalMetadata.putAll(updatedMetadataMap)
        if(!withoutRelations){
            val relMap:util.Map[String, util.List[util.Map[String, AnyRef]]] = getRelationMap(node, updatedMetadataMap, definitionMap)
            finalMetadata.putAll(relMap)
        }
        if (CollectionUtils.isNotEmpty(fields))
            finalMetadata.keySet.retainAll(fields)
        finalMetadata.put("identifier", node.getIdentifier)
        finalMetadata.put("languageCode", getLanguageCodes(node))
        finalMetadata
    }


    def setRelation(node: Node, nodeMap: util.Map[String, AnyRef], relationMap: util.Map[String, AnyRef]) = {
        val inRelations: util.List[Relation] = new util.ArrayList[Relation]()
        val outRelations: util.List[Relation] = new util.ArrayList[Relation]()
        relationMap.asScala.foreach(entry => {
            if(nodeMap.containsKey(entry._1) && null != nodeMap.get(entry._1) && !nodeMap.get(entry._1).asInstanceOf[util.List[util.Map[String, AnyRef]]].isEmpty) {
                nodeMap.get(entry._1).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.map(relMap => {
                    if("in".equalsIgnoreCase(entry._2.asInstanceOf[util.Map[String, AnyRef]].get("direction").asInstanceOf[String])) {
                        val rel:Relation = new Relation(relMap.get("identifier").asInstanceOf[String], entry._2.asInstanceOf[util.Map[String, AnyRef]].get("type").asInstanceOf[String], node.getIdentifier)
                        rel.setStartNodeObjectType(relMap.get("objectType").asInstanceOf[String])
                        rel.setEndNodeObjectType(node.getObjectType)
                        rel.setStartNodeName(relMap.get("name").asInstanceOf[String])
                        rel.setStartNodeMetadata(new util.HashMap[String, AnyRef](){{
                            put("description", relMap.get("description"))
                            put("status", relMap.get("status"))
                        }})
                        if(null != relMap.get("index") && 0 < relMap.get("index").asInstanceOf[Integer]){
                            rel.setMetadata(new util.HashMap[String, AnyRef](){{
                                put(SystemProperties.IL_SEQUENCE_INDEX.name(), relMap.get("index"))
                            }})
                        }
                        inRelations.add(rel)
                    } else {
                        val rel:Relation = new Relation(node.getIdentifier, entry._2.asInstanceOf[util.Map[String, AnyRef]].get("type").asInstanceOf[String], relMap.get("identifier").asInstanceOf[String])
                        rel.setStartNodeObjectType(node.getObjectType)
                        rel.setEndNodeObjectType(relMap.get("objectType").asInstanceOf[String])
                        rel.setEndNodeName(relMap.get("name").asInstanceOf[String])
                        rel.setEndNodeMetadata(new util.HashMap[String, AnyRef]() {{
                            put("description", relMap.get("description"))
                            put("status", relMap.get("status"))
                        }})
                        if(null != relMap.get("index") && 0 < relMap.get("index").asInstanceOf[Integer]){
                            rel.setMetadata(new util.HashMap[String, AnyRef](){{
                                put(SystemProperties.IL_SEQUENCE_INDEX.name(), relMap.get("index"))
                            }})
                        }
                        outRelations.add(rel)
                    }
                })
            }
        })
        node.setInRelations(inRelations)
        node.setOutRelations(outRelations)
    }

    def deserialize(nodeMap: util.Map[String, AnyRef], schemaName: String, relationMap:util.Map[String, AnyRef]): Node = {
        val node: Node = new Node()
        if(MapUtils.isNotEmpty(nodeMap)) {
            node.setIdentifier(nodeMap.get("identifier").asInstanceOf[String])
            node.setObjectType(nodeMap.get("objectType").asInstanceOf[String])
            val filteredMetadata: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](JavaConverters.mapAsJavaMapConverter(nodeMap.asScala.filterNot(entry => relationMap.containsKey(entry._1)).toMap).asJava)
            node.setMetadata(filteredMetadata)
            setRelation(node, nodeMap, relationMap)
        }
        node.getMetadata.asScala.map(entry => {
            if(entry._2.isInstanceOf[::[AnyRef]]) (entry._1 -> entry._2.asInstanceOf[::[AnyRef]].toArray.toList)
            else entry
        })
        node
    }


    def handleKeyNames(entry: util.Map.Entry[String, AnyRef], fields: util.List[String]) = {
        if(CollectionUtils.isEmpty(fields)) {
            entry.getKey.substring(0,1).toLowerCase + entry.getKey.substring(1)
        } else {
            entry.getKey
        }
    }

    def getRelationMap(node: Node, updatedMetadataMap: util.Map[String, AnyRef], relationMap: util.Map[String, AnyRef]):util.Map[String, util.List[util.Map[String, AnyRef]]] = {
        val inRelations:util.List[Relation] = { if (CollectionUtils.isEmpty(node.getInRelations)) new util.ArrayList[Relation] else node.getInRelations }
        val outRelations:util.List[Relation] = { if (CollectionUtils.isEmpty(node.getOutRelations)) new util.ArrayList[Relation] else node.getOutRelations }
        val relMap = new util.HashMap[String, util.List[util.Map[String, AnyRef]]]
        for (rel <- inRelations.asScala) {
            val relKey:String = rel.getRelationType + "_in_" + rel.getEndNodeObjectType
            if (relMap.containsKey(relationMap.get(relKey))) relMap.get(relationMap.get(relKey)).add(populateRelationMaps(rel, "in"))
            else {
                if(null != relationMap.get(relKey)) {
                    relMap.put(relationMap.get(relKey).asInstanceOf[String], new util.ArrayList[util.Map[String, AnyRef]]() {add(populateRelationMaps(rel, "in"))})
                }
            }
        }
        for (rel <- outRelations.asScala) {
            val relKey:String = rel.getRelationType + "_out_" + rel.getEndNodeObjectType
            if (relMap.containsKey(relationMap.get(relKey))) relMap.get(relationMap.get(relKey)).add(populateRelationMaps(rel, "out"))
            else {
                if(null != relationMap.get(relKey)) {
                    relMap.put(relationMap.get(relKey).asInstanceOf[String], new util.ArrayList[util.Map[String, AnyRef]]() {add(populateRelationMaps(rel, "out"))})
                }
            }
        }
        relMap
    }
    
    def convertJsonProperties(entry: util.Map.Entry[String, AnyRef], jsonProps: scala.List[String]) = {
        if(jsonProps.contains(entry.getKey)) {
            try {JsonUtils.deserialize(entry.getValue.asInstanceOf[String], classOf[Object])} //.readTree(entry.getValue.toString)}
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

    def getLanguageCodes(node: Node): util.List[String] = {
        val value = node.getMetadata.get("language")
        val languages:util.List[String] = value match {
            case value: String => List(value).asJava
            case value: util.List[String] => value
            case value: Array[String] => value.filter((lng: String) => StringUtils.isNotBlank(lng)).toList.asJava
            case _ => new util.ArrayList[String]()
        }
        if(CollectionUtils.isNotEmpty(languages)){
            JavaConverters.bufferAsJavaListConverter(languages.asScala.map(lang => if(Platform.config.hasPath("languageCode." + lang.toLowerCase)) Platform.config.getString("languageCode." + lang.toLowerCase) else "")).asJava
        }else{
            languages
        }
    }

    def isRetired(node: Node): Boolean = StringUtils.equalsIgnoreCase(node.getMetadata.get("status").asInstanceOf[String], "Retired")

}
