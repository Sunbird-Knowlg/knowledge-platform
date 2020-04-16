package org.sunbird.actors

import java.util.concurrent.CompletionException

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.manager.BaseTaxonomyActor

import scala.concurrent.{ExecutionContext, Future}

class  FrameworkActor @Inject() (implicit oec: OntologyEngineContext) extends BaseTaxonomyActor {

    val CHANNEL_SCHEMA_NAME: String = "channel"
    val CHANNEL_VERSION: String = "1.0"

    val validateFn = (node: Node, input: java.util.Map[String, AnyRef]) => {
        val reqChannel = input.getOrDefault("channel", "").asInstanceOf[String]
        val nodeChannel = node.getMetadata.getOrDefault("channel", "").asInstanceOf[String]
        if (!StringUtils.equals(reqChannel, nodeChannel)) {
            throw new ClientException("ERR_INVALID_CHANNEL_ID", "Invalid Request. Channel Id not matched: " + reqChannel)
        }
    }

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
        request.getRequest.put("identifier", identifier)
        validateTranslations(request)
        val channelId = request.getContext.get("channel").asInstanceOf[String]
        validateObject(channelId, CHANNEL_SCHEMA_NAME, CHANNEL_VERSION).map(isValid => {
            if(isValid){
                DataNode.create(request).map(node => {
                    ResponseHandler.OK.put("identifier", node.getIdentifier)
                })
            } else throw new ClientException("ERR_INVALID_CHANNEL_ID", "Invalid Channel Id. Channel doesn't exist.")
        }).flatMap(f =>f)
        
    }

    def update(request: Request): Future[Response] = {
        validateTranslations(request)
        request.put("identifier", request.getContext.get("identifier"))
        updateNode(request, Option(validateFn))
    }

    def retire(request: Request): Future[Response] = {
        request.put("identifier", request.getContext.get("identifier"))
        request.put("status", "Retired")
        updateNode(request, Option(validateFn))
    }

    def read(request:Request):Future[Response] = {
        val redisHierarchy = RedisCache.get(request.get("identifier").asInstanceOf[String], categories => request.get("categories").asInstanceOf[String], 0)
        if (StringUtils.isEmpty(redisHierarchy)) {
            DataNode.read(request).map(node => {
                if (node == null) {
                    throw new ResourceNotFoundException("ERR_DATA_NOT_FOUND", "Data not found with id : " + request.get("identifier"))
                }
                ResponseHandler.OK().put("identifier", node.getIdentifier)
                    .put("nodeType", node.getNodeType)
                    .put("objectType", node.getObjectType)
                    .put("metadata", node.getMetadata)
            }) recoverWith { case e: CompletionException => throw e.getCause }
        }
        else {
            val resp = ResponseHandler.OK
            resp.put("identifier", redisHierarchy)
            Future(resp)
        }
    }

    private def updateNode(request: Request, extraFn: Option[(Node, java.util.Map[String, AnyRef]) => Unit] = None): Future[Response] = {
        DataNode.update(request, extraFn).map(node => {
            ResponseHandler.OK.put("identifier", node.getIdentifier)
        }) recoverWith { case e: CompletionException => throw e.getCause }
    }
}
