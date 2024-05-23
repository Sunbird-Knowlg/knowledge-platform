package org.sunbird.graph.vertex

import java.util
import java.util.Optional
import java.util.concurrent.CompletionException
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.DateUtils
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Edges, Filter, MetadataCriterion, SearchConditions, SearchCriteria, Vertex}
import org.sunbird.graph.nodes.DataNode.saveExternalProperties
import org.sunbird.graph.schema.{DefinitionDTO, DefinitionFactory, DefinitionNode}
import org.sunbird.parseq.Task

import scala.collection.convert.ImplicitConversions._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
object DataVertex {

  private val SYSTEM_UPDATE_ALLOWED_CONTENT_STATUS = List("Live", "Unlisted")

  @throws[Exception]
  def create(request: Request, dataModifier: (Vertex) => Vertex = defaultVertexDataModifier)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Vertex] = {
    DefinitionNode.validates(request).map(vertex => {
      val response = oec.janusGraphService.addVertex(request.graphId, dataModifier(vertex))
      response.map(vertex => DefinitionNode.postProcessor(request, vertex)).map(result => {
        val futureList = Task.parallel[Response](
          saveExternalProperties(vertex.getIdentifier, vertex.getExternalData, request.getContext, request.getObjectType),
          createEdges(request.graphId, vertex, request.getContext))
        futureList.map(list => result)
      }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }).flatMap(f => f)
  }

  @throws[Exception]
  def read(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Vertex] = {
    DefinitionNode.getVertex(request).map(vertex => {
      val schema = vertex.getObjectType.toLowerCase.replace("image", "")
      val objectType: String = request.getContext.get("objectType").asInstanceOf[String]
      request.getContext.put("schemaName", schema)
      val fields: List[String] = Optional.ofNullable(request.get("fields").asInstanceOf[util.List[String]]).orElse(new util.ArrayList[String]()).toList
      val version: String = if (null != vertex && null != vertex.getMetadata) {
        val schemaVersion: String = vertex.getMetadata.getOrDefault("schemaVersion", "0.0").asInstanceOf[String]
        val scVer = if (StringUtils.isNotBlank(schemaVersion) && schemaVersion.toDouble != 0.0) schemaVersion else request.getContext.get("version").asInstanceOf[String]
        scVer
      } else request.getContext.get("version").asInstanceOf[String]
      val extPropNameList = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], version, schema)
      if (CollectionUtils.isNotEmpty(extPropNameList) && null != fields && fields.exists(field => extPropNameList.contains(field)))
        populateExternalProperties(fields, vertex, request, extPropNameList)
      else
        Future(vertex)
    }).flatMap(f => f) recoverWith {
      case e: CompletionException => throw e.getCause
    }
  }

  private def createEdges(graphId: String, vertex: Vertex, context: util.Map[String, AnyRef])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    val edges: util.List[Edges] = vertex.getAddedEdges
    if (CollectionUtils.isNotEmpty(edges)) {
      oec.janusGraphService.createEdges(graphId, getEdgesMap(edges))
    } else {
      Future(new Response)
    }
  }

  private def getEdgesMap(edges: util.List[Edges]): java.util.List[util.Map[String, AnyRef]] = {
    val list = new util.ArrayList[util.Map[String, AnyRef]]
    for (edge <- edges) {
      if ((StringUtils.isNotBlank(edge.getStartVertexId) && StringUtils.isNotBlank(edge.getEndVertexId)) && StringUtils.isNotBlank(edge.getEdgeType)) {
        val map = new util.HashMap[String, AnyRef]
        map.put("startNodeId", edge.getStartVertexId)
        map.put("endNodeId", edge.getEndVertexId)
        map.put("relation", edge.getEdgeType)
        if (MapUtils.isNotEmpty(edge.getMetadata)) map.put("relMetadata", edge.getMetadata)
        else map.put("relMetadata", new util.HashMap[String, AnyRef]())
        list.add(map)
      }
      else throw new ClientException("ERR_INVALID_RELATION_OBJECT", "Invalid Relation Object Found.")
    }
    list
  }

  private def defaultVertexDataModifier(vertex: Vertex) = {
    vertex
  }

  private def defaultDataModifier(vertex: Vertex) = {
    vertex
  }

//  def systemUpdate(request: Request, vertexList: util.List[Vertex], hierarchyKey: String, hierarchyFunc: Option[Request => Future[Response]] = None)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Vertex] = {
//    val data: util.Map[String, AnyRef] = request.getRequest
//
//    // validate nodes
//    validateVertex(vertexList, request)
//
//    // get definition for the object and filter relations
//    val definition = getDefinition(request)
//    val metadata = filterRelations(definition, data)
//
//    // get status
//    val status = getStatus(request, vertexList)
//    // Generate request for new metadata
//    val newRequest = new Request(request)
//    newRequest.putAll(metadata)
//    newRequest.getContext.put("versioning", "disabled")
//    // Enrich Hierarchy and Update the nodes
//    vertexList.map(vertex => {
//      enrichHierarchyAndUpdate(newRequest, vertex, status, hierarchyKey, hierarchyFunc)
//    }).head
//
//  }
//
//  @throws[Exception]
//  private def enrichHierarchyAndUpdate(request: Request, vertex: Vertex, status: String, hierarchyKey: String, hierarchyFunc: Option[Request => Future[Response]] = None)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Vertex] = {
//    val metadata: util.Map[String, AnyRef] = request.getRequest
//    val identifier = vertex.getIdentifier
//    // Image node cannot be made Live or Unlisted using system call
//    if (identifier.endsWith(".img") &&
//      SYSTEM_UPDATE_ALLOWED_CONTENT_STATUS.contains(status)) metadata.remove("status")
//    if (metadata.isEmpty) throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), s"Invalid Request. Cannot update status of Image Node to $status.")
//
//    // Update previous status and status update Timestamp
//    if (metadata.containsKey("status")) {
//      metadata.put("prevStatus", vertex.getMetadata.get("status"))
//      metadata.put("lastStatusChangedOn", DateUtils.formatCurrentDate)
//    }
//    // Generate new request object for Each request
//    val newRequest = new Request(request)
//    newRequest.putAll(metadata)
//    newRequest.getContext.put("identifier", identifier)
//    // Enrich Hierarchy and Update with the new request
//    enrichHierarchy(newRequest, metadata, status, hierarchyKey: String, hierarchyFunc)
//      .flatMap(req => update(req)) recoverWith { case e: CompletionException => throw e.getCause }
//  }

  private def enrichHierarchy(request: Request, metadata: util.Map[String, AnyRef], status: String, hierarchyKey: String, hierarchyFunc: Option[Request => Future[Response]] = None)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Request] = {
    val identifier = request.getContext.get("identifier").asInstanceOf[String]
    // Check if hierarchy could be enriched
    if (!identifier.endsWith(".img") && SYSTEM_UPDATE_ALLOWED_CONTENT_STATUS.contains(status)) {
      hierarchyFunc match {
        case Some(hierarchyFunc) => {
          // Get current Hierarchy
          val hierarchyRequest = new Request(request)
          hierarchyRequest.put("rootId", identifier)
          hierarchyFunc(hierarchyRequest).map(response => {
            // Add metadata to the hierarchy
            if (response.get(hierarchyKey) != null) {
              val hierarchy = response.get(hierarchyKey).asInstanceOf[util.Map[String, AnyRef]]
              val hierarchyMetadata = new util.HashMap[String, AnyRef]()
              hierarchyMetadata.putAll(hierarchy)
              hierarchyMetadata.putAll(metadata)
              // add hierarchy to the request object
              request.put("hierarchy", hierarchyMetadata)
              request
            } else request
          })
        }
        case _ => Future(request)
      }
    } else Future(request)
  }

  def validateVertex(vertexs: java.util.List[Vertex], request: Request): Unit = {
    if (vertexs.isEmpty)
      throw new ClientException(ResponseCode.RESOURCE_NOT_FOUND.name(), s"Error! Node(s) doesn't Exists with identifier : ${request.getContext.get("identifier")}.")

    val objectType = request.getContext.get("objectType").asInstanceOf[String]
    vertexs.foreach(vertex => {
      if (vertex.getMetadata == null && !objectType.equalsIgnoreCase(vertex.getObjectType) && vertex.getMetadata.get("status").asInstanceOf[String].equalsIgnoreCase("failed"))
        throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), s"Cannot update content with FAILED status for id : ${vertex.getIdentifier}.")
    })
  }

  private def getStatus(request: Request, vertexList: util.List[Vertex]): String = {
    val vertex = vertexList.filter(node => !node.getIdentifier.endsWith(".img")).headOption.getOrElse(vertexList.head)
    request.getOrDefault("status", vertex.getMetadata.get("status")).asInstanceOf[String]
  }

  private def getDefinition(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): DefinitionDTO = {
    val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
    val version = request.getContext.get("version").asInstanceOf[String]
    DefinitionFactory.getDefinition(request.graphId, schemaName, version)
  }

  private def filterRelations(definition: DefinitionDTO, data: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    val relations = definition.getRelationsMap().keySet()
    data.filter(item => {
      !relations.contains(item._1)
    })
  }

  private def populateExternalProperties(fields: List[String], vertex: Vertex, request: Request, externalProps: List[String])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Vertex] = {
    if (StringUtils.equalsIgnoreCase(request.get("mode").asInstanceOf[String], "edit"))
      request.put("identifier", vertex.getIdentifier)
    val externalPropsResponse = oec.graphService.readExternalProps(request, externalProps.filter(prop => fields.contains(prop)))
    externalPropsResponse.map(response => {
      vertex.getMetadata.putAll(response.getResult)
      Future {
        vertex
      }
    }).flatMap(f => f)
  }

}
