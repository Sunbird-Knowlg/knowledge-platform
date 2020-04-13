package org.sunbird.actors

import java.util.concurrent.CompletionException

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.manager.BaseTaxonomyActor

import scala.concurrent.{ExecutionContext, Future}

class  FrameworkActor @Inject() (implicit oec: OntologyEngineContext) extends BaseTaxonomyActor {

    val CHANNEL_SCHEMA_NAME: String = "channel"
    val CHANNEL_VERSION: String = "1.0"

    override def onReceive(request: Request): Future[Response] = request.getOperation match {
        case "createFramework" => create(request)
        case "updateFramework" => update(request)
        case "readFramework" => read(request)
        case "retireFramework" => retire(request)
        case _ => ERROR(request.getOperation)
    }

    def create(request: Request): Future[Response] = {
        val identifier = request.getRequest.getOrDefault("code", null);
        if(null == identifier) throw new ClientException("ERR_CODE_IS_REQUIRED", "Code is required for creating a channel")
        validateTranslations(request)
        val channelId = request.getContext.get("channel").asInstanceOf[String]
        validateObject(channelId, CHANNEL_SCHEMA_NAME, CHANNEL_VERSION).map(isValid => {
            if(isValid){
                DataNode.create(request).map(node => {
                    val response = ResponseHandler.OK
                    response.put("identifier", node.getIdentifier)
                    response
                })
            } else throw new ClientException("ERR_INVALID_CHANNEL_ID", "Invalid Channel Id. Channel doesn't exist.")
        }).flatMap(f =>f)
        
    }

    def update(request: Request): Future[Response] = {
        val requestChannelId = request.get("channel").asInstanceOf[String]
        validateTranslations(request)
        request.put("identifier", request.getContext.get("identifier"))
        DataNode.read(request).map(resp => {
            if (StringUtils.equalsIgnoreCase(requestChannelId, resp.getMetadata.get("channel").asInstanceOf[String])) {
                DataNode.update(request).map(node => {
                    val response = ResponseHandler.OK
                    response.put("identifier", node.getIdentifier)
                    response
                })
            }
            else {
                throw new ClientException("ERR_INVALID_CHANNEL_ID", "Invalid Request. Channel Id Not Matched." + requestChannelId)
            }
        }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
    }


    def retire(request: Request): Future[Response] = {
        val channelId = request.getContext.getOrDefault("channel","").asInstanceOf[String]
        request.put("identifier", request.getContext.get("identifier"))
        DataNode.read(request).map(resp => {
            if (StringUtils.equalsIgnoreCase(channelId, resp.getMetadata.get("channel").asInstanceOf[String])) {
                request.put("status", "Retired")
                DataNode.update(request).map(node => {
                    val response = ResponseHandler.OK
                    response.put("identifier", node.getIdentifier)
                    response
                })
            }
            else {
                throw new ClientException("ERR_SERVER_ERROR_UPDATE_FRAMEWORK", "Invalid Request. Channel Id Not Matched." + channelId)
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
