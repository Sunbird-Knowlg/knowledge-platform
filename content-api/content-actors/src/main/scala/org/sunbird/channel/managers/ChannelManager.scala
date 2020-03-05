package org.sunbird.channel.managers

import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.util.ChannelConstants
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.common.Platform
import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.Unirest
import org.sunbird.graph.utils.ScalaJsonUtils

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.{ExecutionContext, Future}

object ChannelManager {

  def validateLicense(request: Request)(implicit ec: ExecutionContext): Unit = {
    if (StringUtils.isNotEmpty(request.getRequest.get(ChannelConstants.DEFAULT_LICENSE).asInstanceOf[String])) {
      val licenseList = RedisCache.getList(ChannelConstants.LICENSE_REDIS_KEY)
      if (licenseList.isEmpty) {
        val licenseList = searchLicenseList()
        licenseList.map(licenseList => {
          if (licenseList.contains(request.getRequest.get(ChannelConstants.DEFAULT_LICENSE)))
            RedisCache.saveList(ChannelConstants.LICENSE_REDIS_KEY, licenseList)
          else
            throw new ClientException("ERR_INVALID_LICENSE", "LICENSE_NOT_FOUND : " + request.getRequest.get(ChannelConstants.DEFAULT_LICENSE).asInstanceOf[String])
        })
      } else if (!licenseList.contains(request.getRequest.get(ChannelConstants.DEFAULT_LICENSE)))
        throw new ClientException("ERR_INVALID_LICENSE_NAME", "LICENSE_NOT_FOUND : " + request.getRequest.get(ChannelConstants.DEFAULT_LICENSE))
    }
  }

  def channelLicenseCache(response: Response, request: Request): Unit = {
    if (!ResponseHandler.checkError(response) && response.getResult.containsKey(ChannelConstants.NODE_ID) && request.getRequest.containsKey(ChannelConstants.DEFAULT_LICENSE))
      RedisCache.set(ChannelConstants.CHANNEL_LICENSE_CACHE_PREFIX + response.getResult.get(ChannelConstants.NODE_ID) + ChannelConstants.CHANNEL_LICENSE_CACHE_SUFFIX, request.getRequest.get(ChannelConstants.DEFAULT_LICENSE).asInstanceOf[String], 0)
  }

  def searchLicenseList()(implicit ec: ExecutionContext): Future[List[String]] = {
    val url: String = if (Platform.config.hasPath("composite.search.url")) Platform.config.getString("composite.search.url") else "https://dev.sunbirded.org/action/composite/v3/search"
    val httpResponse: HttpResponse[String] = Unirest.post(url).header("Content-Type", "application/json").body("{ \"request\": { \n      \"filters\":{\n      \t\"objectType\":\"license\",\n      \t\"status\":\"Live\"\n      },\n      \"fields\":[\"name\"]\n    }\n}").asString
    if (httpResponse.getStatus == 200) {
      val response: Response = ScalaJsonUtils.deserialize(httpResponse.getBody)(manifest[Response])
      if (response.getResult.get("count").asInstanceOf[Integer] > 0 && !response.getResult.get("license").asInstanceOf[List[Map[String, AnyRef]]].isEmpty) {
        val licenseList = response.getResult.get("license").asInstanceOf[List[Map[String, AnyRef]]].map(license => mapAsJavaMap(license).get("name").asInstanceOf[String])
        Future(licenseList)
      } else {
        Future(List[String]())
      }
    } else {
      throw new ServerException("ERR_FETCHING_LICENSE", "Error while fetching license.")
    }
  }
}
