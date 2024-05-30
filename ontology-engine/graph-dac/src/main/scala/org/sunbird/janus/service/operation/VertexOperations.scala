package org.sunbird.janus.service.operation

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.{BooleanUtils, StringUtils}
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.{GraphTraversal, GraphTraversalSource}
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ServerException}
import org.sunbird.common.{DateUtils, JsonUtils}
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.common.enums.{AuditProperties, GraphDACParams, SystemProperties}
import org.sunbird.graph.dac.enums.SystemNodeTypes
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.service.common.{DACErrorCodeConstants, DACErrorMessageConstants}
import org.sunbird.janus.service.util.JanusConnectionUtil
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.collection.convert.ImplicitConversions.{`collection AsScalaIterable`, `map AsJavaMap`, `map AsScala`}
import scala.collection.immutable.HashMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VertexOperations {

  val graphConnection = new JanusConnectionUtil
  def addVertex(graphId: String, vertex: Node): Future[Node] = {
    Future {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
              DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

      if (null == vertex)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
              DACErrorMessageConstants.INVALID_NODE + " | [Create Node Operation Failed.]")

      val parameterMap = new util.HashMap[String, AnyRef]
      parameterMap.put(GraphDACParams.graphId.name, graphId)
      parameterMap.put(GraphDACParams.vertex.name, setPrimitiveData(vertex))

      try {
          graphConnection.initialiseGraphClient()
          val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

          val addedVertex = createVertexTraversal(parameterMap, g)
          val vertexElementMap = addedVertex.elementMap().next()

          vertex.setGraphId(graphId)
          vertex.setIdentifier(vertexElementMap.get(SystemProperties.IL_UNIQUE_ID.name))
          vertex.getMetadata.put(GraphDACParams.versionKey.name, vertexElementMap.get(GraphDACParams.versionKey.name))
          vertex
      }
      catch {
          case e: Throwable =>
            e.printStackTrace()
            e.getCause match {
              case cause: org.apache.tinkerpop.gremlin.driver.exception.ResponseException =>
                throw new ClientException(
                  DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(),
                  DACErrorMessageConstants.CONSTRAINT_VALIDATION_FAILED + vertex.getIdentifier
                )
              case cause =>
                throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name, DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
            }
      }
    }
  }

  def deleteVertex(graphId: String, vertexId: String, request: Request): Future[java.lang.Boolean] = {
    Future {
      if (StringUtils.isBlank(graphId))
          throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
              DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Remove Property Values Operation Failed.]")

      if (StringUtils.isBlank(vertexId))
          throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
              DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Remove Property Values Operation Failed.]")

      try {
          graphConnection.initialiseGraphClient()
          val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

          val parameterMap = new util.HashMap[String, AnyRef]
          parameterMap.put(GraphDACParams.graphId.name, graphId)
          parameterMap.put(GraphDACParams.nodeId.name, vertexId)
          parameterMap.put(GraphDACParams.request.name, request)

          executeVertexDeletion(parameterMap, g)
      }
      catch {
        case e: Throwable =>
          e.printStackTrace()
          throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
            DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
      }
    }
  }

  private def executeVertexDeletion(parameterMap: util.Map[String, AnyRef], g: GraphTraversalSource): Boolean = {
    if (null != parameterMap) {
      val graphId = parameterMap.get(GraphDACParams.graphId.name).asInstanceOf[String]
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Remove Property Values Query Generation Failed.]")

      val nodeId = parameterMap.get(GraphDACParams.nodeId.name).asInstanceOf[String]
      if (StringUtils.isBlank(nodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Remove Property Values Query Generation Failed.]")

      val traversal = g.V().hasLabel(graphId).has(SystemProperties.IL_UNIQUE_ID.name(), nodeId)
      if (traversal.hasNext) {
        traversal.drop().iterate()
        true
      } else {
        throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name,
          DACErrorMessageConstants.NODE_NOT_FOUND + " | [Invalid Node Id.]: " + nodeId, nodeId)
      }
    } else {
      throw new ClientException(DACErrorCodeConstants.INVALID_PARAMETER.name, DACErrorMessageConstants.INVALID_PARAM_MAP)
    }
  }

  private def createVertexTraversal(parameterMap: util.Map[String, AnyRef], g: GraphTraversalSource): GraphTraversal[org.apache.tinkerpop.gremlin.structure.Vertex, org.apache.tinkerpop.gremlin.structure.Vertex] = {
    if (null != parameterMap) {
      val graphId = parameterMap.getOrDefault(GraphDACParams.graphId.name,"").asInstanceOf[String]
      val vertex = parameterMap.getOrDefault(GraphDACParams.vertex.name, null).asInstanceOf[Node]

      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Node' Query Generation Failed.]")

      if (null == vertex)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Create Node Query Generation Failed.]")

      val date: String = DateUtils.formatCurrentDate

      val mpMap :util.Map[String, AnyRef] = getMetadataCypherQueryMap(vertex)
      val spMap :util.Map[String, AnyRef] = getSystemPropertyMap(vertex, date)
      val apMap :util.Map[String, AnyRef] = getAuditPropertyMap(vertex, date, false)
      val vpMap :util.Map[String, AnyRef] = getVersionPropertyMap(vertex, date)

      val combinedMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
      combinedMap.putAll(mpMap)
      combinedMap.putAll(spMap)
      combinedMap.putAll(apMap)
      combinedMap.putAll(vpMap)

      parameterMap.put(GraphDACParams.paramValueMap.name, combinedMap)

      val newVertexTraversal = g.addV(vertex.getGraphId)
      val finalMap = parameterMap.getOrDefault(GraphDACParams.paramValueMap.name, new util.HashMap[String, AnyRef]).asInstanceOf[util.Map[String, AnyRef]]

      finalMap.foreach { case (key, value) => newVertexTraversal.property(key, value) }

      newVertexTraversal
    }
    else {
      throw new ClientException(DACErrorCodeConstants.INVALID_PARAMETER.name, DACErrorMessageConstants.INVALID_PARAM_MAP )
    }
  }

  def getMetadataCypherQueryMap(node: Node): util.Map[String, AnyRef] = {
    val metadataPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && null != node.getMetadata && !node.getMetadata.isEmpty) {
      node.getMetadata.foreach { case (key, value) => metadataPropertyMap.put(key, value) }
    }
    metadataPropertyMap
  }

  def getSystemPropertyMap(node: Node, date: String): util.Map[String, AnyRef] = {
    val systemPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && StringUtils.isNotBlank(date)) {
      if (StringUtils.isBlank(node.getIdentifier))
        node.setIdentifier(Identifier.getIdentifier(node.getGraphId, Identifier.getUniqueIdFromTimestamp))
      systemPropertyMap.put(SystemProperties.IL_UNIQUE_ID.name, node.getIdentifier)
      systemPropertyMap.put(SystemProperties.IL_SYS_NODE_TYPE.name, node.getNodeType)
      systemPropertyMap.put(SystemProperties.IL_FUNC_OBJECT_TYPE.name, node.getObjectType)
    }
    systemPropertyMap
  }

  def getAuditPropertyMap(node: Node, date: String, isUpdateOnly: Boolean):util.Map[String, AnyRef] = {
    val auditPropertyMap = new util.HashMap[String, AnyRef]
    if(null != node && StringUtils.isNotBlank(date)) {
      if (BooleanUtils.isFalse(isUpdateOnly)) {
        auditPropertyMap.put(AuditProperties.createdOn.name,
          if (node.getMetadata.containsKey(AuditProperties.createdOn.name))
            node.getMetadata.get(AuditProperties.createdOn.name)
          else date)
      }
      if (null != node.getMetadata && null == node.getMetadata.get(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name))
        auditPropertyMap.put(AuditProperties.lastUpdatedOn.name, date)
    }
    auditPropertyMap
  }

  def getVersionPropertyMap(node: Node, date: String): util.Map[String, AnyRef] = {
    val versionPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && StringUtils.isNotBlank(date))
      versionPropertyMap.put(GraphDACParams.versionKey.name, DateUtils.parse(date).getTime.toString)
    versionPropertyMap
  }

  def upsertVertex(graphId: String, vertex: Node, request: Request): Future[Node] = {
    Future {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Create Node Operation Failed.]")

      if (null == vertex)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Create Node Operation Failed.]")

      val parameterMap = new util.HashMap[String, AnyRef]
      parameterMap.put(GraphDACParams.graphId.name, graphId)
      parameterMap.put(GraphDACParams.vertex.name, setPrimitiveData(vertex))
      parameterMap.put(GraphDACParams.request.name, request)

      prepareUpsertMap(parameterMap)
      try {
        graphConnection.initialiseGraphClient()
        val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

        val existingVertex = g.V().has(SystemProperties.IL_UNIQUE_ID.name, vertex.getIdentifier)
        val finalMap = parameterMap.getOrDefault(GraphDACParams.paramValueMap.name, new util.HashMap[String, AnyRef]).asInstanceOf[util.Map[String, AnyRef]]
        finalMap.foreach { case (key, value) =>
          if (!key.equals(GraphDACParams.graphId.name) && !key.equals(GraphDACParams.request.name)) {
            existingVertex.property(key, value)
          }
        }

        val retrieveVertex = existingVertex.elementMap().next()
        vertex.setIdentifier(retrieveVertex.get(SystemProperties.IL_UNIQUE_ID.name))
        vertex.getMetadata.put(GraphDACParams.versionKey.name, retrieveVertex.get(GraphDACParams.versionKey.name))
        vertex
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          e.getCause match {
            case cause: org.apache.tinkerpop.gremlin.driver.exception.ResponseException =>
              throw new ClientException(
                DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(),
                DACErrorMessageConstants.CONSTRAINT_VALIDATION_FAILED + vertex.getIdentifier
              )
            case cause =>
              throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name, DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
          }
      }
    }
  }

  def prepareUpsertMap(parameterMap: util.Map[String, AnyRef]) = {
    if (null != parameterMap) {
      val vertex = parameterMap.getOrDefault("vertex", null).asInstanceOf[Node]
      if (null == vertex)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Upsert Node Query Generation Failed.]")
      if (StringUtils.isBlank(vertex.getIdentifier))
        vertex.setIdentifier(Identifier.getIdentifier(vertex.getGraphId, Identifier.getUniqueIdFromTimestamp))
      val date: String = DateUtils.formatCurrentDate

      val ocsMap: util.Map[String, AnyRef] = getOnCreateSetMap( vertex, date)
      val omsMap: util.Map[String, AnyRef] = getOnMatchSetMap( vertex,date, merge = true)
      val combinedMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
      combinedMap.putAll(ocsMap)
      combinedMap.putAll(omsMap)
      parameterMap.put(GraphDACParams.paramValueMap.name, combinedMap)
    }
  }

  private def getOnCreateSetMap(node: Node, date: String): util.Map[String, Object] = {
    val paramMap = new util.HashMap[String, Object]()

    if (node != null && StringUtils.isNotBlank(date)) {
      if (StringUtils.isBlank(node.getIdentifier)) {
        node.setIdentifier(Identifier.getIdentifier(node.getGraphId, Identifier.getUniqueIdFromTimestamp))
      }

      paramMap.put(SystemProperties.IL_UNIQUE_ID.name, node.getIdentifier)

      paramMap.put(SystemProperties.IL_SYS_NODE_TYPE.name, node.getNodeType)

      if (StringUtils.isNotBlank(node.getObjectType)) {
        paramMap.put(SystemProperties.IL_FUNC_OBJECT_TYPE.name, node.getObjectType)
      }
      paramMap.put(AuditProperties.createdOn.name, date)

      if (!node.getMetadata.containsKey(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name)) {
        paramMap.put(AuditProperties.lastUpdatedOn.name, date)
      }
      val versionKey = DateUtils.parse(date).getTime.toString
      paramMap.put(GraphDACParams.versionKey.name, versionKey)
    }

    paramMap
  }

  private def getOnMatchSetMap(node: Node, date: String, merge: Boolean): util.Map[String, Object] = {
    val paramMap = new util.HashMap[String, Object]()

    if (node != null && StringUtils.isNotBlank(date)) {

      if (node.getMetadata != null) {
        node.getMetadata.foreach {
          case (key, value) => if (key != GraphDACParams.versionKey.name) paramMap.put(key, value)
        }
      }

      // Set lastUpdatedOn property if not already set
      if (!node.getMetadata.containsKey(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name)) {
        paramMap.put(AuditProperties.lastUpdatedOn.name, date)
      }

      // Set versionKey property if missing in metadata
      if (node.getMetadata != null &&
        StringUtils.isBlank(node.getMetadata.get(GraphDACParams.versionKey.name()).toString)) {
        val versionKey = DateUtils.parse(date).getTime.toString
        paramMap.put(GraphDACParams.versionKey.name, versionKey)
      }
    }

    paramMap
  }

  def upsertRootVertex(graphId: String, request: AnyRef): Future[Node] = {
    Future {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Upsert Root Node Operation Failed.]")

      val g = graphConnection.getGraphTraversalSource

      val rootNodeUniqueId = Identifier.getIdentifier(graphId, SystemNodeTypes.ROOT_NODE.name())

      val vertex = new Node
      vertex.setIdentifier(rootNodeUniqueId)
      vertex.getMetadata.put(SystemProperties.IL_UNIQUE_ID.name, rootNodeUniqueId)
      vertex.getMetadata.put(SystemProperties.IL_SYS_NODE_TYPE.name, SystemNodeTypes.ROOT_NODE.name)
      vertex.getMetadata.put(AuditProperties.createdOn.name, DateUtils.formatCurrentDate())
      vertex.getMetadata.put(GraphDACParams.Nodes_Count.name, 0: Integer)
      vertex.getMetadata.put(GraphDACParams.Relations_Count.name, 0: Integer)

      val parameterMap = Map(
        GraphDACParams.graphId.name -> graphId,
        GraphDACParams.rootNode.name -> vertex,
        GraphDACParams.request.name -> request
      )

      try {
        val existingRootNode = g.V().has(SystemProperties.IL_UNIQUE_ID.name, rootNodeUniqueId).next()

        val updatedVertex = existingRootNode.property(AuditProperties.createdOn.name, DateUtils.formatCurrentDate())

        val identifier = updatedVertex.property(SystemProperties.IL_UNIQUE_ID.name).value().toString
        val versionKey = Option(updatedVertex.property(GraphDACParams.versionKey.name)).map(_.value().toString).getOrElse("")

        vertex.setIdentifier(identifier)
        vertex.setGraphId(graphId)
        if (StringUtils.isNotBlank(versionKey))
          vertex.getMetadata.put(GraphDACParams.versionKey.name, versionKey)

        vertex

      }
      catch {
        case e: Throwable =>
          throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name,
            DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage, e)
      }
    }
  }

  def updateVertexes(graphId: String, identifiers: java.util.List[String], data: java.util.Map[String, AnyRef]): Future[util.Map[String, Node]] = {
    Future {
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Invalid or 'null' Graph Id.]")


      if (identifiers.isEmpty)
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
        DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Please Provide Node Identifier.]")

      if (MapUtils.isEmpty(data))
        throw new ClientException(DACErrorCodeConstants.INVALID_METADATA.name,
          DACErrorMessageConstants.INVALID_METADATA + " | [Please Provide Valid Node Metadata]")

      val parameterMap = generateUpdateVerticesQuery(graphId, identifiers, setPrimitiveData(data))

      try {
        graphConnection.initialiseGraphClient()
        val g: GraphTraversalSource = graphConnection.getGraphTraversalSource

        val updatedVertices = identifiers.foldLeft(List.empty[Node]) {
          (acc: List[Node], identifier: String) =>
            val existingVertex = g.V().has(SystemProperties.IL_UNIQUE_ID.toString, identifier)
            val finalMap = parameterMap.getOrDefault(GraphDACParams.paramValueMap.name, new util.HashMap[String, AnyRef]).asInstanceOf[util.Map[String, AnyRef]]

            finalMap.foreach { case (key, value) =>
              if (!key.equals(GraphDACParams.graphId.name) && !key.equals(GraphDACParams.request.name)) {
                existingVertex.property(key, value)
              }
            }

            val updatedVertex = existingVertex.toList().head.asInstanceOf[Node]

            acc :+ updatedVertex
        }


        val resultMap = updatedVertices.map(vertex => {
          val identifier = vertex.getMetadata.get(SystemProperties.IL_UNIQUE_ID.name).asInstanceOf[String]
          val newVertex = new Node
          newVertex.setIdentifier(identifier)
          newVertex
        })

        resultMap.asInstanceOf[Map[String, Node]]
      }
      catch {
        case e: Throwable =>
          e.printStackTrace()
          e match {
            case cause: org.apache.tinkerpop.gremlin.driver.exception.ResponseException =>
              throw new ClientException(
                DACErrorCodeConstants.CONSTRAINT_VALIDATION_FAILED.name(),
                DACErrorMessageConstants.CONSTRAINT_VALIDATION_FAILED + " | Updating multiple nodes failed."
              )
            case cause =>
              val errorMessage = DACErrorMessageConstants.CONNECTION_PROBLEM + " | " + e.getMessage
              throw new ServerException(DACErrorCodeConstants.CONNECTION_PROBLEM.name, errorMessage, e)
          }
      }
    }
  }

  private def setPrimitiveData(metadata: java.util.Map[String, AnyRef]): mutable.Map[String, Object] = {
    metadata.flatMap { case (key, value) =>
      val processedValue = value match {
        case map: util.Map[Any, Any] =>
          try {
            JsonUtils.serialize(map)
          } catch {
            case e: Exception =>
              TelemetryManager.error("Exception Occurred While Processing Primitive Data Types | Exception is : " + e.getMessage(), e)
              value
          }
        case list: List[_] if list.nonEmpty && list.head.isInstanceOf[Map[Any, Any]] =>
          try {
            JsonUtils.serialize(list)
          } catch {
            case e: Exception =>
              TelemetryManager.error("Exception Occurred While Processing Primitive Data Types | Exception is : " + e.getMessage(), e)
              value
          }
        case _ => value
      }
      Some((key, processedValue))
    }
  }

  def generateUpdateVerticesQuery(graphId: String, identifiers: java.util.List[String], data: mutable.Map[String, AnyRef]): Map[String, Object] = {
    val parameterMap = new HashMap[String, Object]
    parameterMap.put("identifiers", identifiers);
    parameterMap.putAll(data);
    parameterMap;
  }

  private def setPrimitiveData(vertex: Node): Node = {
    val metadata: util.Map[String, AnyRef] = vertex.getMetadata
    metadata.forEach((key, value) => {
      try {
        value match {
          case v: util.Map[String, AnyRef] => metadata.put(key, JsonUtils.serialize(v))
          case v: util.List[util.Map[String, AnyRef]] if (!v.isEmpty && v.isInstanceOf[util.Map[String, AnyRef]]) => metadata.put(key, JsonUtils.serialize(v))
          case _ =>
        }
      } catch {
        case e: Exception => TelemetryManager.error(s"Exception Occurred While Processing Primitive Data Types | Exception is : ${e.getMessage}", e)
      }
    })
    vertex
  }



}
