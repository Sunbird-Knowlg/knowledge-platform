package org.sunbird.actors

import java.util
import java.util.concurrent.CompletionException

import javax.inject.Inject
import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.manager.BaseTaxonomyActor

import scala.collection.JavaConverters._
import scala.concurrent.Future

class  FrameworkActor @Inject() (implicit oec: OntologyEngineContext) extends BaseTaxonomyActor {

    val CHANNEL_SCHEMA_NAME: String = "channel"
    val CHANNEL_VERSION: String = "1.0"
    val cacheEnabled = if(Platform.config.hasPath("framework.cache.enabled")) Platform.config.getBoolean("framework.cache.enabled") else false
    val CACHE_PREFIX = "fw_"
    val cacheTtl = if(Platform.config.hasPath("framework.cache.ttl"))Platform.config.getInt("framework.cache.ttl") else 86400

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

    @throws[Exception]
    def read(request:Request):Future[Response] = {
        val identifier: String = request.get("identifier").asInstanceOf[String]
        val cacheKey = if(!request.get("categories").asInstanceOf[List[String]].isEmpty) getFrameworkCacheKey(request) else identifier
        RedisCache.getAsync(cacheKey, cacheTtl, Option(frameworkCacheHandler), Option(request)).map(cachedHierarchy => {
            ResponseHandler.OK().put("framework", JsonUtils.deserialize(cachedHierarchy, classOf[util.Map[String, AnyRef]]))
        }) recoverWith {case e:CompletionException => throw e.getCause}
    }
    
    val frameworkCacheHandler = (request: AnyRef) => {
        val req = request.asInstanceOf[Request]
        val identifier: String = req.get("identifier").asInstanceOf[String]
        req.put("fields", util.Arrays.asList("hierarchy"))
        DataNode.read(req).map( node => {
            val frameworkData = JsonUtils.deserialize(node.getMetadata.getOrDefault("hierarchy", "").asInstanceOf[String], classOf[util.Map[String,AnyRef]])
            if(MapUtils.isEmpty(frameworkData)){
                throw new ResourceNotFoundException("ERR_INVALID_FRAMEWORK", "Framework not found : ", identifier)
            } else {
                JsonUtils.serialize(getFilteredFramework(frameworkData, req))
            }
        })
    }


    def removeAssociations(terms: util.List[util.Map[String, AnyRef]], categoryNames: List[String]):util.List[util.Map[String, AnyRef]] = {
        if(!terms.asScala.toList.isEmpty) {
            terms.asScala.toList.map(term => {
                if(term.containsKey("associations")) {
                    term.put("associations", 
                        term.getOrDefault("associations", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala
                    .toList.filter(p => categoryNames.contains(p.get("category"))).asJava)
                    if(term.containsKey("children")) {
                        term.put("children", removeAssociations(term.getOrDefault("children", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]], categoryNames))
                    }
                }
                term
            }).asJava
        }
        terms
    }

    def getFilteredFramework(frameworkData: util.Map[String, AnyRef], request: Request): AnyRef = {
        val categoryNames = request.get("categories").asInstanceOf[List[String]]
        if(categoryNames.isEmpty) {
            frameworkData
        } else{
            val categories = frameworkData.getOrDefault("categories", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
            frameworkData.put("categories", categories.asScala.toList.filter(category => categoryNames.contains(category.get("code")))
                .map(category => {
                    category.put("terms", removeAssociations(category.getOrDefault("terms", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]], categoryNames))
                    category
                }).asJava)
            frameworkData
        }
    }


    private def getFrameworkCacheKey(request:Request): String = {
        val identifier: String = request.get("identifier").asInstanceOf[String]
        val categories: List[String] = request.get("categories").asInstanceOf[List[String]]
        if(cacheEnabled && !categories.isEmpty) {
            val sortedCategories = categories.sorted
            CACHE_PREFIX + identifier.toLowerCase + "_" + sortedCategories.mkString("_")
        } else ""
    }
    
    private def updateNode(request: Request, extraFn: Option[(Node, java.util.Map[String, AnyRef]) => Unit] = None): Future[Response] = {
        DataNode.update(request, extraFn).map(node => {
            ResponseHandler.OK.put("identifier", node.getIdentifier)
        }) recoverWith { case e: CompletionException => throw e.getCause }
    }
}
