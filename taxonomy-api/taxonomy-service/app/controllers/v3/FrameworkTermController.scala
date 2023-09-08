package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import controllers.BaseController
import org.sunbird.utils.Constants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext

class FrameworkTermController @Inject()(@Named(ActorNames.TERM_ACTOR) termActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "Term"
  def createFrameworkTerm(framework: String, category: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val term = body.getOrDefault(Constants.TERM, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    term.put(Constants.FRAMEWORK, framework)
    term.put(Constants.CATEGORY, category)
    term.putAll(headers)
    val termRequest = getRequest(term, headers, Constants.CREATE_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    getResult(ApiId.CREATE_TERM, termActor, termRequest)
  }

  def readFrameworkTerm(termId: String, framework: String, category: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val term = body.getOrDefault(Constants.TERM, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    term.put(Constants.TERM, termId)
    term.put(Constants.CATEGORY, category)
    term.put(Constants.FRAMEWORK, framework)
    term.putAll(headers)
    val readTermRequest = getRequest(term, headers, Constants.READ_TERM)
    setRequestContext(readTermRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    getResult(ApiId.READ_TERM, termActor, readTermRequest)
  }

  def updateFrameworkTerm(termId: String, framework: String, category: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val term = body.getOrDefault(Constants.TERM, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    term.put(Constants.TERM, termId)
    term.put(Constants.CATEGORY, category)
    term.put(Constants.FRAMEWORK, framework)
    term.putAll(headers)
    val termRequest = getRequest(term, headers, Constants.UPDATE_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    termRequest.getContext.put(Constants.TERM, termId)
    getResult(ApiId.UPDATE_TERM, termActor, termRequest)
  }

  def retireFrameworkTerm(termId: String, framework: String, category: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val term = body.getOrDefault(Constants.TERM, new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    term.put(Constants.TERM, termId)
    term.put(Constants.CATEGORY, category)
    term.put(Constants.FRAMEWORK, framework)
    term.putAll(headers)
    val termRequest = getRequest(term, headers, Constants.RETIRE_TERM)
    setRequestContext(termRequest, Constants.TERM_SCHEMA_VERSION, objectType, Constants.TERM_SCHEMA_NAME)
    termRequest.getContext.put(Constants.TERM, termId)
    getResult(ApiId.RETIRE_TERM, termActor, termRequest)
  }

}