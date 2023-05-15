package org.sunbird.mangers

import org.sunbird.common.Platform
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.validator.NodeValidator
import java.util.Optional
import java.util
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

object FrameworkManager {
  def validateTranslations(request: Request) = {
    val translations: util.Map[String, AnyRef] = Optional.ofNullable(request.get("translations").asInstanceOf[util.HashMap[String, AnyRef]]).orElse(new util.HashMap[String, AnyRef]())
    if (translations.isEmpty) request.getRequest.remove("translations")
    else {
      val languageCodes = Platform.getStringList("platform.language.codes", new util.ArrayList[String]())
      if (translations.asScala.exists(entry => !languageCodes.contains(entry._1)))
        throw new ClientException("ERR_INVALID_LANGUAGE_CODE", "Please Provide Valid Language Code For translations. Valid Language Codes are : " + languageCodes)
    }
  }

  def validateChannel(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext) ={
    val channelList: util.List[String] = new util.ArrayList[String](1)
    val channel = request.getRequest.getOrDefault("channel", "").asInstanceOf[String]
    channelList.add(channel)
    NodeValidator.validate(request.getContext.get("graph_id").asInstanceOf[String], channelList)
  }
}
