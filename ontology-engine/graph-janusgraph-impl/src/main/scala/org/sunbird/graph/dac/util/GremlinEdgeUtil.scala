package org.sunbird.graph.dac.util

import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.structure.{Edge, Vertex}
import org.sunbird.common.exception.ServerException
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.enums.GraphDACErrorCodes
import org.sunbird.graph.dac.model.Relation
import org.sunbird.graph.util.ScalaJsonUtil
import java.{lang, util}

class GremlinEdgeUtil {
  def getRelation(graphId: String, edge: Edge, startNodeMap: util.Map[Object, AnyRef], endNodeMap: util.Map[Object, AnyRef], propTypeMap: Option[Map[String, AnyRef]] = None): Relation = {

    if (null == edge)
      throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_REL.name, "Failed to create relation object. Relation from database is null.")

    val relation: Relation = new Relation
    relation.setGraphId(graphId)
//    val vertexIdAsLong: Long = edge.id match {
//      case id: Number => id.longValue()
//      case id: String => id.toLong
//    }
//    relation.setId(edge.id.asInstanceOf[Long])

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
    relation.setStartNodeMetadata(getNodeMetadata(startNode, propTypeMap))
    relation.setEndNodeMetadata(getNodeMetadata(endNode, propTypeMap))
    val metadata = new util.HashMap[String, Object]

    edge.keys.forEach((key: String) => {
      val value: AnyRef = edge.values(key).next()
      if (propTypeMap.exists(_.get(key).contains("array"))) {
        val listValue: util.List[AnyRef] = ScalaJsonUtil.deserialize[util.List[AnyRef]](value.toString)
        if (listValue != null && listValue.size() > 0) {
          val obj = listValue.get(0)
          obj match {
            case _: String => metadata.put(key, listValue.toArray(new Array[String](0)))
            case _: Number => metadata.put(key, listValue.toArray(new Array[Number](0)))
            case _: lang.Boolean => metadata.put(key, listValue.toArray(new Array[lang.Boolean](0)))
            case _ => metadata.put(key, listValue.toArray(new Array[AnyRef](0)))
          }
        }
      }
      else metadata.put(key, value)
    })
    relation.setMetadata(metadata)
    relation
  }

  private def getName(vertex: Vertex) = {
    var name = vertex.property("name").value.asInstanceOf[String]
    if (StringUtils.isBlank(name) || StringUtils.equalsIgnoreCase("null", name)) {
      name = vertex.property("title").value.asInstanceOf[String]
      if (StringUtils.isBlank(name) || StringUtils.equalsIgnoreCase("null", name)) {
        name = vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name).value.asInstanceOf[String]
        if (StringUtils.isBlank(name) || StringUtils.equalsIgnoreCase("null", name))
          name = vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name).value.asInstanceOf[String]
      }
    }
    name
  }


  private def getNodeType(vertex: Vertex) = vertex.property(SystemProperties.IL_SYS_NODE_TYPE.name).value.toString

  private def getObjectType(vertex: Vertex) = vertex.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name).value.toString

  private def getNodeMetadata(vertex: Vertex, propTypeMap: Option[Map[String, AnyRef]] = None) = {
    val metadata = new util.HashMap[String, AnyRef]
      if (vertex != null) {
        vertex.keys.forEach((key: String) => {
          val value: AnyRef = vertex.values(key).next()
          if (propTypeMap.exists(_.get(key).contains("array"))) {
            val listValue: util.List[AnyRef] = ScalaJsonUtil.deserialize[util.List[AnyRef]](value.toString)
            if (listValue != null && listValue.size() > 0) {
              println("list in If --->" + listValue)
              val obj = listValue.get(0)
              obj match {
                case _: String => metadata.put(key, listValue.toArray(new Array[String](0)))
                case _: Number => metadata.put(key, listValue.toArray(new Array[Number](0)))
                case _: lang.Boolean => metadata.put(key, listValue.toArray(new Array[lang.Boolean](0)))
                case _ => metadata.put(key, listValue.toArray(new Array[AnyRef](0)))
              }
            }
          }
          else metadata.put(key, value)
        })
      }
    metadata
  }

}
