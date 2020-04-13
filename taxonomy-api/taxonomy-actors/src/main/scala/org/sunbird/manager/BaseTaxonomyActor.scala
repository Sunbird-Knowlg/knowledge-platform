package org.sunbird.manager

import java.util
import java.util.concurrent.CompletionException

import org.apache.commons.collections4.MapUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.Platform
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

abstract class BaseTaxonomyActor (implicit oec: OntologyEngineContext) extends BaseActor  {

    implicit val ec: ExecutionContext = getContext().dispatcher
    val languageCodes:List[String] = if(Platform.config.hasPath("languageCodes")) Platform.config.getObject("languageCodes").values().toArray.toList.map(obj => obj.asInstanceOf[String]) else List()
        
    def validateTranslations(request: Request) = {
        val translations: util.Map[String, AnyRef] = request.getRequest.getOrDefault("translations", new util.HashMap()).asInstanceOf[util.Map[String, AnyRef]]
        if(MapUtils.isNotEmpty(translations) && !translations.keySet().asScala.toList.filterNot(p => languageCodes.contains(p)).isEmpty) {
               throw new ClientException("ERR_INVALID_LANGUAGE_CODE",
                   "Please Provide Valid Language Code For translations. Valid Language Codes are : " + languageCodes)
        }
    }
    
    def validateObject(objectId: String, schemaName: String, version: String): Future[Boolean] = {
        val req = new Request()
        req.setContext(new util.HashMap[String, AnyRef](){{ put("schemaName", schemaName); put("version", version); put("graphId", "domain") }})
        req.setRequest(new util.HashMap[String, AnyRef](){{ put("identifier", objectId) }})
        DataNode.read(req).map(node => {
            true
        }) recoverWith { case e: CompletionException => Future{false}}
        
    }
}
