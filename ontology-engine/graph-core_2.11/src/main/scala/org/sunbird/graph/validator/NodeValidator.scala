package org.sunbird.graph.validator

import java.util
import java.util.concurrent.CompletionException

import org.sunbird.common.exception.{ResourceNotFoundException, ServerException}
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Filter, MetadataCriterion, Node, SearchConditions, SearchCriteria}
import org.sunbird.graph.exception.GraphErrorCodes
import org.sunbird.graph.service.operation.SearchAsyncOperations

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

import scala.concurrent.{ExecutionContext, Future}

object NodeValidator {

    def validate(graphId: String, identifiers: util.List[String])(implicit ec: ExecutionContext): Future[util.Map[String, Node]] = {
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

    private def getDataNodes(graphId: String, identifiers: util.List[String]) = {
        val searchCriteria = new SearchCriteria
        val mc = if (identifiers.size == 1)
            MetadataCriterion.create(util.Arrays.asList(new Filter(SystemProperties.IL_UNIQUE_ID.name, SearchConditions.OP_EQUAL, identifiers.get(0))))
        else
            MetadataCriterion.create(util.Arrays.asList(new Filter(SystemProperties.IL_UNIQUE_ID.name, SearchConditions.OP_IN, identifiers), new Filter("status", SearchConditions.OP_NOT_EQUAL, "Retired")))
        searchCriteria.addMetadata(mc)
        searchCriteria.setCountQuery(false)
        try {
            val nodes = SearchAsyncOperations.getNodeByUniqueIds(graphId, searchCriteria)
            nodes
        } catch {
            case e: Exception =>
                throw new ServerException(GraphErrorCodes.ERR_GRAPH_PROCESSING_ERROR.toString, "Unable To Fetch Nodes From Graph. Exception is: " + e.getMessage)
        }
    }
}
