package org.sunbird.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.`object`.importer.{ImportConfig, ImportManager}
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.{DateUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.managers.{AssessmentManager, CopyManager}
import org.sunbird.utils.RequestUtil

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class QuestionActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	private lazy val importConfig = getImportConfig()
	private lazy val importMgr = new ImportManager(importConfig)

	override def onReceive(request: Request): Future[Response] = request.getOperation match {
		case "createQuestion" => AssessmentManager.create(request, "ERR_QUESTION_CREATE")
		case "readQuestion" => AssessmentManager.read(request, "question")
		case "readPrivateQuestion" => AssessmentManager.privateRead(request, "question")
		case "updateQuestion" => update(request)
		case "reviewQuestion" => review(request)
		case "publishQuestion" => publish(request)
		case "retireQuestion" => retire(request)
		case "importQuestion" => importQuestion(request)
		case "systemUpdateQuestion" => systemUpdate(request)
		case "listQuestions" => listQuestions(request)
		case "rejectQuestion" => reject(request)
		case "copyQuestion" => copy(request)
		case "bulkUploadQuestion" => bulkUpload(request)
		case _ => ERROR(request.getOperation)
	}

	def update(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		request.getRequest.put("artifactUrl",null)
		AssessmentManager.getValidatedNodeForUpdate(request, "ERR_QUESTION_UPDATE").flatMap(_ => AssessmentManager.updateNode(request))
	}

	def review(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		AssessmentManager.getValidatedNodeForReview(request, "ERR_QUESTION_REVIEW").flatMap(node => {
			val updateRequest = new Request(request)
			updateRequest.getContext.put("identifier", request.get("identifier"))
			updateRequest.putAll(Map("versionKey" -> node.getMetadata.get("versionKey"), "prevStatus" -> "Draft", "status" -> "Review", "lastStatusChangedOn" -> DateUtils.formatCurrentDate).asJava)
			AssessmentManager.updateNode(updateRequest)
		})
	}

	def publish(request: Request): Future[Response] = {
		val lastPublishedBy: String = request.getRequest.getOrDefault("lastPublishedBy", "").asInstanceOf[String]
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		AssessmentManager.getValidatedNodeForPublish(request, "ERR_QUESTION_PUBLISH").map(node => {
			if(StringUtils.isNotBlank(lastPublishedBy))
				node.getMetadata.put("lastPublishedBy", lastPublishedBy)
			AssessmentManager.pushInstructionEvent(node.getIdentifier, node)
			ResponseHandler.OK.putAll(Map[String, AnyRef]("identifier" -> node.getIdentifier.replace(".img", ""), "message" -> "Question is successfully sent for Publish").asJava)
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
				ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
			})
		})
	}

	def importQuestion(request: Request): Future[Response] = importMgr.importObject(request)

	def getImportConfig(): ImportConfig = {
		val requiredProps = Platform.getStringList("import.required_props.question", java.util.Arrays.asList("name", "code", "mimeType", "framework")).asScala.toList
		val validStages = Platform.getStringList("import.valid_stages.question", java.util.Arrays.asList("create", "upload", "review", "publish")).asScala.toList
		val propsToRemove = Platform.getStringList("import.remove_props.question", java.util.Arrays.asList()).asScala.toList
		val topicName = Platform.config.getString("import.output_topic_name")
		val reqLimit = Platform.getInteger("import.request_size_limit", 200)
		val validSourceStatus = Platform.getStringList("import.valid_source_status", java.util.Arrays.asList("Live", "Unlisted")).asScala.toList
		ImportConfig(topicName, reqLimit, requiredProps, validStages, propsToRemove, validSourceStatus)
	}

	def systemUpdate(request: Request): Future[Response] = {
		val identifier = request.getContext.get("identifier").asInstanceOf[String]
		RequestUtil.validateRequest(request)
		val readReq = new Request(request)
		val identifiers = new util.ArrayList[String](){{
			add(identifier)
			if (!identifier.endsWith(".img"))
				add(identifier.concat(".img"))
		}}
		readReq.put("identifiers", identifiers)
		DataNode.list(readReq).flatMap(response => {
			DataNode.systemUpdate(request, response,"", None)
		}).map(node => ResponseHandler.OK.put("identifier", identifier).put("status", "success"))
	}

	def listQuestions(request: Request): Future[Response] = {
		RequestUtil.validateListRequest(request)
		val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
		request.getRequest.put("fields", fields)
		DataNode.search(request).map(nodeList => {
			val questionList = nodeList.map(node => {
					NodeUtil.serialize(node, fields, node.getObjectType.toLowerCase.replace("Image", ""), request.getContext.get("version").asInstanceOf[String])
			}).asJava
			ResponseHandler.OK.put("questions", questionList).put("count", questionList.size)
		})
	}

	def reject(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		AssessmentManager.getValidateNodeForReject(request, "ERR_QUESTION_REJECT").flatMap(node => {
			val updateRequest = new Request(request)
			val date = DateUtils.formatCurrentDate
			updateRequest.getContext.put("identifier", request.getContext.get("identifier"))
			if(request.getRequest.containsKey("rejectComment"))
				updateRequest.put("rejectComment", request.get("rejectComment").asInstanceOf[String])
			updateRequest.putAll(Map("versionKey" -> node.getMetadata.get("versionKey"), "status" -> "Draft", "prevStatus" -> node.getMetadata.get("status"), "lastStatusChangedOn" -> date, "lastUpdatedOn" -> date).asJava)
			AssessmentManager.updateNode(updateRequest)
			})
		}

	def copy(request: Request): Future[Response] ={
		RequestUtil.restrictProperties(request)
		CopyManager.copy(request)
	}

	def bulkUpload(request: Request): Future[Response] = {
		AssessmentManager.create(request, "ERR_QUESTION_CREATE")
	}
}
