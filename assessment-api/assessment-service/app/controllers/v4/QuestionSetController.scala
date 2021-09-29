package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import controllers.BaseController
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, QuestionSetOperations}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class QuestionSetController @Inject()(@Named(ActorNames.QUESTION_SET_ACTOR) questionSetActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

	val objectType = "QuestionSet"
	val schemaName: String = "questionset"
	val version = "1.0"

	def create() = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
		questionSet.putAll(headers)
		val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.createQuestionSet.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		getResult(ApiId.CREATE_QUESTION_SET, questionSetActor, questionSetRequest)
	}

	def read(identifier: String, mode: Option[String], fields: Option[String]) = Action.async { implicit request =>
		val headers = commonHeaders()
		val questionSet = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
		questionSet.putAll(headers)
		questionSet.putAll(Map("identifier" -> identifier, "fields" -> fields.getOrElse(""), "mode" -> mode.getOrElse("read")).asJava)
		val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.readQuestionSet.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		getResult(ApiId.READ_QUESTION_SET, questionSetActor, questionSetRequest)
	}

	def update(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
		questionSet.putAll(headers)
		val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.updateQuestionSet.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		questionSetRequest.getContext.put("identifier", identifier)
		getResult(ApiId.UPDATE_QUESTION_SET, questionSetActor, questionSetRequest)
	}

	def review(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
		questionSet.putAll(headers)
		val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.reviewQuestionSet.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		questionSetRequest.getContext.put("identifier", identifier)
		getResult(ApiId.REVIEW_QUESTION_SET, questionSetActor, questionSetRequest)
	}

	def publish(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
		questionSet.putAll(headers)
		val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.publishQuestionSet.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		questionSetRequest.getContext.put("identifier", identifier)
		getResult(ApiId.PUBLISH_QUESTION_SET, questionSetActor, questionSetRequest)
	}

	def retire(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		val questionSet = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
		questionSet.putAll(headers)
		val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.retireQuestionSet.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		questionSetRequest.getContext.put("identifier", identifier)
		getResult(ApiId.RETIRE_QUESTION_SET, questionSetActor, questionSetRequest)
	}

	def add() = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
		questionSet.putAll(headers)
		val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.addQuestion.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		getResult(ApiId.ADD_QUESTION_SET, questionSetActor, questionSetRequest)
	}

	def remove() = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
		questionSet.putAll(headers)
		val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.removeQuestion.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		getResult(ApiId.REMOVE_QUESTION_SET, questionSetActor, questionSetRequest)
	}

	def updateHierarchy() = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val data = body.getOrDefault("data", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
		data.putAll(headers)
		val questionSetRequest = getRequest(data, headers, "updateHierarchy")
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		getResult(ApiId.UPDATE_HIERARCHY, questionSetActor, questionSetRequest)
	}

	def getHierarchy(identifier: String, mode: Option[String]) = Action.async { implicit request =>
		val headers = commonHeaders()
		val questionSet = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
		questionSet.putAll(headers)
		questionSet.putAll(Map("rootId" -> identifier, "mode" -> mode.getOrElse("")).asJava)
		val readRequest = getRequest(questionSet, headers, "getHierarchy")
		setRequestContext(readRequest, version, objectType, schemaName)
		getResult(ApiId.GET_HIERARCHY, questionSetActor, readRequest)
	}

	def reject(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
		questionSet.putAll(headers)
		val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.rejectQuestionSet.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		questionSetRequest.getContext.put("identifier", identifier)
		getResult(ApiId.REJECT_QUESTION_SET, questionSetActor, questionSetRequest)
	}

	def importQuestionSet() = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		body.putAll(headers)
		val questionSetRequest = getRequest(body, headers, QuestionSetOperations.importQuestionSet.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		getResult(ApiId.IMPORT_QUESTION_SET, questionSetActor, questionSetRequest)
	}

	def systemUpdate(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
		questionSet.putAll(headers)
		val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.systemUpdateQuestionSet.toString)
		setRequestContext(questionSetRequest, version, objectType, schemaName)
		questionSetRequest.getContext.put("identifier", identifier);
		getResult(ApiId.SYSTEM_UPDATE_QUESTION_SET, questionSetActor, questionSetRequest)
	}
}
