package org.sunbird.janus.dac.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.graph.dac.model.{Edges, Vertex}
import org.sunbird.common.exception.ServerException
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.enums.GraphDACErrorCodes

import java.{lang, util}

class GremlinVertexUtil {

  def getNode(graphId: String, gremlinVertex: org.apache.tinkerpop.gremlin.structure.Vertex, edgeMap: util.Map[Object, AnyRef],
              startNodeMap: util.Map[Object, AnyRef], endNodeMap: util.Map[Object, AnyRef]): Vertex = {
    if (null == gremlinVertex)
      throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
        "Failed to create node object. Node from database is null.")

    val vertex: Vertex = new Vertex()
    vertex.setGraphId(graphId)
    vertex.setId(gremlinVertex.id())

    val metadata = new util.HashMap[String, Object]()
    gremlinVertex.keys().forEach { key =>
      if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name()))
        vertex.setIdentifier(gremlinVertex.values(key).next().asInstanceOf[String])
      else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name()))
        vertex.setVertexType(gremlinVertex.values(key).next().asInstanceOf[String])
      else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name()))
        vertex.setObjectType(gremlinVertex.values(key).next().asInstanceOf[String])
      else {
        val value = gremlinVertex.values(key)
        if (null != value) {
          value match {
            case list: util.List[_] =>
              if (null != list && list.size() > 0) {

                val obj = list.get(0)
                obj match {
                  case _: String => metadata.put(key, list.toArray(new Array[String](list.size())))
                  case _: Number => metadata.put(key, list.toArray(new Array[Number](list.size())))
                  case _: lang.Boolean => metadata.put(key, list.toArray(new Array[lang.Boolean](list.size())))
                  case _ => metadata.put(key, list.toArray(new Array[AnyRef](list.size())))
                }

              }
            case _ => metadata.put(key, value.next())
          }
        }
      }
    }
    vertex.setMetadata(metadata)

    if (null != edgeMap && !edgeMap.isEmpty && null != startNodeMap && !startNodeMap.isEmpty && null != endNodeMap && !endNodeMap.isEmpty) {
      val inEdges = new util.ArrayList[Edges]()
      val outEdges = new util.ArrayList[Edges]()

      edgeMap.forEach { (id, rel) =>
        val edge = rel.asInstanceOf[org.apache.tinkerpop.gremlin.structure.Edge]
        if (edge.inVertex().id() == gremlinVertex.id()) {
          outEdges.add(new Edges(graphId, edge, startNodeMap, endNodeMap))
        }
        if (edge.outVertex().id() == gremlinVertex.id()) {
          inEdges.add(new Edges(graphId, edge, startNodeMap, endNodeMap))
        }
      }
      vertex.setInEdges(inEdges)
      vertex.setOutEdges(outEdges)
    }

    vertex
  }
}
