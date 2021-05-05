package org.sunbird.channel.managers

import java.util
import java.util.Optional
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.util.{ChannelConstants, HttpUtil}
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.common.Platform
import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.Unirest
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.JsonUtils

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

object ChannelManager {

  val CONTENT_PRIMARY_CATEGORIES: util.List[String] = Platform.getStringList("channel.content.primarycategories", new util.ArrayList[String]())
  val COLLECTION_PRIMARY_CATEGORIES: util.List[String] =  Platform.getStringList("channel.collection.primarycategories", new util.ArrayList[String]())
  val ASSET_PRIMARY_CATEGORIES: util.List[String] =  Platform.getStringList("channel.asset.primarycategories", new util.ArrayList[String]())
  val CONTENT_ADDITIONAL_CATEGORIES: util.List[String] =  Platform.getStringList("channel.content.additionalcategories", new util.ArrayList[String]())
  val COLLECTION_ADDITIONAL_CATEGORIES: util.List[String] = Platform.getStringList("channel.collection.additionalcategories", new util.ArrayList[String]())
  val ASSET_ADDITIONAL_CATEGORIES: util.List[String] =  Platform.getStringList("channel.asset.additionalcategories", new util.ArrayList[String]())
  implicit val httpUtil: HttpUtil = new HttpUtil

  def channelLicenseCache(request: Request, identifier: String): Unit = {
    if (request.getRequest.containsKey(ChannelConstants.DEFAULT_LICENSE))
      RedisCache.set(ChannelConstants.CHANNEL_LICENSE_CACHE_PREFIX + identifier + ChannelConstants.CHANNEL_LICENSE_CACHE_SUFFIX, request.getRequest.get(ChannelConstants.DEFAULT_LICENSE).asInstanceOf[String], 0)
  }

  def getAllFrameworkList(): util.List[util.Map[String, AnyRef]] = {
    val url: String = Platform.getString("composite.search.url", "https://dev.sunbirded.org/action/composite/v3/search")
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
      val languageCodes = Platform.getStringList("platform.language.codes", new util.ArrayList[String]())
      if (translations.asScala.exists(entry => !languageCodes.contains(entry._1)))
        throw new ClientException("ERR_INVALID_LANGUAGE_CODE", "Please Provide Valid Language Code For translations. Valid Language Codes are : " + languageCodes)
    }
  }

  def validateObjectCategory(request: Request) = {
    if (!util.Collections.disjoint(request.getRequest.keySet(), ChannelConstants.categoryKeyList)) {
      val masterCategoriesList: List[String] = getMasterCategoryList()
      val errMsg: ListBuffer[String] = ListBuffer()
      compareWithMasterCategory(request, masterCategoriesList, errMsg)
      if (errMsg.nonEmpty)
        throw new ClientException(ChannelConstants.ERR_VALIDATING_PRIMARY_CATEGORY, "Please provide valid : " + errMsg.mkString("[", ",", "]"))
    }
  }

  def compareWithMasterCategory(request: Request, masterCat: List[String], errMsg: ListBuffer[String]): Unit = {
    ChannelConstants.categoryKeyList.map(cat => {
      if (request.getRequest.containsKey(cat)) {
        val requestedCategoryList: util.List[String] = getRequestedCategoryList(request, cat)
        if (!masterCat.containsAll(requestedCategoryList))
          errMsg += cat
      }
    })
  }

  def getRequestedCategoryList(request: Request, cat: String): util.ArrayList[String] = {
    try {
      val requestedList = request.getRequest.get(cat).asInstanceOf[util.ArrayList[String]]
      if (requestedList.isEmpty)
        throw new ClientException(ChannelConstants.ERR_VALIDATING_PRIMARY_CATEGORY, "Empty list not allowed for " + cat)
      requestedList
    } catch {
      case e: ClassCastException => {
        throw new ClientException(ChannelConstants.ERR_VALIDATING_PRIMARY_CATEGORY, "Please provide valid list for " + cat)
      }
      case e: ClientException => {
        throw new ClientException(e.getErrCode, e.getMessage)
      }
      case e: Exception => {
        throw new ServerException(ChannelConstants.ERR_VALIDATING_PRIMARY_CATEGORY, e.getMessage)
      }
    }
  }

  def setPrimaryAndAdditionCategories(metadata: util.Map[String, AnyRef]): Unit = {
    metadata.putIfAbsent(ChannelConstants.CONTENT_PRIMARY_CATEGORIES, CONTENT_PRIMARY_CATEGORIES)
    metadata.putIfAbsent(ChannelConstants.COLLECTION_PRIMARY_CATEGORIES, COLLECTION_PRIMARY_CATEGORIES)
    metadata.putIfAbsent(ChannelConstants.ASSET_PRIMARY_CATEGORIES, ASSET_PRIMARY_CATEGORIES)
    metadata.putIfAbsent(ChannelConstants.CONTENT_ADDITIONAL_CATEGORIES, CONTENT_ADDITIONAL_CATEGORIES)
    metadata.putIfAbsent(ChannelConstants.COLLECTION_ADDITIONAL_CATEGORIES, COLLECTION_ADDITIONAL_CATEGORIES)
    metadata.putIfAbsent(ChannelConstants.ASSET_ADDITIONAL_CATEGORIES, ASSET_ADDITIONAL_CATEGORIES)
    val primaryCategories = getChannelPrimaryCategories(metadata.get("identifier").asInstanceOf[String])
    metadata.put("primaryCategories", primaryCategories)
    val additionalCategories = getAdditionalCategories()
    metadata.put("additionalCategories", additionalCategories)
  }

  def getAdditionalCategories()(implicit httpUtil: HttpUtil): java.util.List[String] = {
    val body = """{"request":{"filters":{"objectType":"ObjectCategory","visibility":["Default"]},"fields":["name","identifier"]}}"""
    val url: String = Platform.getString("composite.search.url", "https://dev.sunbirded.org/action/composite/v3/search")
    val httpResponse = httpUtil.post(url, body)
    if (200 != httpResponse.status) throw new ServerException("ERR_FETCHING_OBJECT_CATEGORY", "Error while fetching object categories for additional category list.")
    val response: Response = JsonUtils.deserialize(httpResponse.body, classOf[Response])
    val objectCategoryList: util.List[util.Map[String, AnyRef]] = response.getResult.getOrDefault(ChannelConstants.OBJECT_CATEGORY, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]]
    objectCategoryList.asScala.map(cat => cat.get("name").asInstanceOf[String]).asJava

  }

  def getChannelPrimaryCategories(channel: String)(implicit httpUtil: HttpUtil): java.util.List[java.util.Map[String, AnyRef]] = {
    val globalPCRequest = s"""{"request":{"filters":{"objectType":"ObjectCategoryDefinition", "visibility":["Default"]},"not_exists": "channel","fields":["name","identifier","targetObjectType"]}}"""
    val globalPrimaryCategories = getPrimaryCategories(globalPCRequest)
    val channelPCRequest = s"""{"request":{"filters":{"objectType":"ObjectCategoryDefinition", "visibility":["Default"], "channel": "$channel"},"fields":["name","identifier","targetObjectType"]}}"""
    val channelPrimaryCategories = getPrimaryCategories(channelPCRequest)
    if (CollectionUtils.isEmpty(channelPrimaryCategories))
      globalPrimaryCategories
    else {
      val idsToIgnore = channelPrimaryCategories.map(cat => cat.get("identifier").asInstanceOf[String])
        .map(id => id.replace("_"+channel, "_all"))
      globalPrimaryCategories.filter(cat => {
        !idsToIgnore.contains(cat.get("identifier").asInstanceOf[String])
      }) ++ channelPrimaryCategories
    }
  }

  private def getPrimaryCategories(body: String)(implicit httpUtil: HttpUtil): java.util.List[java.util.Map[String, AnyRef]] =  {
    val url: String = Platform.getString("composite.search.url", "https://dev.sunbirded.org/action/composite/v3/search")
    val httpResponse = httpUtil.post(url, body)
    if (200 != httpResponse.status) throw new ServerException("ERR_FETCHING_OBJECT_CATEGORY_DEFINITION", "Error while fetching primary categories.")
    val response: Response = JsonUtils.deserialize(httpResponse.body, classOf[Response])
    val objectCategoryList: util.List[util.Map[String, AnyRef]] = response.getResult.getOrDefault(ChannelConstants.objectCategoryDefinitionKey, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]]
    objectCategoryList.asScala.map(cat => (cat - "objectType").asJava).asJava
  }

  def getMasterCategoryList(): List[String] = {
    val url: String = Platform.getString("composite.search.url", "https://dev.sunbirded.org/action/composite/v3/search")
    val httpResponse: HttpResponse[String] = Unirest.post(url).header("Content-Type", "application/json").body("""{"request":{"filters":{"objectType":"ObjectCategory"},"fields":["name"]}}""").asString
    if (200 != httpResponse.getStatus)
      throw new ServerException("ERR_FETCHING_OBJECT_CATEGORY", "Error while fetching object category.")
    val response: Response = JsonUtils.deserialize(httpResponse.getBody, classOf[Response])
    val objectCategoryList: util.List[util.Map[String, AnyRef]] = response.getResult.getOrDefault(ChannelConstants.OBJECT_CATEGORY, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]]
    if (objectCategoryList.isEmpty)
      throw new ClientException("ERR_NO_MASTER_OBJECT_CATEGORY_DEFINED", "Master category object not present")
    objectCategoryList.map(a => a.getOrDefault("name", "").asInstanceOf[String]).toList
  }
}
