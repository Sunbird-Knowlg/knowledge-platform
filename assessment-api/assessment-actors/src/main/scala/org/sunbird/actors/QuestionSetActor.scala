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
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.telemetry.logger.TelemetryManager

import scala.collection.JavaConverters
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
		//TODO: Remove mock response
		val response = ResponseHandler.OK
		response.put("identifier", "do_1234")
		Future(response)
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

	def update(request: Request): Future[Response] = ???

	def review(request: Request): Future[Response] = {
		getValidatedNodeToReview(request).flatMap(node => {
			validateQuestionHierarchy(request)
			val updateRequest = new Request(request)
			updateRequest.getContext.put("identifier", request.get("identifier"))
			updateRequest.put("versionKey", node.getMetadata.get("versionKey"))
			updateRequest.put("prevStatus", "Draft")
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

	def publish(request: Request): Future[Response] = ???

	def retire(request: Request): Future[Response] = ???

	def add(request: Request): Future[Response] = ???

	def remove(request: Request): Future[Response] = ???

	private def getValidatedNodeToReview(request: Request): Future[Node] = {
		request.put("mode", "edit")
		DataNode.read(request).map(node => {
			if(StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
				throw new ClientException("ERR_QUESTION_SET_REVIEW_FAILED", "Questions with visibility Parent, can't be sent for review individually.")
			if(!StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Draft"))
				throw new ClientException("ERR_QUESTION_SET_REVIEW_FAILED", "Question with status other than Draft can't be sent for review.")
			node
		})
	}

	private def validateQuestionHierarchy(request: Request): Unit = {
		getQuestionHierarchy(request).map(hierarchyString => {
			val hierarchy = if (!hierarchyString.asInstanceOf[String].isEmpty) {
				JsonUtils.deserialize(hierarchyString.asInstanceOf[String], classOf[java.util.HashMap[String, AnyRef]])
			} else new java.util.HashMap[String, AnyRef]()
			val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[java.util.Map[String, AnyRef]]]
			validateChildrenRecursive(children)
		})

	}

	private def validateChildrenRecursive(children: util.List[util.Map[String, AnyRef]]): Unit = {
		children.toList.foreach(content => {
			if(!StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Parent")
				&& StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))
				throw new ClientException("ERR_QUESTION_SET_REVIEW_FAILED", "Content with identifier: " + content.get("identifier") + "is not Live. Please Publish it.")
			validateChildrenRecursive(content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]])
		})
	}

	private def getQuestionHierarchy(request: Request): Future[Any] = {
		oec.graphService.readExternalProps(request, List("hierarchy")).map(response => {
			if (ResponseHandler.checkError(response) && ResponseHandler.isResponseNotFoundError(response)) {
				oec.graphService.readExternalProps(request, List("hierarchy")).map(resp => {
						resp.getResult.toMap.getOrElse("hierarchy", "{}").asInstanceOf[String]
					}) recover { case e: ResourceNotFoundException => TelemetryManager.log("No hierarchy is present in cassandra for identifier:" + request.get("identifier")) }
			} else Future(response.getResult.toMap.getOrElse("hierarchy", "{}").asInstanceOf[String])
		}).flatMap(f => f)
	}
}
