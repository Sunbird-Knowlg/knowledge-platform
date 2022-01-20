package org.sunbird.actors

import java.util
import javax.inject.Inject
import org.apache.commons.collections4.CollectionUtils
import org.sunbird.`object`.importer.{ImportConfig, ImportManager}
import org.sunbird.actor.core.BaseActor
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{DateUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.dac.model.Node
import org.sunbird.managers.HierarchyManager.hierarchyPrefix
import org.sunbird.managers.{AssessmentManager, CopyManager, HierarchyManager, UpdateHierarchyManager}
import org.sunbird.utils.RequestUtil

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class QuestionSetActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher
	private lazy val importConfig = getImportConfig()
	private lazy val importMgr = new ImportManager(importConfig)

	override def onReceive(request: Request): Future[Response] = request.getOperation match {
		case "createQuestionSet" => AssessmentManager.create(request, "ERR_QUESTION_SET_CREATE")
		case "readQuestionSet" => AssessmentManager.read(request, "questionset")
		case "readPrivateQuestionSet" => AssessmentManager.privateRead(request, "questionset")
		case "updateQuestionSet" => update(request)
		case "reviewQuestionSet" => review(request)
		case "publishQuestionSet" => publish(request)
		case "retireQuestionSet" => retire(request)
		case "addQuestion" => HierarchyManager.addLeafNodesToHierarchy(request)
		case "removeQuestion" => HierarchyManager.removeLeafNodesFromHierarchy(request)
		case "updateHierarchy" => UpdateHierarchyManager.updateHierarchy(request)
		case "getHierarchy" => HierarchyManager.getHierarchy(request)
		case "rejectQuestionSet" => reject(request)
		case "importQuestionSet" => importQuestionSet(request)
		case "systemUpdateQuestionSet" => systemUpdate(request)
		case "copyQuestionSet" => copy(request)
		case _ => ERROR(request.getOperation)
	}

	def update(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		AssessmentManager.getValidatedNodeForUpdate(request, "ERR_QUESTION_SET_UPDATE").flatMap(_ => AssessmentManager.updateNode(request))
	}

	def review(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		request.getRequest.put("mode", "edit")
		AssessmentManager.getValidatedNodeForReview(request, "ERR_QUESTION_SET_REVIEW").flatMap(node => {
			AssessmentManager.getQuestionSetHierarchy(request, node).flatMap(hierarchyString => {
				AssessmentManager.validateQuestionSetHierarchy(hierarchyString.asInstanceOf[String], node.getMetadata.getOrDefault("createdBy", "").asInstanceOf[String])
				val (updatedHierarchy, nodeIds) = AssessmentManager.updateHierarchy(hierarchyString.asInstanceOf[String], "Review", node.getMetadata.getOrDefault("createdBy", "").asInstanceOf[String])
				val updateReq = new Request(request)
				val date = DateUtils.formatCurrentDate
				updateReq.putAll(Map("identifiers" -> nodeIds, "metadata" -> Map("status" -> "Review", "prevStatus" -> node.getMetadata.get("status"), "lastStatusChangedOn" -> date, "lastUpdatedOn" -> date).asJava).asJava)
				updateHierarchyNodes(updateReq, node, Map("status" -> "Review", "hierarchy" -> updatedHierarchy), nodeIds)
			})
		})
	}

	def publish(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		AssessmentManager.getValidatedNodeForPublish(request, "ERR_QUESTION_SET_PUBLISH").flatMap(node => {
			AssessmentManager.getQuestionSetHierarchy(request, node).map(hierarchyString => {
				AssessmentManager.validateQuestionSetHierarchy(hierarchyString.asInstanceOf[String], node.getMetadata.getOrDefault("createdBy", "").asInstanceOf[String])
				AssessmentManager.pushInstructionEvent(node.getIdentifier, node)
				ResponseHandler.OK.putAll(Map[String, AnyRef]("identifier" -> node.getIdentifier.replace(".img", ""), "message" -> "Question is successfully sent for Publish").asJava)
			})
		})
	}

	def retire(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		AssessmentManager.getValidatedNodeForRetire(request, "ERR_QUESTION_SET_RETIRE").flatMap(node => {
			val updateRequest = new Request(request)
			updateRequest.put("identifiers", java.util.Arrays.asList(request.get("identifier").asInstanceOf[String], request.get("identifier").asInstanceOf[String] + ".img"))
			val updateMetadata: util.Map[String, AnyRef] = Map("prevStatus" -> node.getMetadata.get("status"), "status" -> "Retired", "lastStatusChangedOn" -> DateUtils.formatCurrentDate, "lastUpdatedOn" -> DateUtils.formatCurrentDate).asJava
			updateRequest.put("metadata", updateMetadata)
			DataNode.bulkUpdate(updateRequest).map(_ => {
				ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
			})
		})
	}

	def reject(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		request.getRequest.put("mode", "edit")
		AssessmentManager.getValidateNodeForReject(request, "ERR_QUESTION_SET_REJECT").flatMap(node => {
			AssessmentManager.getQuestionSetHierarchy(request, node).flatMap(hierarchyString => {
				AssessmentManager.validateQuestionSetHierarchy(hierarchyString.asInstanceOf[String], node.getMetadata.getOrDefault("createdBy", "").asInstanceOf[String])
				val (updatedHierarchy, nodeIds) = AssessmentManager.updateHierarchy(hierarchyString.asInstanceOf[String], "Draft", node.getMetadata.getOrDefault("createdBy", "").asInstanceOf[String])
				val updateReq = new Request(request)
				val date = DateUtils.formatCurrentDate
				updateReq.putAll(Map("identifiers" -> nodeIds, "metadata" -> Map("status" -> "Draft", "prevStatus" -> node.getMetadata.get("status"), "lastStatusChangedOn" -> date, "lastUpdatedOn" -> date).asJava).asJava)
				val metadata: Map[String, AnyRef] = Map("status" -> "Draft", "hierarchy" -> updatedHierarchy)
				val updatedMetadata = if(request.getRequest.containsKey("rejectComment")) (metadata ++ Map("rejectComment" -> request.get("rejectComment").asInstanceOf[String])) else metadata
				updateHierarchyNodes(updateReq, node, updatedMetadata, nodeIds)
			})
		})
	}

	def updateHierarchyNodes(request: Request, node: Node, metadata: Map[String, AnyRef], nodeIds: util.List[String]): Future[Response] = {
		if (CollectionUtils.isNotEmpty(nodeIds)) {
			DataNode.bulkUpdate(request).flatMap(_ => {
				updateNode(request, node, metadata)
			})
		} else {
			updateNode(request, node, metadata)
		}
	}

	def updateNode(request: Request, node: Node,  metadata: Map[String, AnyRef]): Future[Response] = {
		val updateRequest = new Request(request)
		val date = DateUtils.formatCurrentDate
		val fMeta: Map[String, AnyRef] = Map("versionKey" -> node.getMetadata.get("versionKey"), "prevStatus" -> node.getMetadata.get("status"), "lastStatusChangedOn" -> date, "lastUpdatedOn" -> date) ++ metadata
		updateRequest.getContext.put("identifier",  request.getContext.get("identifier"))
		updateRequest.putAll(fMeta.asJava)
		DataNode.update(updateRequest).map(_ => {
			ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
		})
	}

	def importQuestionSet(request: Request): Future[Response] = importMgr.importObject(request)

	def getImportConfig(): ImportConfig = {
		val requiredProps = Platform.getStringList("import.required_props.questionset", java.util.Arrays.asList("name", "code", "mimeType", "framework")).asScala.toList
		val validStages = Platform.getStringList("import.valid_stages.questionset", java.util.Arrays.asList("create", "upload", "review", "publish")).asScala.toList
		val propsToRemove = Platform.getStringList("import.remove_props.questionset", java.util.Arrays.asList()).asScala.toList
		val topicName = Platform.config.getString("import.output_topic_name")
		val reqLimit = Platform.getInteger("import.request_size_limit", 200)
		ImportConfig(topicName, reqLimit, requiredProps, validStages, propsToRemove)
	}

	def systemUpdate(request: Request): Future[Response] = {
		val identifier = request.getContext.get("identifier").asInstanceOf[String]
		RequestUtil.validateRequest(request)
		if(Platform.getBoolean("questionset.cache.enable", false))
			RedisCache.delete(hierarchyPrefix + identifier)

		val readReq = new Request(request)
		val identifiers = new util.ArrayList[String](){{
			add(identifier)
			if (!identifier.endsWith(".img"))
				add(identifier.concat(".img"))
		}}
		readReq.put("identifiers", identifiers)
		DataNode.list(readReq).flatMap(response => {
			DataNode.systemUpdate(request, response,"questionSet", Some(HierarchyManager.getHierarchy))
		}).map(node => ResponseHandler.OK.put("identifier", identifier).put("status", "success"))
	}

	def copy(request: Request): Future[Response] ={
		RequestUtil.restrictProperties(request)
		CopyManager.copy(request)
	}
}
