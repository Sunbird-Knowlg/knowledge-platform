package org.sunbird.janus.service.operation

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.{GraphTraversal, GraphTraversalSource}
import org.sunbird.janus.dac.util.GremlinVertexUtil
import org.apache.tinkerpop.gremlin.groovy.jsr223.dsl.credential.__._
import org.sunbird.graph.dac.model.Node
import org.apache.tinkerpop.gremlin.structure.Edge
import org.sunbird.common.dto.{Property, Request}
import org.sunbird.common.exception.{ClientException, MiddlewareException, ResourceNotFoundException, ServerException}
import org.sunbird.graph.common.enums.{GraphDACParams, SystemProperties}
import org.sunbird.graph.service.common.{CypherQueryConfigurationConstants, DACErrorCodeConstants, DACErrorMessageConstants}
import org.sunbird.janus.service.util.JanusConnectionUtil
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import scala.collection.JavaConverters.{asJavaIterableConverter, asScalaBufferConverter}
import java.lang.Boolean

class SearchOperations {

  val graphConnection = new JanusConnectionUtil
  val gremlinVertexUtil = new GremlinVertexUtil

  def getNodeByUniqueId(graphId: String, vertexId: String, getTags: Boolean, request: Request): Future[Node] = {
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
        graphConnection.initialiseGraphClient()
        val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

        val parameterMap = new util.HashMap[String, AnyRef]
        parameterMap.put(GraphDACParams.graphId.name, graphId)
        parameterMap.put(GraphDACParams.nodeId.name, vertexId)
        parameterMap.put(GraphDACParams.getTags.name, getTags.asInstanceOf[java.lang.Boolean])
        parameterMap.put(GraphDACParams.request.name, request)

        val retrievedVertices = getVertexByUniqueId(parameterMap, g)
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
          newVertex = gremlinVertexUtil.getNode(graphId, entry.getValue.asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex], relationMap, startNodeMap, endNodeMap)
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
        graphConnection.initialiseGraphClient()
        val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

        val parameterMap = new util.HashMap[String, AnyRef]
        parameterMap.put(GraphDACParams.graphId.name, graphId)
        parameterMap.put(GraphDACParams.nodeId.name, vertexId)
        parameterMap.put(GraphDACParams.key.name, key)

        val nodeProperty = executeGetNodeProperty(parameterMap, g)
        val elementMap = nodeProperty.elementMap().next()
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

    try {
        graphConnection.initialiseGraphClient()
        val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

        val parameterMap = new util.HashMap[String, AnyRef]
        parameterMap.put(GraphDACParams.graphId.name, graphId)
        parameterMap.put(GraphDACParams.startNodeId.name, startNodeId)
        parameterMap.put(GraphDACParams.relationType.name, relationType)
        parameterMap.put(GraphDACParams.endNodeId.name, endNodeId)

        val result = generateCheckCyclicLoopTraversal(parameterMap, g)
        if (null != result && result.hasNext) {
          cyclicLoopMap.put(GraphDACParams.loop.name, new Boolean(true))
          cyclicLoopMap.put(GraphDACParams.message.name, startNodeId + " and " + endNodeId + " are connected by relation: " + relationType)
        }
        else
          cyclicLoopMap.put(GraphDACParams.loop.name, new Boolean(false))

    }
    TelemetryManager.log("Returning Cyclic Loop Map: ", cyclicLoopMap)
    cyclicLoopMap
  }


  def generateCheckCyclicLoopTraversal(parameterMap: util.Map[String, AnyRef], g: GraphTraversalSource) = {

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

       val cyclicTraversal = g.V().hasLabel(graphId).has(SystemProperties.IL_UNIQUE_ID.name(), startNodeId)
        .repeat(outE(relationType).inV().simplePath()).until(has(SystemProperties.IL_UNIQUE_ID.name(), endNodeId))
        .hasLabel(graphId)
      cyclicTraversal
    }
    else throw new ClientException(DACErrorCodeConstants.INVALID_PARAMETER.name, DACErrorMessageConstants.INVALID_PARAM_MAP)
  }


  def executeGetNodeProperty(parameterMap: util.Map[String, AnyRef], g: GraphTraversalSource) = {
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

        g.V().hasLabel(graphId).has(SystemProperties.IL_UNIQUE_ID.name, nodeId).values(key)

      }
      else throw new ClientException(DACErrorCodeConstants.INVALID_PARAMETER.name, DACErrorMessageConstants.INVALID_PARAM_MAP)
    }
    catch {
      case e: Exception =>
        throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name, "Error! Something went wrong while creating node object. ", e.getCause);
    }
  }

  private def getVertexByUniqueId(parameterMap: util.Map[String, AnyRef], g: GraphTraversalSource): util.List[util.Map[String, AnyRef]] = {
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
          val result = g.V().hasLabel(graphId).has("IL_UNIQUE_ID", vertexId).as("ee")
            .flatMap(
              coalesce(
                union(
                  outE().dedup().as("r").inV().dedup().as("__endNode").select("ee", "r", "__endNode"),
                  inE().dedup().as("r").outV().dedup().as("__startNode").select("ee", "r", "__startNode")
                ),
                project("ee", "r", "__startNode", "__endNode")
                  .by(select("ee"))
                  .by(constant(null))
                  .by(constant(null))
                  .by(constant(null))
              )
          )
        .dedup().toList.asInstanceOf[util.List[util.Map[String, AnyRef]]]

          val finalList = new util.ArrayList[util.Map[String, AnyRef]]
          result.asScala.map { tr =>
            val ee = tr.get("ee").asInstanceOf[org.apache.tinkerpop.gremlin.structure.Vertex]
            val r = tr.get("r")
            val startNode = tr.get("__startNode") match {
              case null => ee
              case node => node
            }
            val endNode = tr.get("__endNode") match {
              case null => ee
              case node => node
            }
            val resMap = new util.HashMap[String, AnyRef]
            resMap.put("ee", ee)
            resMap.put("r", r)
            resMap.put("__startNode", startNode)
            resMap.put("__endNode", endNode)
            finalList.add(resMap)
          }.asJava

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

}
