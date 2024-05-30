package org.sunbird.janus.dac.util

import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.structure.{Edge, Vertex}
import org.sunbird.common.exception.ServerException
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.enums.GraphDACErrorCodes
import org.sunbird.graph.dac.model.Relation

import java.{lang, util}
import java.util.{HashMap, List, Map}

class GremlinEdgeUtil {
  def getRelation(graphId: String, edge: Edge, startNodeMap: util.Map[Object, AnyRef], endNodeMap: util.Map[Object, AnyRef]): Relation = {

    if (null == edge)
      throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_REL.name, "Failed to create relation object. Relation from database is null.")

    val relation: Relation = new Relation
    relation.setGraphId(graphId)
    relation.setId(edge.id.asInstanceOf[Long])

    val startNode = startNodeMap.get(edge.outVertex.id).asInstanceOf[Vertex]
    val endNode = endNodeMap.get(edge.inVertex.id).asInstanceOf[Vertex]

    relation.setStartNodeId(startNode.property(SystemProperties.IL_UNIQUE_ID.name).value.toString)
    relation.setEndNodeId(endNode.property(SystemProperties.IL_UNIQUE_ID.name).value.toString)
    relation.setStartNodeName(getName(startNode))
    relation.setEndNodeName(getName(endNode))
    relation.setStartNodeType(getNodeType(startNode))
    relation.setEndNodeType(getNodeType(endNode))
    relation.setStartNodeObjectType(getObjectType(startNode))
    relation.setEndNodeObjectType(getObjectType(endNode))
    relation.setRelationType(edge.label())
    relation.setStartNodeMetadata(getNodeMetadata(startNode))
    relation.setEndNodeMetadata(getNodeMetadata(endNode))
    val metadata = new util.HashMap[String, Object]

    edge.keys.forEach((key: String) => {
      val value = edge.value(key)
      if (null != value) if (value.isInstanceOf[util.List[_]]) {
        val list = value.asInstanceOf[util.List[AnyRef]]
        if (!list.isEmpty) {
          val obj = list.get(0)
          obj match {
            case _: String => metadata.put(key, list.toArray(new Array[String](list.size())))
            case _: Number => metadata.put(key, list.toArray(new Array[Number](list.size())))
            case _: lang.Boolean => metadata.put(key, list.toArray(new Array[lang.Boolean](list.size())))
            case _ => metadata.put(key, list.toArray(new Array[AnyRef](list.size())))
          }
        }
      }
      else metadata.put(key, value)
    })

    relation.setMetadata(metadata)
    relation
  }

  private def getName(vertex: Vertex) = {
    var name = vertex.property("name").asInstanceOf[String]
    if (StringUtils.isBlank(name) || StringUtils.equalsIgnoreCase("null", name)) {
      name = vertex.property("title").asInstanceOf[String]
      if (StringUtils.isBlank(name) || StringUtils.equalsIgnoreCase("null", name)) {
        name = vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name).asInstanceOf[String]
        if (StringUtils.isBlank(name) || StringUtils.equalsIgnoreCase("null", name))
          name = vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name).asInstanceOf[String]
      }
    }
    name
  }


  private def getNodeType(vertex: Vertex) = vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name).value.toString

  private def getObjectType(vertex: Vertex) = vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name).value.toString

  private def getNodeMetadata(vertex: Vertex) = {
    val metadata = new util.HashMap[String, AnyRef]
    if (vertex != null) vertex.keys.forEach((key: String) => {
      val value = vertex.value(key)
      if (null != value) if (value.isInstanceOf[util.List[_]]) {
        val list = value.asInstanceOf[util.List[_]]
        if (!list.isEmpty) {
          val firstElement = list.get(0)
          firstElement match {
            case _: String => metadata.put(key, list.toArray(new Array[String](list.size())))
            case _: Number => metadata.put(key, list.toArray(new Array[Number](list.size())))
            case _: lang.Boolean => metadata.put(key, list.toArray(new Array[lang.Boolean](list.size())))
            case _ => metadata.put(key, list.toArray(new Array[AnyRef](list.size())))
          }
        }
      }
      else metadata.put(key, value)

    })
    metadata
  }

}
