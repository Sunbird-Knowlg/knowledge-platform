package org.sunbird.actors

import java.util.concurrent.CompletionException

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode

import scala.concurrent.{ExecutionContext, Future}

class  FrameworkActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {

    implicit val ec: ExecutionContext = getContext().dispatcher

    override def onReceive(request: Request): Future[Response] = request.getOperation match {
        case "createFramework" => create(request)
        case "updateFramework" => update(request)
        case "readFramework" => read(request)
        case "retireFramework" => retire(request)
        case _ => ERROR(request.getOperation)
    }

    def create(request: Request): Future[Response] = {
        //TODO:  include RequestUtils.restrictproperties method
        if (request.getRequest.containsKey("code"))
            request.put("identifier", request.getRequest.get("code").asInstanceOf[String])
        else
            throw new ClientException("ERR_CODE_IS_REQUIRED", "Code is required for creating a channel")
        DataNode.create(request).map(node => {
            val response = ResponseHandler.OK
            response.put("identifier", node.getIdentifier)
            response
        })
    }

    def update(request: Request): Future[Response] = {
        val requestChannelId = request.get("channel")
        request.put("identifier", request.getContext.get("identifier"))
        DataNode.read(request).map(resp => {
            if (resp != null) {
                if (requestChannelId == resp.getMetadata.get("channel")) {
                    DataNode.update(request).map(node => {
                        val response = ResponseHandler.OK
                        response.put("identifier", node.getIdentifier)
                        response
                    })
                }
                else {
                    throw new ClientException("ERR_SERVER_ERROR_UPDATE_FRAMEWORK", "Invalid Request. Channel Id Not Matched." + requestChannelId)
                }
            }
            else {
                throw new ResourceNotFoundException("ERR_FRAMEWORK_NOT_FOUND", "Framework Not Found With Id : " + request.get("identifier"))
            }
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }


    def retire(request: Request): Future[Response] = {
        val ownerChannelId = request.get("channel")
        request.put("identifier", request.getContext.get("identifier"))
        DataNode.read(request).map(resp => {
            if (resp != null) {
                if (ownerChannelId == resp.getMetadata.get("channel")) {
                    if (resp.getMetadata.get("status") == "Live") {
                        request.put("status", "Retired")
                        DataNode.update(request).map(node => {
                            val response = ResponseHandler.OK
                            response.put("identifier", node.getIdentifier)
                            response
                        })
                    }
                    else
                        throw new ClientException("ERR_SERVER_ERROR_UPDATE_FRAMEWORK", "Cannot update.Status is already set to Retired.")

                }
                else {
                    throw new ClientException("ERR_SERVER_ERROR_UPDATE_FRAMEWORK", "Invalid Request. Channel Id Not Matched." + ownerChannelId)
                }
            }
            else {
                throw new ResourceNotFoundException("ERR_FRAMEWORK_NOT_FOUND", "Framework Not Found With Id : " + request.get("identifier"))
            }
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }

    }
    def read(request:Request):Future[Response] = {
        val redisHierarchy = RedisCache.get(request.get("identifier").asInstanceOf[String], categories => request.get("categories").asInstanceOf[String], 0)
        if (StringUtils.isEmpty(redisHierarchy)) {
            DataNode.read(request).map(node => {
                if (node == null) {
                    throw new ResourceNotFoundException("ERR_DATA_NOT_FOUND", "Data not found with id : " + request.get("identifier"))
                }
                val resp = ResponseHandler.OK()
                resp.put("identifier", node.getIdentifier)
                resp.put("nodeType", node.getNodeType)
                resp.put("objectType", node.getObjectType)
                resp.put("metadata", node.getMetadata)
                resp
            }) recoverWith { case e: CompletionException => throw e.getCause }
        }
        else {
            val resp = ResponseHandler.OK
            resp.put("identifier", redisHierarchy)
            Future(resp)
        }

    }
}
