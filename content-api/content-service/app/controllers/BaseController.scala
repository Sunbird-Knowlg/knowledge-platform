package controllers


import java.io.File
import java.util
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.Patterns
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.DateUtils
import org.sunbird.common.dto.{Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import play.api.mvc._
import utils.JavaJsonUtils

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

abstract class BaseController(protected val cc: ControllerComponents)(implicit exec: ExecutionContext) extends AbstractController(cc) {

    def requestBody()(implicit request: Request[AnyContent]) = {
        val body = request.body.asJson.getOrElse("{}").toString
        JavaJsonUtils.deserialize[java.util.Map[String, Object]](body).getOrDefault("request", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    }

    def requestFormData()(implicit request: Request[AnyContent]) = {
        val reqMap = new util.HashMap[String, AnyRef]()
        val multipartData = request.body.asMultipartFormData.get
        if (null != multipartData.asFormUrlEncoded && !multipartData.asFormUrlEncoded.isEmpty) {
            val fileUrl: String = multipartData.asFormUrlEncoded.getOrElse("fileUrl",Seq()).get(0)
            if (StringUtils.isNotBlank(fileUrl))
                reqMap.put("fileUrl", fileUrl)
        } else if (null != multipartData.files && !multipartData.files.isEmpty) {
            val file: File = new File("/tmp" + File.separator + request.body.asMultipartFormData.get.files.get(0).filename)
            multipartData.files.get(0).ref.copyTo(file, false)
            reqMap.put("file", file)
        } else throw new ClientException("ERR_INVALID_DATA", "Please Provide Valid File Or File Url!")
        reqMap
    }

    def commonHeaders()(implicit request: Request[AnyContent]): java.util.Map[String, Object] = {
        val customHeaders = Map("x-channel-id" -> "channel", "X-Consumer-ID" -> "consumerId", "X-App-Id" -> "appId")
        customHeaders.map(ch => {
            val value = request.headers.get(ch._1)
            if (value.isDefined && !value.isEmpty) {
                collection.mutable.HashMap[String, Object](ch._2 -> value.get).asJava
            } else {
                collection.mutable.HashMap[String, Object]().asJava
            }
        }).flatten.toMap.asJava
    }

    def getRequest(input: java.util.Map[String, AnyRef], context: java.util.Map[String, AnyRef], operation: String): org.sunbird.common.dto.Request = {
        new org.sunbird.common.dto.Request(context, input, operation, null);
    }

    def getResult(apiId: String, actor: ActorRef, request: org.sunbird.common.dto.Request) : Future[Result] = {
        val future = Patterns.ask(actor, request, 30000) recoverWith {case e: Exception => Future(ResponseHandler.getErrorResponse(e))}
        future.map(f => {
            val result = f.asInstanceOf[Response]
            result.setId(apiId)
            setResponseEnvelope(result)
            val response = JavaJsonUtils.serialize(result);
            result.getResponseCode match {
                case ResponseCode.OK => Ok(response).as("application/json")
                case ResponseCode.CLIENT_ERROR => BadRequest(response).as("application/json")
                case ResponseCode.RESOURCE_NOT_FOUND => NotFound(response).as("application/json")
                case _ => play.api.mvc.Results.InternalServerError(response).as("application/json")
            }
        })
    }

    def setResponseEnvelope(response: Response) = {
        response.setTs(DateUtils.formatCurrentDate("yyyy-MM-dd'T'HH:mm:ss'Z'XXX"))
        response.getParams.setResmsgid(UUID.randomUUID().toString)
    }

    def setRequestContext(request:org.sunbird.common.dto.Request, version: String, objectType: String, schemaName: String): Unit = {
        var contextMap: java.util.Map[String, AnyRef] = new mutable.HashMap[String, AnyRef](){{
            put("graph_id", "domain")
            put("version" , version)
            put("objectType" , objectType)
            put("schemaName", schemaName)
        }};
        contextMap.putAll(request.getContext)
        request.setObjectType(objectType);
        request.setContext(contextMap)
    }
}
