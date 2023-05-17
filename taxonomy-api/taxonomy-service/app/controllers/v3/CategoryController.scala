package controllers.v3

import akka.actor.{ActorRef, ActorSystem}

import scala.concurrent.{ExecutionContext, Future}
import controllers.BaseController

import javax.inject.{Inject, Named, Singleton}
import org.sunbird.utils.Constants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, JavaJsonUtils}

class CategoryController  @Inject()(@Named(ActorNames.CATEGORY_ACTOR) createActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "Category"
  def createCategory() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val category = body.getOrDefault(Constants.CATEGORY, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    category.putAll(headers)
    val categoryRequest = getRequest(category, headers, Constants.CREATE_CATEGORY)
    setRequestContext(categoryRequest, Constants.CATEGORY_SCHEMA_NAME, objectType, Constants.CATEGORY_SCHEMA_NAME)
    getResult(ApiId.CREATE_CATEGORY, createActor, categoryRequest)
  }

}
