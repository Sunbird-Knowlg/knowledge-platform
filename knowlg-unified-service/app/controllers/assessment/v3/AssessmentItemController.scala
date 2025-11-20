package controllers.assessment.v3

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, AssessmentItemOperations}

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext

@Singleton
class AssessmentItemController @Inject()(@Named(ActorNames.ASSESSMENT_ITEM_ACTOR) assessmentItemActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "AssessmentItem"
  val schemaName: String = "assessmentitem"
  val version = "1.0"

  def create() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val assessmentItem = body.getOrDefault("assessment_item", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
    assessmentItem.putAll(headers)
    val assessmentItemRequest = getRequest(assessmentItem, headers, AssessmentItemOperations.createItem.toString)
    setRequestContext(assessmentItemRequest, version, objectType, schemaName)
    getResult(ApiId.CREATE_ASSESSMENT_ITEM, assessmentItemActor, assessmentItemRequest)
  }

  def read(identifier: String, ifields: Option[String], fields: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val assessmentItem = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    assessmentItem.putAll(headers)
    val fieldList = fields.orElse(ifields).getOrElse("")
    assessmentItem.putAll(Map("identifier" -> identifier, "fields" -> fieldList).asJava)
    val assessmentItemRequest = getRequest(assessmentItem, headers, AssessmentItemOperations.readItem.toString)
    setRequestContext(assessmentItemRequest, version, objectType, schemaName)
    getResult(ApiId.READ_ASSESSMENT_ITEM, assessmentItemActor, assessmentItemRequest)
  }

  def update(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val assessmentItem = body.getOrDefault("assessment_item", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    assessmentItem.putAll(headers)
    val assessmentItemRequest = getRequest(assessmentItem, headers, AssessmentItemOperations.updateItem.toString)
    setRequestContext(assessmentItemRequest, version, objectType, schemaName)
    assessmentItemRequest.getContext.put("identifier", identifier)
    getResult(ApiId.UPDATE_ASSESSMENT_ITEM, assessmentItemActor, assessmentItemRequest)
  }

  def retire(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val assessmentItem = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    assessmentItem.putAll(headers)
    val assessmentItemRequest = getRequest(assessmentItem, headers, AssessmentItemOperations.retireItem.toString)
    setRequestContext(assessmentItemRequest, version, objectType, schemaName)
    assessmentItemRequest.getContext.put("identifier", identifier)
    getResult(ApiId.RETIRE_ASSESSMENT_ITEM, assessmentItemActor, assessmentItemRequest)
  }
}
