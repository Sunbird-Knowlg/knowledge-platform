package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import controllers.BaseController
import javax.inject.{Inject, Named}
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ClientException
import org.sunbird.utils.Constants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class ObjectCategoryDefinitionController @Inject()(@Named(ActorNames.OBJECT_CATEGORY_DEFINITION_ACTOR) objCategoryDefinitionActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

	val OBJECT_TYPE = "ObjectCategoryDefinition"
	val OBJECT_CATEGORY_DEFINITION = "objectCategoryDefinition"
	val SCHEMA_NAME: String = "objectcategorydefinition"
	val SCHEMA_VERSION = "1.0"

	def create() = Action.async { implicit request =>
		val headers = commonHeaders()
		headers.remove("channel")
		val body = requestBody()
		val categoryDefinition = body.getOrDefault(OBJECT_CATEGORY_DEFINITION, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
		categoryDefinition.putAll(headers)
		val categoryDefinitionReq = getRequest(categoryDefinition, headers, Constants.CREATE_OBJECT_CATEGORY_DEFINITION)
		setRequestContext(categoryDefinitionReq, SCHEMA_VERSION, OBJECT_TYPE, SCHEMA_NAME)
		getResult(ApiId.CREATE_OBJECT_CATEGORY_DEFINITION, objCategoryDefinitionActor, categoryDefinitionReq)
	}

	def read(identifier: String, fields: Option[String]) = Action.async { implicit request =>
		val headers = commonHeaders()
		val categoryDefinition = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
		categoryDefinition.putAll(headers)
		categoryDefinition.putAll(Map(Constants.IDENTIFIER -> identifier, Constants.FIELDS -> fields.getOrElse("")).asJava)
		val categoryDefinitionReq = getRequest(categoryDefinition, headers,  Constants.READ_OBJECT_CATEGORY_DEFINITION)
		setRequestContext(categoryDefinitionReq, SCHEMA_VERSION, OBJECT_TYPE, SCHEMA_NAME)
		getResult(ApiId.READ_OBJECT_CATEGORY_DEFINITION, objCategoryDefinitionActor, categoryDefinitionReq)
	}

	def update(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		headers.remove("channel")
		val body = requestBody()
		val categoryDefinition = body.getOrDefault(OBJECT_CATEGORY_DEFINITION, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
		categoryDefinition.putAll(headers)
		val categoryDefinitionReq = getRequest(categoryDefinition, headers,  Constants.UPDATE_OBJECT_CATEGORY_DEFINITION)
		setRequestContext(categoryDefinitionReq, SCHEMA_VERSION, OBJECT_TYPE, SCHEMA_NAME)
		categoryDefinitionReq.getContext.put(Constants.IDENTIFIER, identifier)
		getResult(ApiId.UPDATE_OBJECT_CATEGORY_DEFINITION, objCategoryDefinitionActor, categoryDefinitionReq)
	}

	def readCategoryDefinition(fields: Option[String]) = Action.async { implicit request =>
		val headers = commonHeaders()
		headers.remove("channel")
		val body = requestBody()
		val categoryDefinition = body.getOrDefault(OBJECT_CATEGORY_DEFINITION, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
		categoryDefinition.putAll(headers)
		categoryDefinition.put(Constants.FIELDS, fields.getOrElse(""))
		categoryDefinition.put("REQ_METHOD", request.method)
		val categoryDefinitionReq = getRequest(categoryDefinition, headers, Constants.READ_OBJECT_CATEGORY_DEFINITION)
		setRequestContext(categoryDefinitionReq, SCHEMA_VERSION, OBJECT_TYPE, SCHEMA_NAME)
		getResult(ApiId.READ_OBJECT_CATEGORY_DEFINITION, objCategoryDefinitionActor, categoryDefinitionReq)
	}


}
