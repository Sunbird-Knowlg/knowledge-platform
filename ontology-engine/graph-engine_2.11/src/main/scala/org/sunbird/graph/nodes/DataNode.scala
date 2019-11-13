package org.sunbird.graph.nodes

import java.util

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.graph.service.operation.{GraphAsyncOperations, NodeAsyncOperations}
import org.sunbird.parseq.Task

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}


object DataNode {
    @throws[Exception]
    def create(request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
        DefinitionNode.validate(request).map(node => {
            val response = NodeAsyncOperations.addNode(graphId, node)
            response.map(result => {
                val futureList = Task.parallel[Response](
                    saveExternalProperties(node.getIdentifier, node.getExternalData, request.getContext, request.getObjectType),
                    createRelations(graphId, node, request.getContext))
                futureList.map(list => result)
            }).flatMap(f => f)
        }).flatMap(f => f)
    }

    @throws[Exception]
    def update(request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val graphId: String = request.getContext.get("graph_id").asInstanceOf[String]
        val identifier: String = request.getContext.get("identifier").asInstanceOf[String]
        DefinitionNode.validate(identifier, request).map(node => {
            val response = NodeAsyncOperations.upsertNode(graphId, node, request)
            response.map(result => {
                val futureList = Task.parallel[Response](
                    saveExternalProperties(node.getIdentifier, node.getExternalData, request.getContext, request.getObjectType),
                    updateRelations(graphId, node, request.getContext))
                futureList.map(list => result)
            }).flatMap(f => f)
        }).flatMap(f => f)
    }

    private def saveExternalProperties(identifier: String, externalProps: util.Map[String, AnyRef], context: util.Map[String, AnyRef], objectType: String)(implicit ec: ExecutionContext): Future[Response] = {
        if (MapUtils.isNotEmpty(externalProps)) {
            externalProps.put("identifier", identifier)
            val request = new Request(context, externalProps, "", objectType)
            ExternalPropsManager.saveProps(request)
        } else {
            Future(new Response)
        }
    }
    
    private def createRelations(graphId: String, node: Node, context: util.Map[String, AnyRef])(implicit ec: ExecutionContext) : Future[Response] = {
        val relations: util.List[Relation] = node.getAddedRelations
        if (CollectionUtils.isNotEmpty(relations)) {
            GraphAsyncOperations.createRelation(graphId,getRelationMap(relations))
        } else {
            Future(new Response)
        }
    }

    private def updateRelations(graphId: String, node: Node, context: util.Map[String, AnyRef])(implicit ec: ExecutionContext) : Future[Response] = {
        val request: Request = new Request
        request.setContext(context)

        if (CollectionUtils.isEmpty(node.getAddedRelations) && CollectionUtils.isEmpty(node.getDeletedRelations)) {
            Future(new Response)
        } else {
            if (CollectionUtils.isNotEmpty(node.getAddedRelations))
                GraphAsyncOperations.createRelation(graphId,getRelationMap(node.getAddedRelations))
            if (CollectionUtils.isNotEmpty(node.getDeletedRelations))
               GraphAsyncOperations.removeRelation(graphId, getRelationMap(node.getDeletedRelations))
            Future(new Response)
        }
    }

    private def getRelationMap(relations:util.List[Relation]):java.util.List[util.Map[String, AnyRef]]={
        val list = new util.ArrayList[util.Map[String, AnyRef]]
        for (rel <- relations) {
            if ((StringUtils.isNotBlank(rel.getStartNodeId) && StringUtils.isNotBlank(rel.getEndNodeId)) && StringUtils.isNotBlank(rel.getRelationType)) {
                val map = new util.HashMap[String, AnyRef]
                map.put("startNodeId", rel.getStartNodeId)
                map.put("endNodeId", rel.getEndNodeId)
                map.put("relation", rel.getRelationType)
                if (MapUtils.isNotEmpty(rel.getMetadata)) map.put("relMetadata", rel.getMetadata)
                else map.put("relMetadata", new util.HashMap[String,AnyRef]())
                list.add(map)
            }
            else throw new ClientException("ERR_INVALID_RELATION_OBJECT", "Invalid Relation Object Found.")
        }
        list
    }
}