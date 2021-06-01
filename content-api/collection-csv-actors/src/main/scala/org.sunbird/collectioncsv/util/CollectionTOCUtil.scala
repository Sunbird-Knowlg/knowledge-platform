package org.sunbird.collectioncsv.util

import com.mashape.unirest.http.Unirest
import org.sunbird.common.exception.{ClientException, ResponseCode, ServerException}
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.sunbird.collectioncsv.util.CollectionTOCConstants.BEARER
import org.sunbird.common.Platform
import org.sunbird.common.dto.Response
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.collection.JavaConverters._
import java.text.MessageFormat
import scala.collection.immutable.{HashMap, Map}
import scala.collection.JavaConversions.mapAsJavaMap


class CollectionTOCUtil {

  private def requestParams(params: Map[String, String]): String = {
    if (null != params) {
      val sb: StringBuilder = new StringBuilder()
      sb.append("?")
      var i: Int = 0
      for ((key, value) <- params) {
        if ({ i += 1; i - 1 } > 1) {
          sb.append("&")
        }
        sb.append(key).append("=").append(value)
      }
      sb.toString
    } else {
      ""
    }
  }

  def getRelatedFrameworkById(frameworkId: String): Response = {
    val reqParams: Map[String, String] = HashMap[String, String]("categories" -> "topic")

    try {
      val headers = new util.HashMap[String, String]() {
        put(CollectionTOCConstants.CONTENT_TYPE_HEADER, CollectionTOCConstants.APPLICATION_JSON)
        put(AUTHORIZATION, CollectionTOCConstants.BEARER + Platform.config.getString(CollectionTOCConstants.SUNBIRD_AUTHORIZATION))
      }

      val requestUrl = Platform.config.getString(CollectionTOCConstants.LEARNING_SERVICE_BASE_URL) + Platform.config.getString(CollectionTOCConstants.FRAMEWORK_READ_API_URL) + "/" + frameworkId + requestParams(reqParams)

      TelemetryManager.log("CollectionTOCUtil --> handleReadRequest --> requestUrl: " + requestUrl)
      TelemetryManager.log("CollectionTOCUtil --> handleReadRequest --> headers: " + headers)
      val httpResponse = Unirest.get(requestUrl).headers(headers).asString
      
      TelemetryManager.log("CollectionTOCUtil --> handleReadRequest --> httpResponse.getStatus: " + httpResponse.getStatus)
      if ( null== httpResponse || httpResponse.getStatus != ResponseCode.OK.code())
        throw new ServerException("SERVER_ERROR", "Error while fetching content data.")

      ScalaJsonUtils.deserialize[Response](httpResponse.getBody)

    } catch {
      case e: Exception =>
        TelemetryManager.log("CollectionTOCUtil --> handleReadRequest --> Exception: " + e.getMessage)
        throw e
    }
  }

  def validateDialCodes(channelId: String, dialcodes: List[String]): List[String] = {
    val reqMap = new util.HashMap[String, AnyRef]() {
        put(CollectionTOCConstants.REQUEST, new util.HashMap[String, AnyRef]() {
            put(CollectionTOCConstants.SEARCH, new util.HashMap[String, AnyRef]() {
                put(CollectionTOCConstants.IDENTIFIER, dialcodes.distinct.asJava)
            })
        })
    }

    val headerParam = HashMap[String, String](CollectionTOCConstants.X_CHANNEL_ID -> channelId, AUTHORIZATION -> (CollectionTOCConstants.BEARER + Platform.config.getString(CollectionTOCConstants.SUNBIRD_AUTHORIZATION)), "Content-Type" -> "application/json")
    val requestUrl = Platform.config.getString(CollectionTOCConstants.SUNBIRD_CS_BASE_URL) + Platform.config.getString(CollectionTOCConstants.SUNBIRD_DIALCODE_SEARCH_API)
    val searchResponse = Unirest.post(requestUrl).headers(headerParam).body(ScalaJsonUtils.serialize(reqMap)).asString

    if (null == searchResponse || searchResponse.getStatus != ResponseCode.OK.code())
      throw new ServerException("SERVER_ERROR", "Error while fetching DIAL Codes List.")

    val response = ScalaJsonUtils.deserialize[Response](searchResponse.getBody)

    try {
      response.getResult.getOrDefault(CollectionTOCConstants.DIAL_CODES, new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[List[Map[String, AnyRef]]].map(_.getOrElse(CollectionTOCConstants.IDENTIFIER, "")).asInstanceOf[List[String]]
    }
    catch {
      case _:Exception =>
        List.empty
    }
  }

  def searchLinkedContents(linkedContents: List[String]): List[Map[String, AnyRef]] = {

    val reqMap = new util.HashMap[String, AnyRef]() {
        put(CollectionTOCConstants.REQUEST, new util.HashMap[String, AnyRef]() {
            put(CollectionTOCConstants.FILTERS, new util.HashMap[String, AnyRef]() {
                put(CollectionTOCConstants.IDENTIFIER, linkedContents.distinct.asJava)
            })
            put(CollectionTOCConstants.FIELDS, new util.ArrayList[String]() {
              add(CollectionTOCConstants.IDENTIFIER)
              add(CollectionTOCConstants.NAME)
              add(CollectionTOCConstants.CONTENT_TYPE)
              add(CollectionTOCConstants.MIME_TYPE)
            })
            put(CollectionTOCConstants.LIMIT, linkedContents.size.asInstanceOf[AnyRef])
        })
    }

    val headerParam = HashMap[String, String](AUTHORIZATION -> (BEARER + Platform.config.getString(CollectionTOCConstants.SUNBIRD_AUTHORIZATION)), "Content-Type" -> "application/json")
    val requestUrl = Platform.config.getString(CollectionTOCConstants.SUNBIRD_CS_BASE_URL) + Platform.config.getString(CollectionTOCConstants.SUNBIRD_CONTENT_SEARCH_URL)

    val searchResponse = Unirest.post(requestUrl).headers(headerParam).body(ScalaJsonUtils.serialize(reqMap)).asString

    if (null == searchResponse || searchResponse.getStatus != ResponseCode.OK.code())
      throw new ServerException("SERVER_ERROR", "Error while fetching Linked Contents List.")


    val response = ScalaJsonUtils.deserialize[Response](searchResponse.getBody)
    try {
      response.getResult.getOrDefault(CollectionTOCConstants.CONTENT, new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[List[Map[String, AnyRef]]]
    }
    catch {
      case _:Exception =>
        List.empty
    }
  }

  def linkDIALCode(channelId: String, collectionID: String, linkDIALCodesMap: List[Map[String,String]]): Response = {

    val reqMap = new util.HashMap[String, AnyRef]() {
        put(CollectionTOCConstants.REQUEST, new util.HashMap[String, AnyRef]() {
            put(CollectionTOCConstants.CONTENT, linkDIALCodesMap.asJava)
        })
    }

    val headerParam = HashMap[String, String](CollectionTOCConstants.X_CHANNEL_ID -> channelId, AUTHORIZATION -> (BEARER + Platform.config.getString(CollectionTOCConstants.SUNBIRD_AUTHORIZATION)), "Content-Type" -> "application/json")
    val requestUrl = Platform.config.getString(CollectionTOCConstants.LEARNING_SERVICE_BASE_URL) + Platform.config.getString(CollectionTOCConstants.LINK_DIAL_CODE_API) + "/" + collectionID
    val linkResponse = Unirest.post(requestUrl).headers(headerParam).body(ScalaJsonUtils.serialize(reqMap)).asString

    val response = ScalaJsonUtils.deserialize[Response](linkResponse.getBody)

    if (null == linkResponse || linkResponse.getStatus != ResponseCode.OK.code())
      if(linkResponse.getStatus == 400) {
        val msgsResult = response.getResult.getOrDefault(CollectionTOCConstants.MESSAGES, new util.ArrayList[String])
        throw new ClientException("DIAL_CODE_LINK_ERROR", MessageFormat.format("{0}",msgsResult))
      } else throw new ServerException("SERVER_ERROR", "Error while updating collection hierarchy.")

    response
  }

}
