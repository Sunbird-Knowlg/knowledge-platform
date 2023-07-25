package controllers.v4

import akka.actor.ActorRef
import controllers.BaseController
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}
import javax.inject.{Inject, Named}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class ObjectController  @Inject()(@Named(ActorNames.OBJECT_ACTOR) objectActor: ActorRef, cc: ControllerComponents)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val version = "1.0"
  val apiVersion = "4.0"

  def read(schema: String, identifier: String, fields: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val app = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    app.putAll(headers)
    app.putAll(Map("identifier" -> identifier, "mode" -> "read", "fields" -> fields.getOrElse("")).asJava)
    val readRequest = getRequest(app, headers, "readObject")
    setRequestContext(readRequest, version, schema.capitalize, schema)
    getResult(s"api.$schema.read", objectActor, readRequest, version = apiVersion)
  }

  def create(schema: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val content = body.getOrDefault(schema, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    content.putAll(headers)
    val createRequest = getRequest(content, headers, "createObject", true)
    setRequestContext(createRequest, version, schema.capitalize, schema)
    getResult(s"api.$schema.create", objectActor, createRequest, version = apiVersion)
  }

  def update(schema: String, identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val content = body.getOrDefault(schema, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    content.putAll(headers)
    val updateRequest = getRequest(content, headers, "updateObject")
    setRequestContext(updateRequest, version, schema.capitalize, schema)
    updateRequest.getContext.put("identifier", identifier);
    getResult(s"api.$schema.update", objectActor, updateRequest, version = apiVersion)
  }

  def retire(schema: String, identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val content = body.getOrDefault(schema, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    content.putAll(headers)
    val retireRequest = getRequest(content, headers, "retireObject")
    setRequestContext(retireRequest, version, schema.capitalize, schema)
    retireRequest.getContext.put("identifier", identifier);
    getResult(s"api.$schema.retire", objectActor, retireRequest, version = apiVersion)
  }

  def transition(schema: String, transition: String, identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val content = body.getOrDefault(schema, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    content.putAll(headers)
    val transitionRequest = getRequest(content, headers, transition)
    setRequestContext(transitionRequest, version, schema.capitalize, schema)
    transitionRequest.getContext.put("identifier", identifier);
    getResult(s"api.$schema.transition", objectActor, transitionRequest, version = apiVersion)
  }


}