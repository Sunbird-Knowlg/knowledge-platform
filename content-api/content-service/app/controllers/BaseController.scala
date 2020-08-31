package controllers


import java.io.File
import java.util
import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.Patterns
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{DateUtils, Platform}
import org.sunbird.common.dto.{Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import play.api.mvc._
import utils.JavaJsonUtils

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

abstract class BaseController(protected val cc: ControllerComponents)(implicit exec: ExecutionContext) extends AbstractController(cc) {

    def requestBody()(implicit request: Request[AnyContent]) = {
        val body = request.body.asJson.getOrElse("{}").toString
        JavaJsonUtils.deserialize[java.util.Map[String, Object]](body).getOrDefault("request", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    }

    def requestFormData()(implicit request: Request[AnyContent]) = {
        val reqMap = new util.HashMap[String, AnyRef]()
        if(!request.body.asMultipartFormData.isEmpty) {
            val multipartData = request.body.asMultipartFormData.get
            if (null != multipartData.asFormUrlEncoded && !multipartData.asFormUrlEncoded.isEmpty) {
                if(multipartData.asFormUrlEncoded.getOrElse("fileUrl",Seq()).length > 0){
                    val fileUrl: String = multipartData.asFormUrlEncoded.getOrElse("fileUrl",Seq()).head
                    if (StringUtils.isNotBlank(fileUrl))
                        reqMap.put("fileUrl", fileUrl)
                }
                if(multipartData.asFormUrlEncoded.getOrElse("filePath",Seq()).length > 0){
                    val filePath: String = multipartData.asFormUrlEncoded.getOrElse("filePath",Seq()).head
                    if (StringUtils.isNotBlank(filePath))
                        reqMap.put("filePath", filePath)
                }
            }
            if (null != multipartData.files && !multipartData.files.isEmpty) {
                val file: File = new File("/tmp" + File.separator + request.body.asMultipartFormData.get.files.head.filename)
                multipartData.files.head.ref.copyTo(file, false)
                reqMap.put("file", file)
            }
        }
        if(StringUtils.isNotBlank(reqMap.getOrDefault("fileUrl", "").asInstanceOf[String]) || null != reqMap.get("file").asInstanceOf[File]){
            reqMap
        } else {
            throw new ClientException("ERR_INVALID_DATA", "Please Provide Valid File Or File Url!")
        }
    }

    def commonHeaders(ignoreHeaders: Option[List[String]] = Option(List()))(implicit request: Request[AnyContent]): java.util.Map[String, Object] = {
        val customHeaders = Map("x-channel-id" -> "channel", "X-Consumer-ID" -> "consumerId", "X-App-Id" -> "appId").filterKeys(key => !ignoreHeaders.getOrElse(List()).contains(key))
        customHeaders.map(ch => {
            val value = request.headers.get(ch._1)
            if (value.isDefined && !value.isEmpty) {
                collection.mutable.HashMap[String, Object](ch._2 -> value.get).asJava
            } else {
                collection.mutable.HashMap[String, Object]().asJava
            }
        }).reduce((a, b) => {
            a.putAll(b)
            return a
        })
    }

    def getRequest(input: java.util.Map[String, AnyRef], context: java.util.Map[String, AnyRef], operation: String, categoryMapping: Boolean = false): org.sunbird.common.dto.Request = {
        //Todo mapping and reverse mapping
        setContentAndCategoryTypes(input, categoryMapping)
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
                case ResponseCode.PARTIAL_SUCCESS => MultiStatus(response).as("application/json")
                case _ => play.api.mvc.Results.InternalServerError(response).as("application/json")
            }
        })
    }

    def setResponseEnvelope(response: Response) = {
        response.setTs(DateUtils.formatCurrentDate("yyyy-MM-dd'T'HH:mm:ss'Z'XXX"))
        response.getParams.setResmsgid(UUID.randomUUID().toString)
    }

    def setRequestContext(request:org.sunbird.common.dto.Request, version: String, objectType: String, schemaName: String): Unit = {
        var contextMap: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef](){{
            put("graph_id", "domain")
            put("version" , version)
            put("objectType" , objectType)
            put("schemaName", schemaName)
        }};
        contextMap.putAll(request.getContext)
        request.setObjectType(objectType);
        request.setContext(contextMap)
    }

    private def setContentAndCategoryTypes(input: java.util.Map[String, AnyRef], categoryMapping: Boolean) = {
        val contentType = input.getOrDefault("contentType", "").asInstanceOf[String]
        val primaryCategory = input.getOrDefault("primaryCategory", "").asInstanceOf[String]
        val categoryMap: java.util.Map[String, AnyRef] = if(Platform.config.hasPath("contentType_primaryCategory_mapping"))
            Platform.config.getAnyRef("contentType_primaryCategory_mapping").asInstanceOf[java.util.Map[String, AnyRef]]
        else
            new util.HashMap[String, AnyRef]()
        val (updatedContentType, updatedPrimaryCategory): (String, String) = if(categoryMapping) {
            if(StringUtils.isNotBlank(contentType) && StringUtils.isNotBlank(primaryCategory)) (contentType, primaryCategory)
            else if(StringUtils.isNotBlank(contentType)) (contentType, categoryMap.get(contentType).asInstanceOf[String])
            else if(StringUtils.isNotBlank(primaryCategory)) (categoryMap.asScala.filter(entry => StringUtils.equalsIgnoreCase(entry._2.asInstanceOf[String],primaryCategory)).keys.head, primaryCategory)
            else (contentType, primaryCategory)
        } else (contentType, primaryCategory)
        input.put("contentType", updatedContentType)
        input.put("primaryCategory", updatedPrimaryCategory)
    }
}
