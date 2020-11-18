package org.sunbird.actors

import java.util

import javax.inject.Inject
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.{DateUtils, JsonUtils}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.managers.QuestionManager
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.RequestUtil

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
		case _ => ERROR(request.getOperation)
	}

	def create(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		val visibility: String = request.getRequest.getOrDefault("visibility", "").asInstanceOf[String]
		if (StringUtils.isBlank(visibility))
			throw new ClientException("ERR_QUESTION_SET_CREATE", "Visibility is a mandatory parameter")
		visibility match {
			case "Parent" => if (!request.getRequest.containsKey("parent"))
				throw new ClientException("ERR_QUESTION_SET_CREATE", "For visibility Parent, parent id is mandatory") else
				request.getRequest.put("parent", List[java.util.Map[String, AnyRef]](Map("identifier" -> request.get("parent")).asJava).asJava)
			case "Public" => if (request.getRequest.containsKey("parent")) throw new ClientException("ERR_QUESTION_SET_CREATE", "For visibility Public, question can't have parent id")
			case _ => throw new ClientException("ERR_QUESTION_SET_CREATE", "Visibility should be one of [\"Parent\", \"Public\"]")
		}
		DataNode.create(request).map(node => {
			val response = ResponseHandler.OK
			response.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
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
		DataNode.read(request).flatMap(node => {
			request.getRequest.getOrDefault("visibility", "") match {
				case "Public" => request.put("parent", null)
				case "Parent" => if (!node.getMetadata.containsKey("parent") || !request.getRequest.containsKey("parent"))
					throw new ClientException("ERR_QUESTION_CREATE_FAILED", "For visibility Parent, parent id is mandatory")
				else request.getRequest.put("parent", List[java.util.Map[String, AnyRef]](Map("identifier" -> request.get("parent")).asJava).asJava)
				case _ => request
			}
			DataNode.update(request).map(node => {
				val response: Response = ResponseHandler.OK
				response.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
				response
			})
		})
	}

	def review(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		getValidatedNodeToReview(request).flatMap(node => {
//			validateQuestionHierarchy(request)
			validateChildrenRecursive(node.getOutRelations)
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
		QuestionManager.getValidatedNodeToPublish(request).map(node => {
			QuestionManager.pushInstructionEvent(node.getIdentifier, node)
			val response = ResponseHandler.OK()
			response.putAll(Map[String,AnyRef]("identifier" -> node.getIdentifier.replace(".img", ""), "message" -> "Question is successfully sent for Publish").asJava)
			response
		})
	}
	def retire(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		getValidatedNodeToRetire(request).flatMap(node => {
			val updateRequest = new Request(request)
			updateRequest.put("identifiers", java.util.Arrays.asList(request.get("identifier").asInstanceOf[String], request.get("identifier").asInstanceOf[String] + ".img"))
			val updateMetadata: util.Map[String, AnyRef] = Map("prevState" -> node.getMetadata.get("status"), "status" -> "Retired", "lastStatusChangedOn" -> DateUtils.formatCurrentDate, "lastUpdatedOn" -> DateUtils.formatCurrentDate).asJava
			updateRequest.put("metadata", updateMetadata)
			DataNode.bulkUpdate(updateRequest).map(nodes => {
				val response: Response = ResponseHandler.OK
				response.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
				response
			})
		})
	}

	def add(request: Request): Future[Response] = {
		request.getRequest.put("identifier", request.getContext.get("identifier"))
		getValidatedQuestionSet(request).flatMap(node => {
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
		getValidatedQuestionSet(request).flatMap(node => {
			val requestChildIds = request.getRequest.getOrDefault("children", "").asInstanceOf[util.List[String]]
			val (publicChildIds: List[String], parentChildIds: List[String]) = getChildIdsFromRelation(node)
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
					DataNode.bulkUpdate(request).map(response => {
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

	private def getValidatedNodeToReview(request: Request): Future[Node] = {
		request.put("mode", "edit")
		DataNode.read(request).map(node => {
			if(StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
				throw new ClientException("ERR_QUESTION_SET_REVIEW", "Questions with visibility Parent, can't be sent for review individually.")
			if(!StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Draft"))
				throw new ClientException("ERR_QUESTION_SET_REVIEW", "Question with status other than Draft can't be sent for review.")
			node
		})
	}

//	private def validateQuestionHierarchy(request: Request): Unit = {
//		getQuestionHierarchy(request).map(hierarchyString => {
//			val hierarchy = if (!hierarchyString.asInstanceOf[String].isEmpty) {
//				JsonUtils.deserialize(hierarchyString.asInstanceOf[String], classOf[java.util.HashMap[String, AnyRef]])
//			} else new java.util.HashMap[String, AnyRef]()
//			val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[java.util.Map[String, AnyRef]]]
//			validateChildrenRecursive(children)
//		})
//
//	}


//	private def validateChildrenRecursive(children: util.List[util.Map[String, AnyRef]]): Unit = {
//		children.toList.foreach(content => {
//			if(!StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Parent")
//				&& StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))
//				throw new ClientException("ERR_QUESTION_SET_REVIEW", "Content with identifier: " + content.get("identifier") + "is not Live. Please Publish it.")
//			validateChildrenRecursive(content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]])
//		})
//	}

	private def validateChildrenRecursive(outRelations: util.List[Relation]): Unit = {
		outRelations.toList.foreach(relation => {
			if(!StringUtils.equalsAnyIgnoreCase(relation.getEndNodeMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent")
				&& !StringUtils.equalsIgnoreCase(relation.getEndNodeMetadata.getOrDefault("status", "").asInstanceOf[String], "Live"))
				throw new ClientException("ERR_QUESTION_SET_REVIEW", "Content with identifier: " + relation.getEndNodeId + "is not Live. Please Publish it.")
		})
	}

	private def getQuestionHierarchy(request: Request): Future[Any] = {
		oec.graphService.readExternalProps(request, List("hierarchy")).flatMap(response => {
			if (ResponseHandler.checkError(response) && ResponseHandler.isResponseNotFoundError(response)) {
				oec.graphService.readExternalProps(request, List("hierarchy")).map(resp => {
						resp.getResult.toMap.getOrElse("hierarchy", "{}").asInstanceOf[String]
					}) recover { case e: ResourceNotFoundException => TelemetryManager.log("No hierarchy is present in cassandra for identifier:" + request.get("identifier")) }
			} else Future(response.getResult.toMap.getOrElse("hierarchy", "{}").asInstanceOf[String])
		})
	}

	private def getValidatedNodeToRetire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		DataNode.read(request).map(node => {
			if (StringUtils.equalsIgnoreCase("Retired", node.getMetadata.get("status").asInstanceOf[String]))
				throw new ClientException("ERR_QUESTION_SET_RETIRE", "Question with Identifier " + node.getIdentifier + " is already Retired.")
			node
		})
	}

	private def getValidatedQuestionSet(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		request.put("mode", "edit")
		DataNode.read(request).map(node => {
			if (!StringUtils.equalsIgnoreCase("QuestionSet", node.getObjectType))
				throw new ClientException("ERR_QUESTION_SET_ADD", "Node with Identifier " + node.getIdentifier + " is not a Question Set")
			node
		})
	}

	private def getChildIdsFromRelation(node: Node): (List[String], List[String]) = {
		val outRelations: List[Relation] = if (node.getOutRelations != null) node.getOutRelations.asScala.toList else List[Relation]()
		val visibilityIdMap: Map[String, List[String]] = outRelations
			.groupBy(_.getEndNodeMetadata.get("visibility").asInstanceOf[String])
			.mapValues(_.map(_.getEndNodeId).toList)
		(visibilityIdMap.getOrDefault("Public", List()), visibilityIdMap.getOrDefault("Parent", List()))
	}
}
