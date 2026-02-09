package org.sunbird.graph.validator

import java.util
import java.util.concurrent.CompletionException

import org.sunbird.common.exception.{ResourceNotFoundException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Filter, MetadataCriterion, Node, SearchConditions, SearchCriteria}
import org.sunbird.graph.exception.GraphErrorCodes
import org.sunbird.graph.service.operation.SearchAsyncOperations
import scala.jdk.CollectionConverters._

import scala.collection.convert.ImplicitConversions._
import scala.concurrent.{ExecutionContext, Future}

object NodeValidator {

    def validate(graphId: String, identifiers: util.List[String])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[util.Map[String, Node]] = {
        val nodes = getDataNodes(graphId, identifiers)

        nodes.map(dataNodes => {
            if (dataNodes.size != identifiers.size) {
                val dbNodeIds = dataNodes.map(node => node.getIdentifier).toList
                val invalidIds = identifiers.toList.filter(id => !dbNodeIds.contains(id))
                throw new ResourceNotFoundException(GraphErrorCodes.ERR_INVALID_NODE.toString, "Node Not Found With Identifier " + invalidIds)
            } else {
                new util.HashMap[String, Node](dataNodes.map(node => node.getIdentifier -> node).toMap.asJava)
            }
        }) recoverWith {
            case e: CompletionException => throw e.getCause
        }
    }

    private def getDataNodes(graphId: String, identifiers: util.List[String])(implicit ec: ExecutionContext, oec: OntologyEngineContext) = {
        if (identifiers.size() == 1) {
            System.out.println("NodeValidator: Singular lookup for identifier: " + identifiers.get(0))
            oec.graphService.getNodeByUniqueId(graphId, identifiers.get(0), false, new org.sunbird.common.dto.Request())
                .map(node => util.Arrays.asList(node))
                .recover {
                    case e: ResourceNotFoundException => util.Arrays.asList()
                }
        } else {
            val searchCriteria = new SearchCriteria
            searchCriteria.setGraphId(graphId)
            val mc = MetadataCriterion.create(util.Arrays.asList(new Filter(SystemProperties.IL_UNIQUE_ID.name, SearchConditions.OP_IN, identifiers)))
            searchCriteria.addMetadata(mc)
            searchCriteria.setCountQuery(false)
            searchCriteria.getParams.put("identifiers", identifiers)
            oec.graphService.getNodeByUniqueIds(graphId, searchCriteria).recoverWith {
                case e: Exception =>
                    Future.failed(new ServerException(GraphErrorCodes.ERR_GRAPH_PROCESSING_ERROR.toString, "Unable To Fetch Nodes From Graph. Exception is: " + e.getMessage))
            }
        }
    }
}
