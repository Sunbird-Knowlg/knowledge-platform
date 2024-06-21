package org.sunbird.graph.service.operation

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.graph.dac.util.GremlinVertexUtil
import org.sunbird.graph.dac.model.{Node, SearchCriteria}
import org.apache.tinkerpop.gremlin.structure.Edge
import org.sunbird.common.dto.{Property, Request}
import org.sunbird.common.exception.{ClientException, MiddlewareException, ResourceNotFoundException, ServerException}
import org.sunbird.graph.common.enums.{GraphDACParams, SystemProperties}
import org.sunbird.graph.service.common.{CypherQueryConfigurationConstants, DACErrorCodeConstants, DACErrorMessageConstants, GraphOperation}
import org.sunbird.graph.service.util.{ClientUtil, SearchUtil}
import org.sunbird.telemetry.logger.TelemetryManager
import org.apache.tinkerpop.gremlin.driver.Result
import org.apache.tinkerpop.gremlin.driver.ResultSet

import java.util
import scala.concurrent.{Await, ExecutionContext, Future}
import ExecutionContext.Implicits.global
import java.lang.Boolean
import scala.collection.JavaConversions._


class SearchOperations {

  val gremlinVertexUtil = new GremlinVertexUtil

  def getNodeByUniqueId(graphId: String, vertexId: String, getTags: Boolean, request: Request, propTypeMap: Option[Map[String, AnyRef]]): Future[Node] = {
    Future {
      TelemetryManager.log("Graph Id: " + graphId + "\nVertex Id: " + vertexId + "\nGet Tags:" + getTags)

      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node By Unique Id' Operation Failed.]")

      if (StringUtils.isBlank(vertexId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node By Unique Id' Operation Failed.]")

      TelemetryManager.log("Driver Initialised. | [Graph Id: " + graphId + "]")
      try {
        val parameterMap = new util.HashMap[String, AnyRef]
        parameterMap.put(GraphDACParams.graphId.name, graphId)
        parameterMap.put(GraphDACParams.nodeId.name, vertexId)
        parameterMap.put(GraphDACParams.getTags.name, getTags.asInstanceOf[java.lang.Boolean])
        parameterMap.put(GraphDACParams.request.name, request)

        val retrievedVertices = getVertexByUniqueId(parameterMap)
        var newVertex: Node = null
        if (CollectionUtils.isEmpty(retrievedVertices))
          throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name,
            DACErrorMessageConstants.NODE_NOT_FOUND + " | [Invalid Node Id.]: " + vertexId, vertexId)

        val vertexMap = new util.HashMap[Object, AnyRef]
        val relationMap = new util.HashMap[Object, AnyRef]
        val startNodeMap = new util.HashMap[Object, AnyRef]
        val endNodeMap = new util.HashMap[Object, AnyRef]

        retrievedVertices.forEach { result =>
          if (null != result)
            getRecordValues(result, vertexMap, relationMap, startNodeMap, endNodeMap)
        }

        if (!vertexMap.isEmpty) {
          val entry = vertexMap.entrySet().iterator().next()
          newVertex = gremlinVertexUtil.getNode(graphId, entry.getValue.asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex], relationMap, startNodeMap, endNodeMap, propTypeMap)
        }
        newVertex
      }
      catch {
        case ex: MiddlewareException => throw ex
        case e: Throwable =>
          e.printStackTrace()
          throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name(),
            DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
      }
    }
  }

  def getNodeProperty(graphId: String, vertexId: String, key: String): Future[Property] = {
    Future {
      TelemetryManager.log("Graph Id: " + graphId + "\nNode Id: " + vertexId + "\nProperty (Key): " + key)

      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node Property' Operation Failed.]")

      if (StringUtils.isBlank(vertexId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node Property' Operation Failed.]")

      if (StringUtils.isBlank(key))
        throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name,
          DACErrorMessageConstants.INVALID_PROPERTY_KEY + " | ['Get Node Property' Operation Failed.]")

      val property = new Property()
      try {
        val parameterMap = new util.HashMap[String, AnyRef]
        parameterMap.put(GraphDACParams.graphId.name, graphId)
        parameterMap.put(GraphDACParams.nodeId.name, vertexId)
        parameterMap.put(GraphDACParams.key.name, key)

        val resultSet = executeGetNodeProperty(parameterMap)
        val result = resultSet.one()
        val elementMap = result.getObject.asInstanceOf[util.Map[AnyRef, AnyRef]]
        if (null != elementMap && null != elementMap.get(key)){
          property.setPropertyName(key)
          property.setPropertyValue(elementMap.get(key))
        }
        property
      }
      catch {
        case e: Throwable =>
          e.printStackTrace()
          e.getCause match {
            case _: NoSuchElementException | _: ResourceNotFoundException =>
              throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name, DACErrorMessageConstants.NODE_NOT_FOUND + " | [Invalid Node Id.]: " + vertexId, vertexId)
            case _ =>
              throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name, DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
          }
      }
    }

  }

  def checkCyclicLoop(graphId: String, startNodeId: String, relationType: String, endNodeId: String): util.Map[String, AnyRef] = {
    if (StringUtils.isBlank(graphId))
      throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
        DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Check Cyclic Loop' Operation Failed.]")

    if (StringUtils.isBlank(startNodeId))
      throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
        DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Check Cyclic Loop' Operation Failed.]")

    if (StringUtils.isBlank(relationType))
      throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name,
        DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Check Cyclic Loop' Operation Failed.]")

    if (StringUtils.isBlank(endNodeId))
      throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
        DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Check Cyclic Loop' Operation Failed.]")

    val cyclicLoopMap = new util.HashMap[String, AnyRef]

//    try {
        val parameterMap = new util.HashMap[String, AnyRef]
        parameterMap.put(GraphDACParams.graphId.name, graphId)
        parameterMap.put(GraphDACParams.startNodeId.name, startNodeId)
        parameterMap.put(GraphDACParams.relationType.name, relationType)
        parameterMap.put(GraphDACParams.endNodeId.name, endNodeId)

        val resultSet = generateCheckCyclicLoopTraversal(parameterMap)
        val result = resultSet.one()
        if (null != result) {
          cyclicLoopMap.put(GraphDACParams.loop.name, new Boolean(true))
          cyclicLoopMap.put(GraphDACParams.message.name, startNodeId + " and " + endNodeId + " are connected by relation: " + relationType)
        }
        else cyclicLoopMap.put(GraphDACParams.loop.name, new Boolean(false))

//    }
    TelemetryManager.log("Returning Cyclic Loop Map: ", cyclicLoopMap)
    cyclicLoopMap
  }


  private def generateCheckCyclicLoopTraversal(parameterMap: util.Map[String, AnyRef]) = {

    if (null != parameterMap) {
      val graphId = parameterMap.get(GraphDACParams.graphId.name).asInstanceOf[String]
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Check Cyclic Loop' Query Generation Failed.]")

      val startNodeId = parameterMap.get(GraphDACParams.startNodeId.name).asInstanceOf[String]
      if (StringUtils.isBlank(startNodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_START_NODE_ID + " | ['Check Cyclic Loop' Query Generation Failed.]")

      val relationType = parameterMap.get(GraphDACParams.relationType.name).asInstanceOf[String]
      if (StringUtils.isBlank(relationType))
        throw new ClientException(DACErrorCodeConstants.INVALID_RELATION.name,
          DACErrorMessageConstants.INVALID_RELATION_TYPE + " | ['Check Cyclic Loop' Query Generation Failed.]")

      val endNodeId = parameterMap.get(GraphDACParams.endNodeId.name).asInstanceOf[String]
      if (StringUtils.isBlank(endNodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_END_NODE_ID + " | ['Check Cyclic Loop' Query Generation Failed.]")

      val client = ClientUtil.getGraphClient(graphId, GraphOperation.READ)
      val query = SearchUtil.checkCyclicLoopQuery(graphId, startNodeId, endNodeId, relationType)
      client.submit(query)
    }
    else throw new ClientException(DACErrorCodeConstants.INVALID_PARAMETER.name, DACErrorMessageConstants.INVALID_PARAM_MAP)
  }


  private def executeGetNodeProperty(parameterMap: util.Map[String, AnyRef]) = {
    try {
      if (null != parameterMap) {
        val graphId = parameterMap.get(GraphDACParams.graphId.name).asInstanceOf[String]
        if (StringUtils.isBlank(graphId))
          throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
            DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node Property' Query Generation Failed.]")

        val nodeId = parameterMap.get(GraphDACParams.nodeId.name).asInstanceOf[String]
        if (StringUtils.isBlank(nodeId))
          throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
            DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node Property' Query Generation Failed.]")

        val key = parameterMap.get(GraphDACParams.key.name).asInstanceOf[String]
        if (StringUtils.isBlank(key))
          throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name,
            DACErrorMessageConstants.INVALID_PROPERTY_KEY + " | ['Get Node Property' Query Generation Failed.]")

        val client = ClientUtil.getGraphClient(graphId, GraphOperation.READ)
        val query = SearchUtil.getNodePropertyQuery(graphId, nodeId, key)
        client.submit(query)
      }
      else throw new ClientException(DACErrorCodeConstants.INVALID_PARAMETER.name, DACErrorMessageConstants.INVALID_PARAM_MAP)
    }
    catch {
      case e: Exception =>
        throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name, "Error! Something went wrong while creating node object. ", e.getCause);
    }
  }

  private def getVertexByUniqueId(parameterMap: util.Map[String, AnyRef]): util.List[util.Map[String, AnyRef]] = {
    try {
        if (null != parameterMap) {
          val graphId = parameterMap.getOrDefault(GraphDACParams.graphId.name, "").asInstanceOf[String]
          if (StringUtils.isBlank(graphId))
            throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
              DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Node By Id' Query Generation Failed.]")

          val vertexId = parameterMap.get(GraphDACParams.nodeId.name).asInstanceOf[String]
          if (StringUtils.isBlank(vertexId))
            throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
              DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['Get Node By Unique Id' Query Generation Failed.]")

          val client = ClientUtil.getGraphClient(graphId, GraphOperation.READ)

          val gremlinQuery = SearchUtil.getVertexByUniqueIdQuery(graphId, vertexId)
          val resultSet: ResultSet = client.submit(gremlinQuery)
          val results: util.List[Result] = resultSet.all().get()

          val finalList = new util.ArrayList[util.Map[String, AnyRef]]
          for (result <- results) {
            val res = result.getObject.asInstanceOf[util.Map[String, AnyRef]]
            val resMap = new util.HashMap[String, AnyRef]
            resMap.put(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT, res.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT).asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex])
            resMap.put(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT, res.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT))
            val startNode = res.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT) match {
              case null => res.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT)
              case node => node
            }
            val endNode = res.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT) match {
              case null => res.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT)
              case node => node
            }
            resMap.put(CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT, startNode)
            resMap.put(CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT, endNode)
            finalList.add(resMap)
          }

          finalList
        }
        else throw new ClientException(DACErrorCodeConstants.INVALID_PARAMETER.name, DACErrorMessageConstants.INVALID_PARAM_MAP )
    }
    catch {
      case e :Exception =>
        e.printStackTrace()
        throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name, "Error! Something went wrong while creating node object. ", e.getCause);
    }
  }

  private def getRecordValues(result: util.Map[String, AnyRef], nodeMap :util.Map[Object, AnyRef], relationMap :util.Map[Object, AnyRef], startNodeMap :util.Map[Object, AnyRef], endNodeMap :util.Map[Object, AnyRef] ): Unit = {
    if (null != nodeMap) {
        val vertexValue = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT)
        if (null != vertexValue && vertexValue.isInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]) {
            val gremlinVertex : org.apache.tinkerpop.gremlin.structure.Vertex = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT).asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]
            nodeMap.put(gremlinVertex.id(), gremlinVertex)
        }
    }
    if (null != relationMap) {
        val edgeValue = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT)
        if (null != edgeValue && edgeValue.isInstanceOf[org.apache.tinkerpop.gremlin.structure.Edge]) {
            val edge: org.apache.tinkerpop.gremlin.structure.Edge = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT).asInstanceOf[Edge]
          relationMap.put(edge.id(), edge)
        }
    }

    if (null != startNodeMap) {
        val startVertexValue = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT)

        if (null != startVertexValue && startVertexValue.isInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]) {
            val startVertex: org.apache.tinkerpop.gremlin.structure.Vertex = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT).asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]
          startNodeMap.put(startVertex.id(), startVertex)
        }
    }
    if (null != endNodeMap) {
        val endVertexValue = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT)
        if (null != endVertexValue && endVertexValue.isInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]) {
            val endVertex: org.apache.tinkerpop.gremlin.structure.Vertex = result.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT).asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]
          endNodeMap.put(endVertex.id(), endVertex)
        }
    }
  }

  def getNodeByUniqueIds(graphId: String, searchCriteria: SearchCriteria): Future[util.ArrayList[Node]]  = {

    if (StringUtils.isBlank(graphId))
      throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name, DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Get Nodes By Search Criteria' Operation Failed.]")

    if (null == searchCriteria)
      throw new ClientException(DACErrorCodeConstants.INVALID_CRITERIA.name, DACErrorMessageConstants.INVALID_SEARCH_CRITERIA + " | ['Get Nodes By Search Criteria' Operation Failed.]")

    val client = ClientUtil.getGraphClient(graphId, GraphOperation.READ)

    val sb = new StringBuilder
    sb.append("g.V().hasLabel('" + graphId + "')")
      .append(searchCriteria.getJanusQuery.replace("AND", ""));

    val gremlinQuery = sb.toString
    val resultSet:ResultSet = client.submit(gremlinQuery)
    val results: util.List[Result]  = resultSet.all().get()
    val finalList = new util.ArrayList[util.Map[String, AnyRef]]
    val nodes = new util.ArrayList[Node]
    if(!CollectionUtils.isEmpty(results)){
      for (result <- results) {
        val res = result.getObject.asInstanceOf[util.Map[String, AnyRef]]
        val resMap = new util.HashMap[String, AnyRef]
        resMap.put(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT, res.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_NODE_OBJECT).asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex])
        resMap.put(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT, res.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_RELATION_OBJECT))
        resMap.put(CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT, res.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_START_NODE_OBJECT))
        resMap.put(CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT, res.get(CypherQueryConfigurationConstants.DEFAULT_CYPHER_END_NODE_OBJECT))
        finalList.add(resMap)
      }

      if (!CollectionUtils.isEmpty(finalList)) {
        val vertexMap = new util.HashMap[Object, AnyRef]
        val relationMap = new util.HashMap[Object, AnyRef]
        val startNodeMap = new util.HashMap[Object, AnyRef]
        val endNodeMap = new util.HashMap[Object, AnyRef]

        finalList.forEach { result =>
          if (null != result)
            getRecordValues(result, vertexMap, relationMap, startNodeMap, endNodeMap)
        }

        if (!vertexMap.isEmpty) {
          for (entry <- vertexMap.entrySet) {
            nodes.add(gremlinVertexUtil.getNode(graphId, entry.getValue.asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex], relationMap, startNodeMap, endNodeMap))
          }
        }
      }
    }

    Future {
      nodes
    }
  }

}
