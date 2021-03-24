package controllers.v4

import akka.actor.ActorRef
import com.google.inject.Singleton
import controllers.BaseController
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext

@Singleton
class AppController @Inject()(@Named(ActorNames.APP_ACTOR) appActor: ActorRef, cc: ControllerComponents)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "App"
  val schemaName: String = "app"
  val version = "1.0"
  val apiVersion = "4.0"

  def register() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val metadata = body.getOrDefault("app", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    metadata.putAll(headers)
    val appRequest = getRequest(metadata, headers, "create")
    setRequestContext(appRequest, version, objectType, schemaName)
    getResult(ApiId.REGISTER_APP, appActor, appRequest, version = apiVersion)
  }

  def update(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val metadata = body.getOrDefault("app", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    metadata.putAll(headers)
    val appRequest = getRequest(metadata, headers, "update")
    setRequestContext(appRequest, version, objectType, schemaName)
    appRequest.getContext.put("identifier", identifier)
    getResult(ApiId.UPDATE_APP, appActor, appRequest, version = apiVersion)
  }
}
