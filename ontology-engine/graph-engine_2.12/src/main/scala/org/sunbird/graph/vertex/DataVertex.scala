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

}
