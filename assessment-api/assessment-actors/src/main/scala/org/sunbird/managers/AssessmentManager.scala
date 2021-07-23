package org.sunbird.managers

import java.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.telemetry.util.LogTelemetryEventUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters
import scala.collection.JavaConverters._

object AssessmentManager {

	val skipValidation: Boolean = Platform.getBoolean("assessment.skip.validation", true)

	def create(request: Request, errCode: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val visibility: String = request.getRequest.getOrDefault("visibility", "").asInstanceOf[String]
		if (StringUtils.isNotBlank(visibility) && StringUtils.equalsIgnoreCase(visibility, "Parent"))
			throw new ClientException(errCode, "Visibility cannot be Parent!")
		DataNode.create(request).map(node => {
			val response = ResponseHandler.OK
			response.putAll(Map("identifier" -> node.getIdentifier, "versionKey" -> node.getMetadata.get("versionKey")).asJava)
			response
		})
	}

	def read(request: Request, resName: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
		request.getRequest.put("fields", fields)
		DataNode.read(request).map(node => {
			val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, node.getObjectType.toLowerCase.replace("Image", ""), request.getContext.get("version").asInstanceOf[String])
			metadata.put("identifier", node.getIdentifier.replace(".img", ""))
			ResponseHandler.OK.put(resName, metadata)
		})
	}

	def updateNode(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		DataNode.update(request).map(node => {
			ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
		})
	}

	def getValidatedNodeForUpdate(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		DataNode.read(request).map(node => {
			if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
				throw new ClientException(errCode, node.getMetadata.getOrDefault("objectType", "").asInstanceOf[String].replace("Image", "") + " with visibility Parent, can't be updated individually.")
			node
		})
	}

	def getValidatedNodeForReview(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		request.put("mode", "edit")
		DataNode.read(request).map(node => {
			if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} with visibility Parent, can't be sent for review individually.")
			if (!StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Draft"))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} with status other than Draft can't be sent for review.")
			node
		})
	}

	def getValidatedNodeForPublish(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		request.put("mode", "edit")
		DataNode.read(request).map(node => {
			if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} with visibility Parent, can't be sent for publish individually.")
			if (StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Processing"))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} having Processing status can't be sent for publish.")
			node
		})
	}

	def getValidatedNodeForRetire(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		DataNode.read(request).map(node => {
			if (StringUtils.equalsIgnoreCase("Retired", node.getMetadata.get("status").asInstanceOf[String]))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} with identifier : ${node.getIdentifier} is already Retired.")
			node
		})
	}

	def getValidateNodeForReject(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		request.put("mode", "edit")
		DataNode.read(request).map(node => {
			if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} with visibility Parent, can't be sent for reject individually.")
			if (!StringUtils.equalsIgnoreCase("Review", node.getMetadata.get("status").asInstanceOf[String]))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} is not in 'Review' state for identifier: " + node.getIdentifier)
			node
		})
	}

	def getValidatedQuestionSet(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		request.put("mode", "edit")
		DataNode.read(request).map(node => {
			if (!StringUtils.equalsIgnoreCase("QuestionSet", node.getObjectType))
				throw new ClientException("ERR_QUESTION_SET_ADD", "Node with Identifier " + node.getIdentifier + " is not a Question Set")
			node
		})
	}

	def validateQuestionSetHierarchy(hierarchyString: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Unit = {
		if (!skipValidation) {
			val hierarchy = if (!hierarchyString.asInstanceOf[String].isEmpty) {
				JsonUtils.deserialize(hierarchyString.asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
			} else
				new java.util.HashMap[String, AnyRef]()
			val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.List[java.util.Map[String, AnyRef]]]
			validateChildrenRecursive(children)
		}
	}

	def getQuestionSetHierarchy(request: Request, rootNode: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Any] = {
		oec.graphService.readExternalProps(request, List("hierarchy")).flatMap(response => {
			if (ResponseHandler.checkError(response) && ResponseHandler.isResponseNotFoundError(response)) {
				if (StringUtils.equalsIgnoreCase("Live", rootNode.getMetadata.get("status").asInstanceOf[String]))
					throw new ServerException("ERR_QUESTION_SET_REVIEW", "No hierarchy is present in cassandra for identifier:" + rootNode.getIdentifier)
				request.put("identifier", if (!rootNode.getIdentifier.endsWith(".img")) rootNode.getIdentifier + ".img" else rootNode.getIdentifier)
				oec.graphService.readExternalProps(request, List("hierarchy")).map(resp => {
					resp.getResult.toMap.getOrElse("hierarchy", "{}").asInstanceOf[String]
				}) recover { case e: ResourceNotFoundException => TelemetryManager.log("No hierarchy is present in cassandra for identifier:" + request.get("identifier")) }
			} else Future(response.getResult.toMap.getOrElse("hierarchy", "{}").asInstanceOf[String])
		})
	}

	private def validateChildrenRecursive(children: util.List[util.Map[String, AnyRef]]): Unit = {
		children.toList.foreach(content => {
			if (!StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Parent")
			  && !StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))
				throw new ClientException("ERR_QUESTION_SET", "Content with identifier: " + content.get("identifier") + "is not Live. Please Publish it.")
			validateChildrenRecursive(content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]])
		})
	}

	def getChildIdsFromRelation(node: Node): (List[String], List[String]) = {
		val outRelations: List[Relation] = if (node.getOutRelations != null) node.getOutRelations.asScala.toList else List[Relation]()
		val visibilityIdMap: Map[String, List[String]] = outRelations
		  .groupBy(_.getEndNodeMetadata.get("visibility").asInstanceOf[String])
		  .mapValues(_.map(_.getEndNodeId).toList)
		(visibilityIdMap.getOrDefault("Default", List()), visibilityIdMap.getOrDefault("Parent", List()))
	}

	def updateHierarchy(hierarchyString: String, status: String): (java.util.Map[String, AnyRef], java.util.List[String]) = {
		val hierarchy = if (!hierarchyString.asInstanceOf[String].isEmpty) {
			JsonUtils.deserialize(hierarchyString.asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
		} else
			new java.util.HashMap[String, AnyRef]()
		val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.List[java.util.Map[String, AnyRef]]]
		hierarchy.put("status", status)
		val childrenToUpdate: List[String] = updateChildrenRecursive(children, status, List())
		(hierarchy, childrenToUpdate.asJava)
	}

	private def updateChildrenRecursive(children: util.List[util.Map[String, AnyRef]], status: String, idList: List[String]): List[String] = {
		children.toList.flatMap(content => {
			val updatedIdList: List[String] =
				if (StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Parent")) {
					content.put("lastStatusChangedOn", DateUtils.formatCurrentDate)
					content.put("status", status)
					content.put("prevStatus", "Draft")
					content.put("lastUpdatedOn", DateUtils.formatCurrentDate)
					content.get("identifier").asInstanceOf[String] :: idList
				} else idList
			val list = updateChildrenRecursive(content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]], status, updatedIdList)
			list ++ updatedIdList
		})
	}

	@throws[Exception]
	def pushInstructionEvent(identifier: String, node: Node)(implicit oec: OntologyEngineContext): Unit = {
		val (actor, context, objData, eData) = generateInstructionEventMetadata(identifier.replace(".img", ""), node)
		val beJobRequestEvent: String = LogTelemetryEventUtil.logInstructionEvent(actor.asJava, context.asJava, objData.asJava, eData)
		val topic: String = Platform.getString("kafka.topics.instruction", "sunbirddev.learning.job.request")
		if (StringUtils.isBlank(beJobRequestEvent)) throw new ClientException("BE_JOB_REQUEST_EXCEPTION", "Event is not generated properly.")
		oec.kafkaClient.send(beJobRequestEvent, topic)
	}

	def generateInstructionEventMetadata(identifier: String, node: Node): (Map[String, AnyRef], Map[String, AnyRef], Map[String, AnyRef], util.Map[String, AnyRef]) = {
		val metadata: util.Map[String, AnyRef] = node.getMetadata
		val publishType = if (StringUtils.equalsIgnoreCase(metadata.getOrDefault("status", "").asInstanceOf[String], "Unlisted")) "unlisted" else "public"
		val eventMetadata = Map("identifier" -> identifier, "mimeType" -> metadata.getOrDefault("mimeType", ""), "objectType" -> node.getObjectType.replace("Image", ""), "pkgVersion" -> metadata.getOrDefault("pkgVersion", 0.asInstanceOf[AnyRef]), "lastPublishedBy" -> metadata.getOrDefault("lastPublishedBy", ""))
		val actor = Map("id" -> s"${node.getObjectType.toLowerCase().replace("image", "")}-publish", "type" -> "System".asInstanceOf[AnyRef])
		val context = Map("channel" -> metadata.getOrDefault("channel", ""), "pdata" -> Map("id" -> "org.sunbird.platform", "ver" -> "1.0").asJava, "env" -> Platform.getString("cloud_storage.env", "dev"))
		val objData = Map("id" -> identifier, "ver" -> metadata.getOrDefault("versionKey", ""))
		val eData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef] {{
				put("action", "publish")
				put("publish_type", publishType)
				put("metadata", eventMetadata.asJava)
			}}
		(actor, context, objData, eData)
	}
}
