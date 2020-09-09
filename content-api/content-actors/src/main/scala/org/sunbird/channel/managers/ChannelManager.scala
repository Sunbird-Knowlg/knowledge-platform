package org.sunbird.channel.managers

import java.util
import java.util.Optional

import org.sunbird.common.dto.{Request, Response}
import org.sunbird.util.ChannelConstants
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.common.Platform
import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.Unirest
import org.sunbird.common.JsonUtils
import org.sunbird.graph.utils.ScalaJsonUtils

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

object ChannelManager {

  def channelLicenseCache(request: Request, identifier: String): Unit = {
    if (request.getRequest.containsKey(ChannelConstants.DEFAULT_LICENSE))
      RedisCache.set(ChannelConstants.CHANNEL_LICENSE_CACHE_PREFIX + identifier + ChannelConstants.CHANNEL_LICENSE_CACHE_SUFFIX, request.getRequest.get(ChannelConstants.DEFAULT_LICENSE).asInstanceOf[String], 0)
  }

  def getAllFrameworkList(): util.List[util.Map[String, AnyRef]] = {
    val url: String = if (Platform.config.hasPath("composite.search.url")) Platform.config.getString("composite.search.url") else "https://dev.sunbirded.org/action/composite/v3/search"
    val httpResponse: HttpResponse[String] = Unirest.post(url).header("Content-Type", "application/json").body("""{"request":{"filters":{"objectType":"Framework","status":"Live"},"fields":["name","code","objectType","identifier"]}}""").asString
    if (200 != httpResponse.getStatus)
      throw new ServerException("ERR_FETCHING_FRAMEWORK", "Error while fetching framework.")
    val response: Response = JsonUtils.deserialize(httpResponse.getBody, classOf[Response])
    response.getResult.getOrDefault("Framework", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
  }

  def validateTranslationMap(request: Request) = {
    val translations: util.Map[String, AnyRef] = Optional.ofNullable(request.get("translations").asInstanceOf[util.HashMap[String, AnyRef]]).orElse(new util.HashMap[String, AnyRef]())
    if (translations.isEmpty) request.getRequest.remove("translations")
    else {
      val languageCodes = if(Platform.config.hasPath("platform.language.codes")) Platform.config.getStringList("platform.language.codes") else new util.ArrayList[String]()
      if (translations.asScala.exists(entry => !languageCodes.contains(entry._1)))
        throw new ClientException("ERR_INVALID_LANGUAGE_CODE", "Please Provide Valid Language Code For translations. Valid Language Codes are : " + languageCodes)
    }
  }

  def validateObjectCategory(request: Request) = {
    val categoryKeyList: util.List[String] = ChannelConstants.categoryKeyList
    if (!util.Collections.disjoint(request.getRequest.keySet(), categoryKeyList)) {
      val url: String = if (Platform.config.hasPath("composite.search.url")) Platform.config.getString("composite.search.url") else "https://dev.sunbirded.org/action/composite/v3/search"
      val httpResponse: HttpResponse[String] = Unirest.post(url).header("Content-Type", "application/json").body("""{"request":{"filters":{"objectType":"ObjectCategory"},"fields":["name"]}}""").asString
      if (200 != httpResponse.getStatus)
        throw new ServerException("ERR_FETCHING_OBJECT_CATEGORY", "Error while fetching object category.")
      val response: Response = JsonUtils.deserialize(httpResponse.getBody, classOf[Response])
      val objectCategoryList: util.List[util.Map[String, AnyRef]] = response.getResult.getOrDefault(ChannelConstants.OBJECT_CATEGORY, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]]
      val masterCategoriesList: util.List[String] = objectCategoryList.map(a => a.get("name").asInstanceOf[String]).toList
      val errMsg: ListBuffer[String] = ListBuffer()
      if(request.getRequest.containsKey(ChannelConstants.CONTENT_PRIMARY_CATEGORIES)){
        val requestedContentCategoryList: util.List[String] = request.getRequest.get(ChannelConstants.CONTENT_PRIMARY_CATEGORIES).asInstanceOf[util.ArrayList[String]]
        if (util.Collections.disjoint(masterCategoriesList, requestedContentCategoryList)) {
          errMsg += "content"
        }
      }
      if(request.getRequest.containsKey(ChannelConstants.COLLECTION_PRIMARY_CATEGORIES)){
        val requestedCollectionCategoryList: util.List[String] = request.getRequest.get(ChannelConstants.COLLECTION_PRIMARY_CATEGORIES).asInstanceOf[util.ArrayList[String]]
        if (util.Collections.disjoint(masterCategoriesList, requestedCollectionCategoryList)) {
          errMsg += "collection"
        }
      }
      if(request.getRequest.containsKey(ChannelConstants.ASSET_PRIMARY_CATEGORIES)){
        val requestedAssetCategoryList: util.List[String] = request.getRequest.get(ChannelConstants.ASSET_PRIMARY_CATEGORIES).asInstanceOf[util.ArrayList[String]]
        if (util.Collections.disjoint(masterCategoriesList, requestedAssetCategoryList)) {
          errMsg += "asset"
        }
      }
      if(errMsg.nonEmpty){
        throw new ClientException("ERR_CATEGORY_OBJECT_NOT_PRESENT", "Please provide valid primary category for : " + errMsg.mkString(", "))
      }
    }
  }
}
