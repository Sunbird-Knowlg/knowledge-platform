package org.sunbird.managers.assessment

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.{DefinitionNode, ObjectCategoryDefinition}
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.graph.utils.NodeUtil.{convertJsonProperties, handleKeyNames}
import org.sunbird.telemetry.util.LogTelemetryEventUtil
import org.sunbird.utils.RequestUtil
import org.sunbird.managers.hierarchy.HierarchyManager

import java.util
import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object AssessmentManager {

	val skipValidation: Boolean = Platform.getBoolean("assessment.skip.validation", false)
	val validStatus = List("Draft", "Review")

	def create(request: Request, errCode: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val visibility: String = request.getRequest.getOrDefault("visibility", "").asInstanceOf[String]
		if (StringUtils.isNotBlank(visibility) && StringUtils.equalsIgnoreCase(visibility, "Parent"))
			throw new ClientException(errCode, "Visibility cannot be Parent!")
		RequestUtil.restrictProperties(request)
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
			if(!StringUtils.equalsIgnoreCase(metadata.get("visibility").asInstanceOf[String],"Private")) {
				ResponseHandler.OK.put(resName, metadata)
			}
			else {
				throw new ClientException("ERR_ACCESS_DENIED", s"$resName visibility is private, hence access denied")
			}
		})
	}

	def privateRead(request: Request, resName: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
		request.getRequest.put("fields", fields)
		if (StringUtils.isBlank(request.getRequest.getOrDefault("channel", "").asInstanceOf[String])) throw new ClientException("ERR_INVALID_CHANNEL", "Please Provide Channel!")
		DataNode.read(request).map(node => {
			val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, node.getObjectType.toLowerCase.replace("Image", ""), request.getContext.get("version").asInstanceOf[String])
			metadata.put("identifier", node.getIdentifier.replace(".img", ""))
				if (StringUtils.equalsIgnoreCase(metadata.getOrDefault("channel", "").asInstanceOf[String],request.getRequest.getOrDefault("channel", "").asInstanceOf[String])) {
					ResponseHandler.OK.put(resName, metadata)
				}
				else {
					throw new ClientException("ERR_ACCESS_DENIED", "Channel id is not matched")
				}
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

	def getValidatedNodeForUpdateComment(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		val identifier = request.getContext.get("identifier")
		val commentValue = request.getRequest.get("reviewComment").toString
		if (commentValue == null || commentValue.trim.isEmpty){
			throw new ClientException(errCode, "Comment key is missing or value is empty in the request body.")
		} else {
			val readReq = request
			readReq.put("identifier", identifier)
			readReq.put("mode", "edit")
			DataNode.read(request).map(node => {
				if (!StringUtils.equalsIgnoreCase("QuestionSet", node.getObjectType))
					throw new ClientException(errCode, s"Node with Identifier ${node.getIdentifier} is not a Question Set.")
				if (!StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Review"))
					throw new ClientException(errCode, s"Node with Identifier ${node.getIdentifier.replace(".img", "")} does not have a status Review.")
				node
			})
		}
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

	def getValidatedQuestionNodeForReview(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		val extPropNameList:util.List[String] = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String], request.getContext.get("schemaName").asInstanceOf[String]).asJava
		val readReq = new Request(request)
		readReq.put("identifier", request.get("identifier").toString)
		readReq.put("mode", "edit")
		readReq.put("fields", extPropNameList)
		DataNode.read(readReq).map(node => {
			if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} with visibility Parent, can't be sent for review individually.")
			if (!StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Draft"))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} with status other than Draft can't be sent for review.")
			val messages = validateQuestionNodeForReview(request, node)
			if(messages.nonEmpty)
				throw new ClientException("ERR_MANDATORY_FIELD_VALIDATION", s"Mandatory Fields ${messages.asJava} Missing for ${node.getIdentifier.replace(".img", "")}")
			else node
		})
	}

	def validateQuestionNodeForReview(request: Request, node: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): List[String] = {
		val messages = ListBuffer[String]()
		val metadataMap = node.getMetadata
		val extPropNameList:util.List[String] = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String], node.getObjectType.toLowerCase.replace("image", "")).asJava
		val objectCategoryDefinition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition(node.getMetadata.getOrDefault("primaryCategory", "").asInstanceOf[String], node.getObjectType.toLowerCase().replace("image", ""), node.getMetadata.getOrDefault("channel","all").asInstanceOf[String])
		val jsonProps = DefinitionNode.fetchJsonProps(node.getGraphId, request.getContext().get("version").toString, node.getObjectType.toLowerCase().replace("image", ""), objectCategoryDefinition)
		val metadata:util.Map[String, AnyRef] = metadataMap.entrySet().asScala.filter(entry => null != entry.getValue).map((entry: util.Map.Entry[String, AnyRef]) => handleKeyNames(entry, extPropNameList) ->  convertJsonProperties(entry, jsonProps)).toMap.asJava
		if (metadata.getOrElse("body", "").asInstanceOf[String].isEmpty) messages += s"""body"""
		if (metadata.getOrElse("editorState", new util.HashMap()).asInstanceOf[util.Map[String, AnyRef]].isEmpty) messages += s"""editorState"""
		if (null != metadata.get("interactionTypes")) {
			if (metadata.getOrElse("responseDeclaration", new util.HashMap()).asInstanceOf[util.Map[String, AnyRef]].isEmpty) messages += s"""responseDeclaration"""
			if (metadata.getOrElse("interactions", new util.HashMap()).asInstanceOf[util.Map[String, AnyRef]].isEmpty) messages += s"""interactions"""
		} else {
			if (metadata.getOrElse("answer", "").asInstanceOf[String].isEmpty) messages += s"""answer"""
		}
		messages.toList
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

	def getValidatedQuestionNodeForPublish(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
		val extPropNameList:util.List[String] = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String], request.getContext.get("schemaName").asInstanceOf[String]).asJava
		request.put("mode", "edit")
		request.put("fields", extPropNameList)
		DataNode.read(request).map(node => {
			if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} with visibility Parent, can't be sent for publish individually.")
			if (StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Processing"))
				throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} having Processing status can't be sent for publish.")
			val messages = validateQuestionNodeForReview(request, node)
			if(messages.nonEmpty)
				throw new ClientException("ERR_MANDATORY_FIELD_VALIDATION", s"Mandatory Fields ${messages.asJava} Missing for ${node.getIdentifier.replace(".img", "")}")
			else node
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

	def validateQuestionSetHierarchy(request: Request, hierarchyString: String, rootUserId: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Unit = {
		if (!skipValidation) {
			val hierarchy = if (!hierarchyString.asInstanceOf[String].isEmpty) {
				JsonUtils.deserialize(hierarchyString.asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
			} else
				new java.util.HashMap[String, AnyRef]()
			val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.List[java.util.Map[String, AnyRef]]]
			validateChildrenRecursive(request, children, rootUserId)
		}
	}

	def getQuestionSetHierarchy(request: Request, rootNode: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Any] = {
		request.put("rootId", request.get("identifier").asInstanceOf[String])
		HierarchyManager.getUnPublishedHierarchy(request).map(resp => {
			if (!ResponseHandler.checkError(resp) && resp.getResponseCode.code() == 200) {
				val hierarchy = resp.getResult.get("questionSet").asInstanceOf[util.Map[String, AnyRef]]
				JsonUtils.serialize(hierarchy)
			} else throw new ServerException("ERR_QUESTION_SET_HIERARCHY", "No hierarchy is present in cassandra for identifier:" + rootNode.getIdentifier)
		})
	}

	private def validateChildrenRecursive(request: Request, children: util.List[util.Map[String, AnyRef]], rootUserId: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Unit = {
		children.toList.foreach(content => {
			if ((StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Default")
			  && !StringUtils.equals(rootUserId, content.getOrDefault("createdBy", "").asInstanceOf[String]))
			  && !StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))
				throw new ClientException("ERR_QUESTION_SET", "Object with identifier: " + content.get("identifier") + " is not Live. Please Publish it.")
			if((StringUtils.equalsAnyIgnoreCase("application/vnd.sunbird.question", content.get("mimeType").toString) &&
			  StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Parent")
			  && !StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))
			  || (StringUtils.equalsAnyIgnoreCase("application/vnd.sunbird.question", content.get("mimeType").toString)
			  && StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Default")
			  && StringUtils.equals(rootUserId, content.getOrDefault("createdBy", "").asInstanceOf[String])
			  && !StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))) {
				val extPropNameList:util.List[String] = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String], content.getOrDefault("objectType", "Question").asInstanceOf[String].toLowerCase.replace("image","")).asJava
				val readReq = new Request(request)
				readReq.getRequest.put("identifier", content.get("identifier").toString)
				readReq.getContext.put("identifier", content.get("identifier").toString)
				readReq.getContext.put("objectType", content.get("objectType").toString)
				readReq.getContext.put("schemaName", content.get("objectType").toString.toLowerCase().replace("image",""))
				readReq.put("mode", "edit")
				readReq.put("fields", extPropNameList)
				val messages:List[String] = Await.result(DataNode.read(readReq).map(node => {
					val messages = validateQuestionNodeForReview(request, node)
					messages
				}), Duration.apply("30 seconds"))
				if(messages.nonEmpty)
					throw new ClientException("ERR_MANDATORY_FIELD_VALIDATION", s"Mandatory Fields ${messages.asJava} Missing for ${content.get("identifier").toString}")
			}
			validateChildrenRecursive(request, content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]], rootUserId)
		})
	}

	def getChildIdsFromRelation(node: Node): (List[String], List[String]) = {
		val outRelations: List[Relation] = if (node.getOutRelations != null) node.getOutRelations.asScala.toList else List[Relation]()
		val visibilityIdMap: Map[String, List[String]] = outRelations
		  .groupBy(_.getEndNodeMetadata.get("visibility").asInstanceOf[String])
		  .view.mapValues(_.map(_.getEndNodeId).toList).toMap
		(visibilityIdMap.getOrDefault("Default", List()), visibilityIdMap.getOrDefault("Parent", List()))
	}

	def updateHierarchy(hierarchyString: String, status: String, rootUserId: String): (java.util.Map[String, AnyRef], java.util.List[String]) = {
		val hierarchy: java.util.Map[String, AnyRef] = if (!hierarchyString.asInstanceOf[String].isEmpty) {
			JsonUtils.deserialize(hierarchyString.asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
		} else
			new java.util.HashMap[String, AnyRef]()
		val keys = List("identifier", "children").asJava
		hierarchy.keySet().retainAll(keys)
		val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.List[java.util.Map[String, AnyRef]]]
		val childrenToUpdate: List[String] = updateChildrenRecursive(children, status, List(), rootUserId)
		(hierarchy, childrenToUpdate.asJava)
	}

	private def updateChildrenRecursive(children: util.List[util.Map[String, AnyRef]], status: String, idList: List[String], rootUserId: String): List[String] = {
		children.toList.flatMap(content => {
			val objectType = content.getOrDefault("objectType", "").asInstanceOf[String]
			val updatedIdList: List[String] =
				if (StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Parent") || (StringUtils.equalsIgnoreCase( objectType, "Question") && StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Default") && validStatus.contains(content.getOrDefault("status", "").asInstanceOf[String]) && StringUtils.equals(rootUserId, content.getOrDefault("createdBy", "").asInstanceOf[String]))) {
					content.put("lastStatusChangedOn", DateUtils.formatCurrentDate)
					content.put("prevStatus", content.getOrDefault("status", "Draft"))
					content.put("status", status)
					content.put("lastUpdatedOn", DateUtils.formatCurrentDate)
					if(StringUtils.equalsAnyIgnoreCase(objectType, "Question")) content.get("identifier").asInstanceOf[String] :: idList else idList
				} else idList
			val list = updateChildrenRecursive(content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]], status, updatedIdList, rootUserId)
			list ++ updatedIdList
		})
	}

	@throws[Exception]
	def pushInstructionEvent(identifier: String, node: Node, requestId: String, featureName: String)(implicit oec: OntologyEngineContext): Unit = {
		val (actor, context, objData, eData) = generateInstructionEventMetadata(identifier.replace(".img", ""), node, requestId, featureName)
		val beJobRequestEvent: String = LogTelemetryEventUtil.logInstructionEvent(actor.asJava, context.asJava, objData.asJava, eData)
		val topic: String = Platform.getString("kafka.topics.instruction", "sunbirddev.learning.job.request")
		if (StringUtils.isBlank(beJobRequestEvent)) throw new ClientException("BE_JOB_REQUEST_EXCEPTION", "Event is not generated properly.")
		oec.kafkaClient.send(beJobRequestEvent, topic)
	}

	def generateInstructionEventMetadata(identifier: String, node: Node, requestId: String, featureName: String): (Map[String, AnyRef], Map[String, AnyRef], Map[String, AnyRef], util.Map[String, AnyRef]) = {
		val metadata: util.Map[String, AnyRef] = node.getMetadata
		val publishType = if (StringUtils.equalsIgnoreCase(metadata.getOrDefault("status", "").asInstanceOf[String], "Unlisted")) "unlisted" else "public"
		val eventMetadata = Map("identifier" -> identifier, "mimeType" -> metadata.getOrDefault("mimeType", ""), "objectType" -> node.getObjectType.replace("Image", ""), "pkgVersion" -> metadata.getOrDefault("pkgVersion", 0.asInstanceOf[AnyRef]), "lastPublishedBy" -> metadata.getOrDefault("lastPublishedBy", ""))
		val actor = Map("id" -> s"${node.getObjectType.toLowerCase().replace("image", "")}-publish", "type" -> "System".asInstanceOf[AnyRef])
		val context = Map("channel" -> metadata.getOrDefault("channel", ""), "pdata" -> Map("id" -> "org.sunbird.platform", "ver" -> "1.0").asJava, "env" -> Platform.getString("cloud_storage.env", "dev"))
		val objData = Map("id" -> identifier, "ver" -> metadata.getOrDefault("versionKey", ""))
		val eData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef] {{
				put("action", "publish")
				put("requestId", requestId)
				put("featureName", featureName)
				put("publish_type", publishType)
				put("metadata", eventMetadata.asJava)
			}}
		(actor, context, objData, eData)
	}

	def readComment(request: Request, resName: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
		request.getRequest.put("fields", fields)
		val res = new java.util.ArrayList[java.util.Map[String, AnyRef]] {}
		DataNode.read(request).map(node => {
			val metadata = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]
			metadata.put("comment", node.getMetadata.getOrDefault("rejectComment", ""))
			res.add(metadata)
			if (!StringUtils.equalsIgnoreCase("QuestionSet", node.getObjectType))
				throw new ClientException("ERR_QUESTION_SET_READ_COMMENT", s"Node with Identifier ${node.getIdentifier} is not a Question Set.")
			if (!StringUtils.equalsIgnoreCase(node.getMetadata.get("visibility").asInstanceOf[String], "Private")) {
				ResponseHandler.OK.put(resName, res)
			}
			else {
				throw new ClientException("ERR_ACCESS_DENIED", s"visibility of ${node.getIdentifier.replace(".img", "")} is private hence access denied")
			}
		})
	}
}
