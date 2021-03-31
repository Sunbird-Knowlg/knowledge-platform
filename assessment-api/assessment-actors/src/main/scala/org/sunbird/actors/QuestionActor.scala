package org.sunbird.actors

import java.util

import javax.inject.Inject
import org.sunbird.`object`.importer.ImportManager
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.{DateUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.managers.AssessmentManager
import org.sunbird.utils.RequestUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

class QuestionActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = request.getOperation match {
		case "createQuestion" => AssessmentManager.create(request, "ERR_QUESTION_CREATE")
		case "readQuestion" => AssessmentManager.read(request, "question")
		case "updateQuestion" => update(request)
		case "reviewQuestion" => review(request)
		case "publishQuestion" => publish(request)
		case "retireQuestion" => retire(request)
		case "importQuestion" => importQuestion(request)
		case _ => ERROR(request.getOperation)
	}

	def update(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		AssessmentManager.getValidatedNodeForUpdate(request, "ERR_QUESTION_UPDATE").flatMap(_ => AssessmentManager.updateNode(request))
	}

	def review(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		AssessmentManager.getValidatedNodeForReview(request, "ERR_QUESTION_REVIEW").flatMap(node => {
			val updateRequest = new Request(request)
			updateRequest.getContext.put("identifier", request.get("identifier"))
			updateRequest.putAll(Map("versionKey" -> node.getMetadata.get("versionKey"), "prevState" -> "Draft", "status" -> "Review", "lastStatusChangedOn" -> DateUtils.formatCurrentDate).asJava)
			AssessmentManager.updateNode(updateRequest)
		})
	}

	def publish(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		AssessmentManager.getValidatedNodeForPublish(request, "ERR_QUESTION_PUBLISH").map(node => {
			AssessmentManager.pushInstructionEvent(node.getIdentifier, node)
			val response = ResponseHandler.OK()
			response.putAll(Map[String, AnyRef]("identifier" -> node.getIdentifier.replace(".img", ""), "message" -> "Question is successfully sent for Publish").asJava)
			response
		})
	}

	def retire(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		AssessmentManager.getValidatedNodeForRetire(request, "ERR_QUESTION_RETIRE").flatMap(node => {
			val updateRequest = new Request(request)
			updateRequest.put("identifiers", java.util.Arrays.asList(request.get("identifier").asInstanceOf[String], request.get("identifier").asInstanceOf[String] + ".img"))
			val updateMetadata: util.Map[String, AnyRef] = Map[String, AnyRef]("status" -> "Retired", "lastStatusChangedOn" -> DateUtils.formatCurrentDate).asJava
			updateRequest.put("metadata", updateMetadata)
			DataNode.bulkUpdate(updateRequest).map(_ => {
				val response: Response = ResponseHandler.OK
				response.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
				response
			})
		})
	}

	def importQuestion(request: Request): Future[Response] = {
		val configMap = new util.HashMap[String, AnyRef]() {{
			put("REQUIRED_PROPS", Platform.getStringList("import.required_props.question", java.util.Arrays.asList("name", "code", "mimeType", "framework")))
			put("VALID_OBJECT_STAGE", Platform.getStringList("import.valid_stages.question", java.util.Arrays.asList("create", "upload", "review", "publish")))
			put("PROPS_TO_REMOVE", Platform.getStringList("import.remove_props.question", new util.ArrayList[String]()))
		}}
		request.getContext.putAll(configMap)
		ImportManager.importObject(request)
	}

}
