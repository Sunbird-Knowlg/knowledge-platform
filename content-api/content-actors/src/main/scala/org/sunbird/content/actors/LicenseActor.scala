package org.sunbird.content.actors

import java.util

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.Slug
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.common.util.WorkflowLogger
import org.sunbird.content.util.LicenseConstants
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.util.RequestUtil

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

class LicenseActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {
    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = {
        request.getOperation match {
            case LicenseConstants.CREATE_LICENSE => create(request)
            case LicenseConstants.READ_LICENSE => read(request)
            case LicenseConstants.UPDATE_LICENSE => update(request)
            case LicenseConstants.RETIRE_LICENSE => retire(request)
            case _ => ERROR(request.getOperation)
        }
    }


    @throws[Exception]
    private def create(request: Request): Future[Response] = {
        // STAGE 1: Controller → Actor
        val context = new java.util.HashMap[String, Object]()
        context.put("operation", LicenseConstants.CREATE_LICENSE)
        context.put("objectType", "License")
        context.put("requestKeys", request.getRequest.keySet().asScala.mkString(", "))
        WorkflowLogger.logStage(1, "License Actor Received", LicenseConstants.CREATE_LICENSE, 
            "LicenseController.create()", "Validation → DataNode.create → Response", context)
        
        RequestUtil.restrictProperties(request)
        
        // STAGE 2: Validation
        val validationContext = new java.util.HashMap[String, Object]()
        if (request.getRequest.containsKey("name")) {
            val name = request.getRequest.get("name").asInstanceOf[String]
            val slug = Slug.makeSlug(name)
            request.getRequest.put("identifier", slug)
            validationContext.put("name", name)
            validationContext.put("generatedIdentifier", slug)
        }
        validationContext.put("validationStatus", "PASSED")
        WorkflowLogger.logStage(2, "Validation Complete", LicenseConstants.CREATE_LICENSE, 
            "Actor Received", "DataNode.create → JanusGraph", validationContext)
        
        // STAGE 3: DataNode.create → Graph Operations
        context.clear()
        context.put("identifier", request.getRequest.getOrDefault("identifier", "PENDING"))
        context.put("targetOperation", "JanusGraph CREATE")
        context.put("graphId", request.getContext.getOrDefault("graphId", "domain"))
        WorkflowLogger.logStage(3, "Calling DataNode.create", LicenseConstants.CREATE_LICENSE, 
            "Validation Complete", "GraphAsyncOperations.addNode → JanusGraph → Response", context)
        
        DataNode.create(request).map(node => {
            // STAGE 4: Response received from graph
            val responseContext = new java.util.HashMap[String, Object]()
            responseContext.put("createdIdentifier", node.getIdentifier)
            responseContext.put("nodeId", node.getIdentifier)
            responseContext.put("graphId", node.getGraphId)
            responseContext.put("objectType", node.getObjectType)
            WorkflowLogger.logStage(4, "DataNode Created Successfully", LicenseConstants.CREATE_LICENSE, 
                "JanusGraph Response", "Building Response → Return to Controller", responseContext)
            
            ResponseHandler.OK.put("identifier", node.getIdentifier).put("node_id", node.getIdentifier)
        }).recover {
            case ex: Exception =>
                val errorContext = new java.util.HashMap[String, Object]()
                errorContext.put("errorMessage", ex.getMessage)
                errorContext.put("errorClass", ex.getClass.getSimpleName)
                errorContext.put("stackTrace", ex.getStackTrace.take(5).mkString("\n"))
                WorkflowLogger.logError(4, "DataNode.create Failed", LicenseConstants.CREATE_LICENSE, 
                    ex.getMessage, errorContext)
                throw ex
        }
    }

    @throws[Exception]
    private def read(request: Request): Future[Response] = {
        val fields: util.List[String] = request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null")).toList.asJava
        request.getRequest.put("fields", fields)
        DataNode.read(request).map(node => {
            if (NodeUtil.isRetired(node)) ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name, "License not found with identifier: " + node.getIdentifier)
            val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
            ResponseHandler.OK.put("license", metadata)
        })
    }

    @throws[Exception]
    private def update(request: Request): Future[Response] = {
        RequestUtil.restrictProperties(request)
        request.getRequest.put("status", "Live")
        DataNode.update(request).map(node => {
            ResponseHandler.OK.put("node_id", node.getIdentifier).put("identifier", node.getIdentifier)
        })
    }

    @throws[Exception]
    private def retire(request: Request): Future[Response] = {
        request.getRequest.put("status", "Retired")
        DataNode.update(request).map(node => {
            ResponseHandler.OK.put("node_id", node.getIdentifier).put("identifier", node.getIdentifier)
        })
    }

}
