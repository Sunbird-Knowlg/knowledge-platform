package org.sunbird.graph.dac.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.common.exception.ServerException
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.enums.GraphDACErrorCodes
import org.sunbird.graph.util.ScalaJsonUtil
import org.apache.tinkerpop.gremlin.structure.{Edge, Vertex}

import java.{lang, util}

object GremlinVertexUtil {

  def getNode(graphId: String, gremlinVertex: Vertex, edgeMap: util.Map[AnyRef, AnyRef], startNodeMap: util.Map[AnyRef, AnyRef], endNodeMap: util.Map[AnyRef, AnyRef], propTypeMap: Option[Map[String, AnyRef]] = None): Node = {
    if (null == gremlinVertex)
      throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
        "Failed to create node object. Node from database is null.")

    val node: Node = new Node()
    node.setGraphId(graphId)
    node.setId(gremlinVertex.id().asInstanceOf[Long])
    val metadata = new util.HashMap[String, AnyRef]()

    gremlinVertex.keys().forEach { key =>
      if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name())) {
        node.setIdentifier(gremlinVertex.values(key).next().asInstanceOf[String])
      }
      else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name())) {
        node.setNodeType(gremlinVertex.values(key).next().asInstanceOf[String])
      }
      else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name())) {
        node.setObjectType(gremlinVertex.values(key).next().asInstanceOf[String])
      }
      else {
          val value: AnyRef = gremlinVertex.values(key).next()
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
      }
    }
    node.setMetadata(metadata)

    if (null != edgeMap && !edgeMap.isEmpty && null != startNodeMap && !startNodeMap.isEmpty && null != endNodeMap && !endNodeMap.isEmpty) {
      val inEdges = new util.ArrayList[Relation]()
      val outEdges = new util.ArrayList[Relation]()

      edgeMap.forEach { (id, rel) =>
        val edge = rel.asInstanceOf[Edge]
        if (edge.inVertex().id() == gremlinVertex.id()) {
          outEdges.add(GremlinEdgeUtil.getRelation(graphId, edge, startNodeMap, endNodeMap))
        }
        if (edge.outVertex().id() == gremlinVertex.id()) {
          inEdges.add(GremlinEdgeUtil.getRelation(graphId, edge, startNodeMap, endNodeMap))
        }
      }
      node.setInRelations(inEdges)
      node.setOutRelations(outEdges)
    }

    node
  }
}
