package org.sunbird.graph.path

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Node, Relation, SubGraph}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.{DefinitionFactory, DefinitionNode, ObjectCategoryDefinition}
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.graph.utils.NodeUtil.{convertJsonProperties, handleKeyNames}

import java.util
import java.util.{ArrayList, HashMap, Map}
import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, future}
import scala.util.Try

object DataSubGraph {


  def read(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[SubGraph] = {
    val identifier: String = request.get("identifier").asInstanceOf[String]
    val depth: Int = request.getOrDefault("depth", 5).asInstanceOf[Int]
    val subGraph: Future[SubGraph] = oec.graphService.getSubGraph(request.graphId, identifier, depth)
    subGraph
  }


  def readOld(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[SubGraph] = {
    val identifier: String = request.get("identifier").asInstanceOf[String]
    val depth: Int = request.getOrDefault("depth", 5).asInstanceOf[Int]
    oec.graphService.getSubGraph(request.graphId, identifier, depth)
  }

  def readSubGraph(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Unit]  = {
    val identifier: String = request.get("identifier").asInstanceOf[String]
    val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
    val schemaVersion = request.getContext.get("version").asInstanceOf[String]
    val definition = DefinitionFactory.getDefinition(request.getContext.get("graph_id").asInstanceOf[String], schemaName, schemaVersion)
    val getRelationsMapKeys =  definition.getRelationsMap().keySet()
    val dataMap = new util.HashMap[String, AnyRef]
    val relMap = new util.HashMap[String, AnyRef]
    readSubGraphData(request, dataMap, relMap).map(sub => {
      println("subGraphData out " + sub)
      sub
    })

//    subGraphData.map(sub => {
//      println("subGraphData in map " + sub)
//      sub
//    }).flatMap(f => Future(f))
  }

  private def readSubGraphData(request: Request, dataMap: util.HashMap[String, AnyRef], relMap: util.HashMap[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.HashMap[String, AnyRef]] = {
    val finalDataMap = new util.HashMap[String, AnyRef]
    val identifier: String = request.get("identifier").asInstanceOf[String]
    val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
    val schemaVersion = request.getContext.get("version").asInstanceOf[String]
    var isRoot = false
    if (isRoot != null && StringUtils.equalsAnyIgnoreCase(request.getContext.get("isRoot").asInstanceOf[String], "true"))
      isRoot = true
    val node: Future[Node] = oec.graphService.getNodeByUniqueId(request.graphId, identifier, false, request)
    node.map(n => {
      val newDataMap = new util.HashMap[String, AnyRef]
      newDataMap.put("metadata", n.getMetadata)
      newDataMap.put("outRelations", n.getOutRelations)
      finalDataMap.put(n.getIdentifier, newDataMap)
      finalDataMap
    })
    finalDataMap.map(entry => {
      val mapData = entry._2.asInstanceOf[java.util.Map[String, AnyRef]].asScala
      println("mapData  " + mapData.toString())
      val outRelations: util.List[Relation] = mapData.getOrElse("outRelations", new util.ArrayList[Relation]).asInstanceOf[util.List[Relation]]
      for (rel <- outRelations.asScala) {
        val subReq = new Request()
        val context = new util.HashMap[String, Object]()
        context.putAll(request.getContext)
        subReq.setContext(context)
        subReq.getContext.put("schemaName", rel.getEndNodeObjectType.toLowerCase())
        subReq.getContext.put("objectType", rel.getEndNodeObjectType)
        subReq.getContext.put("isRoot", "true")
        subReq.put("identifier", rel.getEndNodeId)
        println("readSubGraphData "+ readSubGraphData(subReq, dataMap, relMap))
      }
    })


//    node.map(n => {
//      val finalMetadata = serialize(n, null, schemaName, schemaVersion, isRoot)
////      if (!isRoot && !finalMetadata.isEmpty) {
////        dataMap.put(n.getIdentifier, finalMetadata)
////      } else {
////        relMap.put(n.getIdentifier, finalMetadata)
////        dataMap.put("relMap", relMap)
////      }
//
//      val outRelations: util.List[Relation] = {
//        if (CollectionUtils.isEmpty(n.getOutRelations)) new util.ArrayList[Relation] else n.getOutRelations
//      }
//      for (rel <- outRelations.asScala) {
//        val subReq = new Request()
//        val context = new util.HashMap[String, Object]()
//        context.putAll(request.getContext)
//        subReq.setContext(context)
//        subReq.getContext.put("schemaName", rel.getEndNodeObjectType.toLowerCase())
//        subReq.getContext.put("objectType", rel.getEndNodeObjectType)
//        subReq.getContext.put("isRoot", "true")
//        //          subReq.getContext.put("parentId", n.getIdentifier)
//        //          subReq.getContext.put("parentRelationsMap", definition.getRelationsMap().keySet())
//        subReq.put("identifier", rel.getEndNodeId)
//        val xyz = readSubGraphData(subReq, dataMap, relMap)
//        println("getIdentifier  ==== "+n.getIdentifier + " ==== rel.getEndNodeId ===== "+ rel.getEndNodeId)
//        println("xyz  " + xyz )
//      }
//      println(" finalDataMap =====  " + dataMap)
//      dataMap
//      //      n.getOutRelations.asScala.map(relation => {
//      //        if (StringUtils.equalsIgnoreCase(relation.getRelationType, "hasSequenceMember")) {
//      //          val subReq = new Request()
//      //          val context = new util.HashMap[String, Object]()
//      //          context.putAll(request.getContext)
//      //          subReq.setContext(context)
//      //          subReq.getContext.put("schemaName", relation.getEndNodeObjectType.toLowerCase())
//      //          subReq.getContext.put("objectType", relation.getEndNodeObjectType)
//      //          subReq.getContext.put("isRoot", "true")
//      //          //          subReq.getContext.put("parentId", n.getIdentifier)
//      //          //          subReq.getContext.put("parentRelationsMap", definition.getRelationsMap().keySet())
//      //          subReq.put("identifier", relation.getEndNodeId)
//      //          val subMetaData = readSubGraphData(subReq, dataMap, relMap)
//      //          println("subMetaData  " + subMetaData)
//      //          if (isRoot && !subMetaData.isEmpty) {
//      //            relMap.put(n.getIdentifier, subMetaData)
//      //            finalDataMap.put("relMap", relMap)
//      //          }
//      //        }
//      //        println(" finalDataMap =====  "+ finalDataMap)
//      //        finalDataMap
//      //      })
//    })
    Future{finalDataMap}
  }

  private def getRelationData(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[util.HashMap[String, AnyRef]] = {
    val relMap = new util.HashMap[String, AnyRef]
    val identifier: String = request.get("identifier").asInstanceOf[String]
    val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
    val schemaVersion = request.getContext.get("version").asInstanceOf[String]
    var isRoot = false
    if (isRoot != null && StringUtils.equalsAnyIgnoreCase(request.getContext.get("isRoot").asInstanceOf[String], "true"))
      isRoot = true
    val node: Future[Node] = oec.graphService.getNodeByUniqueId(request.graphId, identifier, false, request)
    node.map(n => {
      val finalMetadata = serialize(n, null, schemaName, schemaVersion, isRoot)
      relMap.put(n.getIdentifier, finalMetadata)
      relMap
    })
  }

  private def serialize(node: Node, fields: util.List[String], schemaName: String, schemaVersion: String, isRoot: Boolean = false)(implicit oec: OntologyEngineContext, ec: ExecutionContext): util.Map[String, AnyRef] = {
    val metadataMap = node.getMetadata
    val objectCategoryDefinition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition(node.getMetadata.getOrDefault("primaryCategory", "").asInstanceOf[String], node.getObjectType.toLowerCase().replace("image", ""), node.getMetadata.getOrDefault("channel", "all").asInstanceOf[String])
    val jsonProps = DefinitionNode.fetchJsonProps(node.getGraphId, schemaVersion, node.getObjectType.toLowerCase().replace("image", ""), objectCategoryDefinition)
    val updatedMetadataMap: util.Map[String, AnyRef] = metadataMap.entrySet().asScala.filter(entry => null != entry.getValue).map((entry: util.Map.Entry[String, AnyRef]) => handleKeyNames(entry, fields) -> convertJsonProperties(entry, jsonProps)).toMap.asJava
    val definitionMap = DefinitionNode.getRelationDefinitionMap(node.getGraphId, schemaVersion, node.getObjectType.toLowerCase().replace("image", ""), objectCategoryDefinition).asJava
    val finalMetadata = new util.HashMap[String, AnyRef]()
    if(!isRoot) {
      finalMetadata.put("objectType", node.getObjectType)
      finalMetadata.putAll(updatedMetadataMap)
      if (CollectionUtils.isNotEmpty(fields))
        finalMetadata.keySet.retainAll(fields)
      finalMetadata.put("identifier", node.getIdentifier)
    }
    println("definitionMap  "+ definitionMap)
    val relMap: util.Map[String, util.List[util.Map[String, AnyRef]]] = geOutRelationMap(node, updatedMetadataMap, definitionMap)
    finalMetadata.putAll(relMap)
    finalMetadata
  }


  private def geOutRelationMap(node: Node, updatedMetadataMap: util.Map[String, AnyRef], relationMap: util.Map[String, AnyRef]): util.Map[String, util.List[util.Map[String, AnyRef]]] = {
    val outRelations: util.List[Relation] = {
      if (CollectionUtils.isEmpty(node.getOutRelations)) new util.ArrayList[Relation] else node.getOutRelations
    }
    val relMap = new util.HashMap[String, util.List[util.Map[String, AnyRef]]]
    for (rel <- outRelations.asScala) {
      val relKey: String = rel.getRelationType + "_out_" + rel.getEndNodeObjectType
      if (relMap.containsKey(relationMap.get(relKey))) relMap.get(relationMap.get(relKey)).add(NodeUtil.populateRelationMaps(rel, "out"))
      else {
        if (null != relationMap.get(relKey)) {
          relMap.put(relationMap.get(relKey).asInstanceOf[String], new util.ArrayList[util.Map[String, AnyRef]]() {
            add(NodeUtil.populateRelationMaps(rel, "out"))
          })
        }
      }
    }
    relMap
  }

}
