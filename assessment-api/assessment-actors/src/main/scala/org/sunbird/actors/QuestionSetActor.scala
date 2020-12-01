package org.sunbird.actors

import java.util

import javax.inject.Inject
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.DateUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Relation
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.managers.QuestionManager
import org.sunbird.utils.{RequestUtil}

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

class QuestionSetActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = request.getOperation match {
		case "createQuestionSet" => create(request)
		case "readQuestionSet" => read(request)
		case "updateQuestionSet" => update(request)
		case "reviewQuestionSet" => review(request)
		case "publishQuestionSet" => publish(request)
		case "retireQuestionSet" => retire(request)
		case "addQuestion" => add(request)
		case "removeQuestion" => remove(request)
		case "updateHierarchyQuestion" => updateHierarchy(request)
		case "readHierarchyQuestion" => readHierarchy(request)
		case _ => ERROR(request.getOperation)
	}

	def create(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		val visibility: String = request.getRequest.getOrDefault("visibility", "").asInstanceOf[String]
		if (StringUtils.isNotBlank(visibility) && StringUtils.equalsIgnoreCase(visibility, "Parent"))
			throw new ClientException("ERR_QUESTION_CREATE", "Visibility cannot be Parent")
		DataNode.create(request).map(node => {
			val response = ResponseHandler.OK
			response.putAll(Map("identifier" -> node.getIdentifier, "versionKey" -> node.getMetadata.get("versionKey")).asJava)
			response
		})
	}

	def read(request: Request): Future[Response] = {
		val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
		request.getRequest.put("fields", fields)
		DataNode.read(request).map(node => {
			val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, node.getObjectType.toLowerCase.replace("image", ""), request.getContext.get("version").asInstanceOf[String])
			metadata.put("identifier", node.getIdentifier.replace(".img", ""))
			val response: Response = ResponseHandler.OK
			response.put("questionset", metadata)
			response
		})
	}

	def update(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		DataNode.update(request).map(node => {
			val response: Response = ResponseHandler.OK
			response.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
			response
		})
	}

	def review(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		QuestionManager.getQuestionSetNodeToReview(request).flatMap(node => {
//			validateQuestionHierarchy(request)
			QuestionManager.validateChildrenRecursive(if (node.getOutRelations != null) node.getOutRelations.asScala.toList else List[Relation]())
			val updateRequest = new Request(request)
			updateRequest.getContext.put("identifier", request.get("identifier"))
			updateRequest.put("versionKey", node.getMetadata.get("versionKey"))
			updateRequest.put("prevState", "Draft")
			updateRequest.put("status", "Review")
			updateRequest.put("lastStatusChangedOn", DateUtils.formatCurrentDate)
			updateRequest.put("lastUpdatedOn", DateUtils.formatCurrentDate)
			DataNode.update(updateRequest).map(node => {
				val response: Response = ResponseHandler.OK
				val identifier: String = node.getIdentifier.replace(".img", "")
				response.put("identifier", identifier)
				response.put("versionKey", node.getMetadata.get("versionKey"))
				response
			})
		})
	}


	def publish(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		QuestionManager.getQuestionNodeToPublish(request).map(node => {
			QuestionManager.pushInstructionEvent(node.getIdentifier, node)
			val response = ResponseHandler.OK()
			response.putAll(Map[String,AnyRef]("identifier" -> node.getIdentifier.replace(".img", ""), "message" -> "Question is successfully sent for Publish").asJava)
			response
		})
	}

	def retire(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		QuestionManager.getQuestionSetNodeToRetire(request).flatMap(node => {
			val updateRequest = new Request(request)
			updateRequest.put("identifiers", java.util.Arrays.asList(request.get("identifier").asInstanceOf[String], request.get("identifier").asInstanceOf[String] + ".img"))
			val updateMetadata: util.Map[String, AnyRef] = Map("prevState" -> node.getMetadata.get("status"), "status" -> "Retired", "lastStatusChangedOn" -> DateUtils.formatCurrentDate, "lastUpdatedOn" -> DateUtils.formatCurrentDate).asJava
			updateRequest.put("metadata", updateMetadata)
			DataNode.bulkUpdate(updateRequest).map(_ => {
				val response: Response = ResponseHandler.OK
				response.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
				response
			})
		})
	}

	def add(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		QuestionManager.getValidatedQuestionSet(request).flatMap(node => {
			val requestChildrenIds = request.getRequest.getOrDefault("children", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
			val nodeChildrenIds: List[String] = if (node.getOutRelations != null) (for (relation <- node.getOutRelations) yield relation.getEndNodeId).toList else List()
			val childrenIds = CollectionUtils.union(requestChildrenIds, nodeChildrenIds)
			val childrenMaps: List[util.Map[String, AnyRef]] = (for (child <- childrenIds) yield Map("identifier" -> child.asInstanceOf[AnyRef]).asJava).toList
			if (childrenMaps.nonEmpty) request.put("children", childrenMaps.asJava)
			request.getRequest.remove("mode")
			DataNode.update(request).map(node => {
				val response: Response = ResponseHandler.OK
				response.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
				response
			})
		})
	}

	def remove(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		QuestionManager.getValidatedQuestionSet(request).flatMap(node => {
			val requestChildIds = request.getRequest.getOrDefault("children", "").asInstanceOf[util.List[String]]
			val (publicChildIds: List[String], parentChildIds: List[String]) = QuestionManager.getChildIdsFromRelation(node)
			val updatedChildren = publicChildIds diff requestChildIds
			val childrenMaps: List[util.Map[String, AnyRef]] = for (child <- updatedChildren) yield Map("identifier" -> child.asInstanceOf[AnyRef]).asJava
			if (childrenMaps.nonEmpty) request.put("children", childrenMaps.asJava) else request.put("children", new util.ArrayList[String]())
			request.getRequest.remove("mode")
			DataNode.update(request).flatMap(node => {
				if(parentChildIds.nonEmpty) {
					val retireRequest = new Request(request)
					retireRequest.put("identifiers", parentChildIds)
					val retireMetadata: util.Map[String, AnyRef] = Map("prevState" -> node.getMetadata.get("status"), "status" -> "Retired", "lastStatusChangedOn" -> DateUtils.formatCurrentDate, "lastUpdatedOn" -> DateUtils.formatCurrentDate).asJava
					retireRequest.put("metadata", retireMetadata)
					DataNode.bulkUpdate(request).map(_ => {
						val response: Response = ResponseHandler.OK
						response.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
						response
					})
				} else {
					val response: Response = ResponseHandler.OK
					response.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
					Future(response)
				}
			})
		})
	}

	def updateHierarchy(request: Request): Future[Response] = {

		Future(ResponseHandler.OK())
	}

	def readHierarchy(request: Request): Future[Response] = {
		Future(ResponseHandler.OK())
	}

//	def processUpdateHierarchyRequest(request: Request): (String, Map[String,AnyRef], Map[String, AnyRef]) =  {
//		val nodesModified: Map[String, AnyRef] = request.getRequest.get("nodesModifier").asInstanceOf[util.Map[String, AnyRef]].asScala.toMap
//		val hierarchy:Map[String, AnyRef]  = request.getRequest.get("hierarchy").asInstanceOf[util.Map[String, AnyRef]].asScala.toMap
//		if (StringUtils.isEmpty(rootId) && StringUtils.isAllBlank(rootId) || StringUtils.contains(rootId, ".img"))
//			throw new ClientException("ERR_INVALID_ROOT_ID", "Please Provide Valid Root Node Identifier")
//		(rootId, nodesModified, hierarchy)
//	}


}
