package controllers.v5

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.sunbird.common.Platform
import org.sunbird.telemetry.logger.TelemetryManager
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, QuestionSetOperations}

import javax.inject.{Inject, Named}
import scala.jdk.CollectionConverters._
import scala.collection.convert.ImplicitConversions.`map AsScala`
import scala.concurrent.ExecutionContext

class QuestionSetController @Inject()(@Named(ActorNames.QUESTION_SET_V5_ACTOR) questionSetActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "QuestionSet"
  val schemaName: String = "questionset"
  val defaultVersion:String = Platform.config.getNumber("v5_default_qumlVersion").toString

  def create() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
    questionSet.putAll(headers)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.createQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    getResult(ApiId.CREATE_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def read(identifier: String, mode: Option[String], fields: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val questionSet = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    questionSet.putAll(headers)
    questionSet.putAll(Map("identifier" -> identifier, "fields" -> fields.getOrElse(""), "mode" -> mode.getOrElse("read")).asJava)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.readQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    getResult(ApiId.READ_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def privateRead(identifier: String, mode: Option[String], fields: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val questionSet = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    questionSet.putAll(headers)
    questionSet.putAll(Map("identifier" -> identifier, "fields" -> fields.getOrElse(""), "mode" -> mode.getOrElse("read")).asJava)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.readPrivateQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    getResult(ApiId.READ_PRIVATE_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def update(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    questionSet.putAll(headers)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.updateQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    questionSetRequest.getContext.put("identifier", identifier)
    getResult(ApiId.UPDATE_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def review(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    questionSet.putAll(headers)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.reviewQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    questionSetRequest.getContext.put("identifier", identifier)
    getResult(ApiId.REVIEW_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def publish(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val headerMap = getRequestHeader("X-Request-Id", "requestId")
    val featureMap = getRequestHeader("X-Feature-Name", "featureName", "QuestionsetPublish")
    headerMap.putAll(featureMap)
    TelemetryManager.info(s"ENTRY:assessment: QuestionSet Publish V2 API | Request URL: ${request.uri} : Request Received For Identifier: ${identifier}", Map("requestId" -> headerMap.get("requestId").asInstanceOf[String], "cdata" -> Map("type" -> "Feature", "id" -> featureMap.get("featureName").asInstanceOf[String]).asJava).asJava.asInstanceOf[java.util.Map[String, AnyRef]])
    val body = requestBody()
    val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    questionSet.putAll(headers)
    headerMap.putAll(headers)
    val questionSetRequest = getRequest(questionSet, headerMap, QuestionSetOperations.publishQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    questionSetRequest.getContext.put("identifier", identifier)
    getResult(ApiId.PUBLISH_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def retire(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val questionSet = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    questionSet.putAll(headers)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.retireQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    questionSetRequest.getContext.put("identifier", identifier)
    getResult(ApiId.RETIRE_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def add() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    questionSet.putAll(headers)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.addQuestion.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    getResult(ApiId.ADD_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def remove() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    questionSet.putAll(headers)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.removeQuestion.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    getResult(ApiId.REMOVE_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def updateHierarchy() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val data = body.getOrDefault("data", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    data.putAll(headers)
    val questionSetRequest = getRequest(data, headers, "updateHierarchy")
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    getResult(ApiId.UPDATE_HIERARCHY, questionSetActor, questionSetRequest)
  }

  def getHierarchy(identifier: String, mode: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val questionSet = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    questionSet.putAll(headers)
    questionSet.putAll(Map("rootId" -> identifier, "mode" -> mode.getOrElse("")).asJava)
    val readRequest = getRequest(questionSet, headers, "getHierarchy")
    setRequestContext(readRequest, defaultVersion, objectType, schemaName)
    getResult(ApiId.GET_HIERARCHY, questionSetActor, readRequest)
  }

  def reject(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    questionSet.putAll(headers)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.rejectQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    questionSetRequest.getContext.put("identifier", identifier)
    getResult(ApiId.REJECT_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def importQuestionSet() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    body.putAll(headers)
    val questionSetRequest = getRequest(body, headers, QuestionSetOperations.importQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    getResult(ApiId.IMPORT_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def systemUpdate(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    questionSet.putAll(headers)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.systemUpdateQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    questionSetRequest.getContext.put("identifier", identifier);
    getResult(ApiId.SYSTEM_UPDATE_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def copy(identifier: String, mode: Option[String], copyType: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val questionSet = body.getOrDefault("questionset", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    questionSet.putAll(headers)
    questionSet.putAll(Map("identifier" -> identifier, "mode" -> mode.getOrElse(""), "copyType" -> copyType).asJava)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.copyQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    getResult(ApiId.COPY_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def updateComment(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    val commentList = body.getOrDefault("comments", new java.util.ArrayList[java.util.Map[String, Object]]()).asInstanceOf[java.util.List[java.util.Map[String, Object]]].asScala.toList
    val filteredComment: String = commentList.headOption.flatMap(comment => Option(comment.asScala.toMap.getOrElse("comment", "").asInstanceOf[String])).getOrElse("")
    val questionSet = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    questionSet.putAll(headers)
    questionSet.put("reviewComment", filteredComment)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.updateCommentQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    questionSetRequest.getContext.put("identifier", identifier)
    getResult(ApiId.UPDATE_COMMENT_QUESTION_SET, questionSetActor, questionSetRequest)
  }

  def readComment(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val questionSet = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    questionSet.putAll(headers)
    questionSet.putAll(Map("identifier" -> identifier, "fields" -> "", "mode" -> "read").asJava)
    val questionSetRequest = getRequest(questionSet, headers, QuestionSetOperations.readCommentQuestionSet.toString)
    setRequestContext(questionSetRequest, defaultVersion, objectType, schemaName)
    getResult(ApiId.READ_COMMENT_QUESTION_SET, questionSetActor, questionSetRequest)
  }
}
