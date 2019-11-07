package org.sunbird.graph.nodes

import java.util

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.engine.RelationManager
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.relations.{IRelation, RelationHandler}
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.graph.service.operation.NodeAsyncOperations
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
                    updateRelations(graphId, node, request.getContext))
                futureList.map(list => result)
            }).flatMap(f => f)
        }).flatMap(f => f)
    }

    @throws[Exception]
    def read(request: Request)(implicit ec: ExecutionContext): Future[Node] = {
        val resultNode: Future[Node] = DefinitionNode.getNode(request)
        resultNode.map(node => {
            val fields: List[String] = request.get("fields").asInstanceOf[util.ArrayList[String]].toList
            val extPropNameList = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String], request.getObjectType)
            val finalNodeFuture: Future[Node] = if (CollectionUtils.isNotEmpty(extPropNameList) && null != fields && fields.exists(field => extPropNameList.contains(field)))
                populateExternalProperties(fields, node, request, extPropNameList)
            else
                Future(node)
            val isBackwardCompatible = if (Platform.config.hasPath("content.tagging.backward_enable")) Platform.config.getBoolean("content.tagging.backward_enable") else false
            if(isBackwardCompatible)
                finalNodeFuture.map(node => updateContentTaggedProperty(node)).flatMap(f => f)
            else
                finalNodeFuture
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
    
    private def updateRelations(graphId: String, node: Node, context: util.Map[String, AnyRef])(implicit ec: ExecutionContext) : Future[Response] = {
        val relations: util.List[Relation] = node.getAddedRelations
        if (CollectionUtils.isNotEmpty(relations)) {
            val relationList: List[IRelation] = relations.toList.map(relation =>
                RelationHandler.getRelation(graphId, node.getRelationNode(relation.getStartNodeId),
                    relation.getRelationType, node.getRelationNode(relation.getEndNodeId), new util.HashMap()))
            val request: Request = new Request
            request.setContext(context)
            request.put("relations", relationList)
            RelationManager.createNewRelations(request)
        } else {
            Future(new Response)
        }
    }

    /**
      * To support backward compatibility to mobile team.
      * @param node
      * @param ec
      * @return
      */
    @Deprecated
    private def updateContentTaggedProperty(node: Node)(implicit ec:ExecutionContext): Future[Node] = {
        val contentTaggedKeys = if(Platform.config.hasPath("content.tagging.property"))
            (for (prop <- Platform.config.getString("content.tagging.property").split(",")) yield prop ) (collection.breakOut)
        else
            List("subject", "medium")
        contentTaggedKeys.map(prop => populateContentTaggedProperty(prop, node.getMetadata.getOrDefault(prop, ""), node))
        Future{node}
    }

    private def populateContentTaggedProperty(key:String, value: Any, node:Node)(implicit ec: ExecutionContext): Future[Node] = {
        val contentValue:String = value match {
            case v: String => v.asInstanceOf[String]
            case v: List[Any] => v.head.asInstanceOf[String]
            case v: Array[String] => v.head
        }
        if(!StringUtils.isAllBlank(contentValue))
            node.getMetadata.put(key, contentValue)
        else
            node.getMetadata.remove(key)
        Future(node)
    }

    private def populateExternalProperties(fields: List[String], node: Node, request: Request, externalProps: List[String])(implicit ec: ExecutionContext): Future[Node] = {
        val externalPropsResponse = ExternalPropsManager.fetchProps(request, externalProps.filter(prop => fields.contains(prop)))
        externalPropsResponse.map(response => {
            node.getMetadata.putAll(response.getResult)
            Future{node}
        }).flatMap(f => f)
    }
}