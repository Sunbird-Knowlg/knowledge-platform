package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import org.sunbird.utils.Constants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

@Singleton
class ObjectCategoryController @Inject()(@Named(ActorNames.OBJECT_CATEGORY_ACTOR) objectCategoryActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

    val objectType = "ObjectCategory"

    def create() = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val category = body.getOrDefault(Constants.OBJECT_CATEGORY, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        category.putAll(headers)
        val categoryRequest = getRequest(category, headers, Constants.CREATE_OBJECT_CATEGORY)
        setRequestContext(categoryRequest, Constants.OBJECT_CATEGORY_SCHEMA_VERSION, objectType, Constants.OBJECT_CATEGORY_SCHEMA_NAME)
        getResult(ApiId.CREATE_OBJECT_CATEGORY, objectCategoryActor, categoryRequest)
    }

    def read(identifier: String, fields: Option[String]) = Action.async { implicit request =>
        val headers = commonHeaders()
        val category = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
        category.putAll(headers)
        category.putAll(Map(Constants.IDENTIFIER -> identifier, Constants.FIELDS -> fields.getOrElse("")).asJava)
        val categoryRequest = getRequest(category, headers,  Constants.READ_OBJECT_CATEGORY)
        setRequestContext(categoryRequest, Constants.OBJECT_CATEGORY_SCHEMA_VERSION, objectType, Constants.OBJECT_CATEGORY_SCHEMA_NAME)
        getResult(ApiId.READ_OBJECT_CATEGORY, objectCategoryActor, categoryRequest)
    }

    def update(identifier: String) = Action.async { implicit request =>
        val headers = commonHeaders()
        val body = requestBody()
        val category = body.getOrDefault(Constants.OBJECT_CATEGORY, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
        category.putAll(headers)
        val categoryRequest = getRequest(category, headers,  Constants.UPDATE_OBJECT_CATEGORY)
        setRequestContext(categoryRequest, Constants.OBJECT_CATEGORY_SCHEMA_VERSION, objectType, Constants.OBJECT_CATEGORY_SCHEMA_NAME)
        categoryRequest.getContext.put(Constants.IDENTIFIER, identifier)
        getResult(ApiId.UPDATE_OBJECT_CATEGORY, objectCategoryActor, categoryRequest)
    }
}
