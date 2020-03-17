package org.sunbird.channel.managers

import java.util
import java.util.concurrent.CompletionException
import java.util.Optional

import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.util.ChannelConstants
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode, ServerException}
import org.sunbird.common.{Platform, dto}
import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.Unirest
import org.apache.commons.collections4.CollectionUtils
import org.sunbird.common.JsonUtils
import org.sunbird.graph.utils.ScalaJsonUtils

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

object ChannelManager {

  def channelLicenseCache(response: Response, request: Request): Unit = {
    if (!ResponseHandler.checkError(response) && response.getResult.containsKey(ChannelConstants.NODE_ID) && request.getRequest.containsKey(ChannelConstants.DEFAULT_LICENSE))
      RedisCache.set(ChannelConstants.CHANNEL_LICENSE_CACHE_PREFIX + response.getResult.get(ChannelConstants.NODE_ID) + ChannelConstants.CHANNEL_LICENSE_CACHE_SUFFIX, request.getRequest.get(ChannelConstants.DEFAULT_LICENSE).asInstanceOf[String], 0)
  }

  def getAllFrameworkList()(implicit ec: ExecutionContext): util.List[String] = {
    val url: String = if (Platform.config.hasPath("composite.search.url")) Platform.config.getString("composite.search.url") else "https://dev.sunbirded.org/action/composite/v3/search"
    val httpResponse: HttpResponse[String] = Unirest.post(url).header("Content-Type", "application/json").body("{ \"request\": { \n      \"filters\":{\n      \t\"objectType\":\"framework\",\n      \t\"status\":\"Live\"\n      },\n      \"fields\":[\"name\"]\n    }\n}").asString
    if (httpResponse.getStatus == 200) {
      val response: Response = JsonUtils.deserialize(httpResponse.getBody, classOf[Response])
      if (response.getResult.get("count").asInstanceOf[Integer] > 0 && CollectionUtils.isNotEmpty(response.getResult.getOrDefault("Framework", new util.ArrayList[AnyRef]()).asInstanceOf[util.List[util.Map[String, AnyRef]]])) {
        val frameworkList = new util.ArrayList[String]()
        response.getResult.get("Framework").asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.foreach(framework => {
          frameworkList.add(framework.get("name").asInstanceOf[String])
        })
        frameworkList
      } else
        new util.ArrayList[String]()
    } else
      throw new ServerException("ERR_FETCHING_FRAMEWORK", "Error while fetching framework.")
  }

  def validateTranslationMap(request: Request) = {
    val translations: util.Map[String, AnyRef] = Optional.ofNullable(request.get("translations").asInstanceOf[util.HashMap[String, AnyRef]]).orElse(new util.HashMap[String, AnyRef]())
    if (translations.isEmpty) request.getRequest.remove("translations")
    else {
      val languageCodes = if(Platform.config.hasPath("language.graph_ids")) Platform.config.getStringList("language.graph_ids") else new util.ArrayList[String]()
      if (translations.asScala.exists(entry => !languageCodes.contains(entry._1)))
        throw new ClientException("ERR_INVALID_LANGUAGE_CODE", "Please Provide Valid Language Code For translations. Valid Language Codes are : " + languageCodes)
    }
  }
}
