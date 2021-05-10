package controllers

import java.util
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.Patterns
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{RequestParams, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.telemetry.TelemetryParams
import play.api.mvc._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

abstract class SearchBaseController(protected val cc: ControllerComponents)(implicit exec: ExecutionContext) extends AbstractController(cc) {

    val DEFAULT_CHANNEL_ID = Platform.config.getString("channel.default");
    val API_VERSION = "3.0"
    
    def requestBody()(implicit request: Request[AnyContent]) = {
        val body = request.body.asJson.getOrElse("{}").toString
        JsonUtils.deserialize(body, classOf[java.util.Map[String, Object]]).getOrDefault("request", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    }

    def commonHeaders()(implicit request: Request[AnyContent]): java.util.Map[String, Object] = {
        val customHeaders = Map("x-channel-id" -> "CHANNEL_ID", "x-consumer-id" -> "CONSUMER_ID", "x-app-id" -> "APP_ID", "x-session-id" -> "SESSION_ID", "x-device-id" -> "DEVICE_ID")
        val headers = request.headers.headers.groupBy(_._1).mapValues(_.map(_._2))
        val appHeaders = headers.filter(header => customHeaders.keySet.contains(header._1.toLowerCase))
                .map(entry => (customHeaders.get(entry._1.toLowerCase()).get, entry._2.head))
        val contextMap = {
            if(appHeaders.contains("CHANNEL_ID"))
                appHeaders
            else appHeaders + ("CHANNEL_ID"-> DEFAULT_CHANNEL_ID)
        }
        mapAsJavaMap(contextMap)
    }

    def getRequest(input: java.util.Map[String, AnyRef], context: java.util.Map[String, AnyRef], operation: String): org.sunbird.common.dto.Request = {
        new org.sunbird.common.dto.Request(context, input, operation, null);
    }

    protected def getResult(response: Future[Response], apiId: String) = {
        response.map(result => {
            result.setId(apiId)
            setResponseEnvelope(result)
            val resultStr = JsonUtils.serialize(result)
            result.getResponseCode match {
                case ResponseCode.OK => play.api.mvc.Results.Ok(resultStr).as("application/json")
                case ResponseCode.CLIENT_ERROR => play.api.mvc.Results.BadRequest(resultStr).as("application/json")
                case ResponseCode.RESOURCE_NOT_FOUND => play.api.mvc.Results.NotFound(resultStr).as("application/json")
                case _ => play.api.mvc.Results.InternalServerError(resultStr).as("application/json")
            }
        })
    }

    def getResult(apiId: String, actor: ActorRef, request: org.sunbird.common.dto.Request) : Future[Result] = {
        val future = Patterns.ask(actor, request, 30000) recoverWith {case e: Exception => Future(ResponseHandler.getErrorResponse(e))}
        getResult(future.map(f => f.asInstanceOf[Response]), apiId)
    }

    def setResponseEnvelope(response: Response) = {
        response.setTs(DateUtils.formatCurrentDate("yyyy-MM-dd'T'HH:mm:ss'Z'XXX"))
        response.getParams.setResmsgid(UUID.randomUUID().toString)
    }

    protected def getRequest(apiId: String)(implicit playRequest: play.api.mvc.Request[AnyContent]): org.sunbird.common.dto.Request = {
        val request: org.sunbird.common.dto.Request = new org.sunbird.common.dto.Request
        if (null != playRequest) {
            val body = playRequest.body.asJson.getOrElse("{}").toString
            
            val requestMap: java.util.Map[String, AnyRef] = JsonUtils.deserialize(body, classOf[java.util.Map[String, Object]])
            if (null != requestMap && !requestMap.isEmpty) {
                val id: String = if (requestMap.get("id") == null || StringUtils.isBlank(requestMap.get("id").asInstanceOf[String])) apiId
                else requestMap.get("id").asInstanceOf[String]
                val ver: String = if (requestMap.get("ver") == null || StringUtils.isBlank(requestMap.get("ver").asInstanceOf[String])) API_VERSION
                else requestMap.get("ver").asInstanceOf[String]
                val ts: String = requestMap.get("ts").asInstanceOf[String]
                request.setId(id)
                request.setVer(ver)
                request.setTs(ts)
                val reqParams: AnyRef = requestMap.get("params")
                if (null != reqParams) try {
                    val params: RequestParams = JsonUtils.convert(reqParams, classOf[RequestParams])
                    request.setParams(params)
                } catch {
                    case e: Exception =>
                        e.printStackTrace()
                }
                val requestObj: AnyRef = requestMap.get("request")
                if (null != requestObj) try {
                    val strRequest: String = JsonUtils.serialize(requestObj)
                    val map: java.util.Map[String, AnyRef] =  JsonUtils.deserialize(strRequest, classOf[java.util.Map[String, Object]])
                    if (null != map && !map.isEmpty) request.setRequest(map)
                } catch {
                    case e: Exception =>
                        e.printStackTrace()
                }
            }
            else {
                request.setId(apiId)
                request.setVer(API_VERSION)
            }
        }
        else {
            request.setId(apiId)
            request.setVer(API_VERSION)
        }
        request
    }

    protected def setHeaderContext(searchRequest: org.sunbird.common.dto.Request)(implicit playRequest: play.api.mvc.Request[AnyContent]) : Unit = {
        searchRequest.setContext(new util.HashMap[String, AnyRef]())
        searchRequest.getContext.put(TelemetryParams.ENV.name, "search")
        searchRequest.getContext.putAll(commonHeaders())
        if (StringUtils.isBlank(searchRequest.getContext.getOrDefault("CHANNEL_ID", "").asInstanceOf[String])) {
            searchRequest.getContext.put("CHANNEL_ID", Platform.config.getString("channel.default"))
        }

        if (null != searchRequest.getContext.get("CONSUMER_ID")) searchRequest.put(TelemetryParams.ACTOR.name, searchRequest.getContext.get("CONSUMER_ID"))
        else if (null != searchRequest && null != searchRequest.getParams.getCid) searchRequest.put(TelemetryParams.ACTOR.name, searchRequest.getParams.getCid)
        else searchRequest.put(TelemetryParams.ACTOR.name, "learning.platform")
    }
}
