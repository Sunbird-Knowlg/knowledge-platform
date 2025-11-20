package org.sunbird.v5.managers

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.{DefinitionNode, ObjectCategoryDefinition}
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.managers.hierarchy.HierarchyManager
import org.sunbird.telemetry.util.LogTelemetryEventUtil
import org.sunbird.utils.RequestUtil
import utils.Constants

import java.util
import java.util.UUID
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration

object AssessmentV5Manager {

  val defaultVersion = Platform.config.getNumber("v5_default_qumlVersion")
  val supportedVersions: java.util.List[Number] = Platform.config.getNumberList("v5_supported_qumlVersions")
  val skipValidation: Boolean = Platform.getBoolean("assessment.skip.validation", false)
  val validStatus = List("Draft", "Review")

  def validateAndGetVersion(ver: AnyRef): AnyRef = {
    if (supportedVersions.contains(ver)) ver else throw new ClientException(Constants.ERR_REQUEST_DATA_VALIDATION, s"Platform doesn't support quml version ${ver} | Currently Supported quml version are: ${supportedVersions}")
  }

  def validateVisibilityForCreate(request: Request, errCode: String): Unit = {
    val visibility: String = request.getRequest.getOrDefault("visibility", "").asInstanceOf[String]
    if (StringUtils.isNotBlank(visibility) && StringUtils.equalsIgnoreCase(visibility, "Parent"))
      throw new ClientException(errCode, "Visibility Cannot Be Parent!")
  }

  def create(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    validateVisibilityForCreate(request, Constants.ERR_REQUEST_DATA_VALIDATION)
    val qumlVersion = request.getRequest.getOrDefault("qumlVersion", 0.0.asInstanceOf[AnyRef])
    val version = if (qumlVersion != 0.0) validateAndGetVersion(qumlVersion) else defaultVersion
    request.put("qumlVersion", version)
    request.getContext.put("version", version.toString)
    RequestUtil.restrictProperties(request)
    request.put("schemaVersion", version.toString)
    DataNode.create(request).map(node => {
      val response = ResponseHandler.OK
      response.putAll(Map("identifier" -> node.getIdentifier, "versionKey" -> node.getMetadata.get("versionKey")).asJava)
      response
    })
  }

  def convertOneOfProps(node: Node, metadata: util.Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): util.Map[String, AnyRef] = {
    val objectCategoryDefinition: ObjectCategoryDefinition = DefinitionNode.getObjectCategoryDefinition(node.getMetadata.getOrDefault("primaryCategory", "").asInstanceOf[String], node.getObjectType.toLowerCase().replace("image", ""), node.getMetadata.getOrDefault("channel", "all").asInstanceOf[String])
    val oneOfProps = DefinitionNode.fetchOneOfProps(node.getGraphId, node.getMetadata.getOrDefault("schemaVersion", 1.0.asInstanceOf[AnyRef]).toString, node.getObjectType.toLowerCase().replace("image", ""), objectCategoryDefinition)
    oneOfProps.foreach(key => {
      if (metadata.containsKey(key)) {
        val data: AnyRef = try {
          JsonUtils.deserialize(metadata.getOrDefault(key, "").asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
        } catch {
          			case _: Throwable => metadata.getOrDefault(key, "")
        }
        metadata.put(key, data)
      }
    })
    metadata
  }

  def getQuestionMetadata(node: Node, fields: util.List[String], extFields: util.List[String])(implicit oec: OntologyEngineContext, ec: ExecutionContext): util.Map[String, AnyRef] = {
    val version: AnyRef = node.getMetadata.getOrDefault("schemaVersion", 1.0.asInstanceOf[AnyRef])
    val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, List().asJava, node.getObjectType.toLowerCase.replace("Image", ""), version.toString)
    val updatedMeta = if (version.toString == "1.0") getTransformedQuestionMetadata(metadata) else convertOneOfProps(node, metadata)
    if (CollectionUtils.isNotEmpty(fields))
      updatedMeta.keySet.retainAll(fields)
    else updatedMeta.keySet().removeAll(extFields)
    updatedMeta.put("identifier", node.getIdentifier.replace(".img", ""))
    updatedMeta
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

  def getValidatedNodeForRetire(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    DataNode.read(request).map(node => {
      if (StringUtils.equalsIgnoreCase("Retired", node.getMetadata.get("status").asInstanceOf[String]))
        throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} with identifier : ${node.getIdentifier} is already Retired.")
      node
    })
  }

  def getNodeWithExternalProps(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    val extPropNameList: util.List[String] = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String], request.getContext.get("schemaName").asInstanceOf[String]).asJava
    val readReq = new Request(request)
    readReq.put("identifier", request.get("identifier").toString)
    readReq.put("mode", "edit")
    readReq.put("fields", extPropNameList)
    DataNode.read(readReq).map(node => node)
  }

  def validateQuestionNodeForReview(request: Request, node: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): List[String] = {
    val messages = ListBuffer[String]()
    //TODO: Refactor this method to use schema level configuration to get all property which need to be validated during review and publish
    //TODO: Extract type of each property from schema and validate. Also resolve oneOf props
    val metadata = node.getMetadata
    if (StringUtils.isBlank(metadata.getOrDefault("body", "").asInstanceOf[String]))
      messages += s"""body"""
    /*if (StringUtils.isBlank(metadata.getOrDefault("answer", "").asInstanceOf[String]))
      messages += s"""answer"""*/
    if (null != metadata.get("interactionTypes")) {
      if (StringUtils.isBlank(metadata.getOrDefault("responseDeclaration", "").asInstanceOf[String])) messages += s"""responseDeclaration"""
      if (StringUtils.isBlank(metadata.getOrDefault("interactions", "").asInstanceOf[String])) messages += s"""interactions"""
      if (StringUtils.isBlank(metadata.getOrDefault("outcomeDeclaration", "").asInstanceOf[String])) messages += s"""outcomeDeclaration"""
    } else {
      if (StringUtils.isBlank(metadata.getOrDefault("answer", "").asInstanceOf[String]))
        messages += s"""answer"""
    }
    messages.toList
  }

  def validateHierarchy(request: Request, children: util.List[util.Map[String, AnyRef]], rootUserId: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Unit = {
    children.asScala.foreach(content => {
      val version: Double = content.getOrDefault("qumlVersion", 1.0.asInstanceOf[AnyRef]).asInstanceOf[Double]
      if (version < 1.1)
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, s"Children Object with identifier ${content.get("identifier").toString} doesn't have data in QuML 1.1 format.")
      if ((StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Default")
        && !StringUtils.equals(rootUserId, content.getOrDefault("createdBy", "").asInstanceOf[String]))
        && !StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, "Object with identifier: " + content.get("identifier") + " is not Live. Please Publish it.")
      if ((StringUtils.equalsAnyIgnoreCase("application/vnd.sunbird.question", content.get("mimeType").toString) &&
        StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Parent")
        && !StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))
        || (StringUtils.equalsAnyIgnoreCase("application/vnd.sunbird.question", content.get("mimeType").toString)
        && StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Default")
        && StringUtils.equals(rootUserId, content.getOrDefault("createdBy", "").asInstanceOf[String])
        && !StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))) {
        val readReq = new Request(request)
        readReq.getRequest.put("identifier", content.get("identifier").toString)
        readReq.getContext.put("identifier", content.get("identifier").toString)
        readReq.getContext.put("objectType", content.get("objectType").toString)
        readReq.getContext.put("schemaName", content.get("objectType").toString.toLowerCase().replace("image", ""))
        readReq.getContext.put("version", content.getOrDefault("schemaVersion", "1.1").toString)
        readReq.put("mode", "edit")
        val extPropNameList: util.List[String] = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], content.getOrDefault("schemaVersion", "1.1").asInstanceOf[String], content.getOrDefault("objectType", "Question").asInstanceOf[String].toLowerCase.replace("image", "")).asJava
        readReq.put("fields", extPropNameList)
        val messages: List[String] = Await.result(DataNode.read(readReq).map(node => {
          val messages = validateQuestionNodeForReview(request, node)
          messages
        }), Duration.apply("30 seconds"))
        if (messages.nonEmpty)
          throw new ClientException(Constants.ERR_OBJECT_VALIDATION, s"Mandatory Fields ${messages.asJava} Missing for ${content.get("identifier").toString}")
      }
      val nestedChildren = content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      if (!nestedChildren.isEmpty)
        validateHierarchy(request, nestedChildren, rootUserId)
    })
  }

  def updateHierarchy(hierarchy: util.Map[String, AnyRef], status: String, rootUserId: String): (java.util.Map[String, AnyRef], java.util.List[String]) = {
    val keys = List("identifier", "children").asJava
    hierarchy.keySet().retainAll(keys)
    val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.List[java.util.Map[String, AnyRef]]]
    val childrenToUpdate: List[String] = updateChildrenRecursive(children, status, List(), rootUserId)
    (hierarchy, childrenToUpdate.asJava)
  }

  private def updateChildrenRecursive(children: util.List[util.Map[String, AnyRef]], status: String, idList: List[String], rootUserId: String): List[String] = {
    children.asScala.toList.flatMap(content => {
      val objectType = content.getOrDefault("objectType", "").asInstanceOf[String]
      val updatedIdList: List[String] =
        if (StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Parent") || (StringUtils.equalsIgnoreCase(objectType, "Question") && StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Default") && validStatus.contains(content.getOrDefault("status", "").asInstanceOf[String]) && StringUtils.equals(rootUserId, content.getOrDefault("createdBy", "").asInstanceOf[String]))) {
          content.put("lastStatusChangedOn", DateUtils.formatCurrentDate)
          content.put("prevStatus", content.getOrDefault("status", "Draft"))
          content.put("status", status)
          content.put("lastUpdatedOn", DateUtils.formatCurrentDate)
          if (StringUtils.equalsAnyIgnoreCase(objectType, "Question")) content.get("identifier").asInstanceOf[String] :: idList else idList
        } else idList
      val list = updateChildrenRecursive(content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]], status, updatedIdList, rootUserId)
      list ++ updatedIdList
    })
  }

  def getQuestionSetHierarchy(request: Request, rootNode: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[String] = {
    request.put("rootId", request.get("identifier").asInstanceOf[String])
    HierarchyManager.getUnPublishedHierarchy(request).map(resp => {
      if (!ResponseHandler.checkError(resp) && resp.getResponseCode.code() == 200) {
        val hierarchy = resp.getResult.get("questionSet").asInstanceOf[util.Map[String, AnyRef]]
        JsonUtils.serialize(hierarchy)
      } else throw new ServerException("ERR_QUESTION_SET_HIERARCHY", "No hierarchy is present in cassandra for identifier:" + rootNode.getIdentifier)
    })
  }

  def getValidatedNodeForPublish(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
    request.put("mode", "edit")
    DataNode.read(request).map(node => {
      if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
        throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} with visibility Parent, can't be sent for publish individually.")
      if (StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Processing"))
        throw new ClientException(errCode, s"${node.getObjectType.replace("Image", "")} having Processing status can't be sent for publish.")
      val version: Double = node.getMetadata.getOrDefault("qumlVersion", 1.0.asInstanceOf[AnyRef]).asInstanceOf[Double]
      if (version < 1.1)
        throw new ClientException(errCode, s"${node.getObjectType().replace("Image", "")} can't be sent for publish as data is not in QuML 1.1 format.")
      node
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
    val eventMetadata = Map("identifier" -> identifier, "mimeType" -> metadata.getOrDefault("mimeType", ""), "objectType" -> node.getObjectType.replace("Image", ""), "pkgVersion" -> metadata.getOrDefault("pkgVersion", 0.asInstanceOf[AnyRef]), "lastPublishedBy" -> metadata.getOrDefault("lastPublishedBy", ""), "qumlVersion" -> node.getMetadata.getOrDefault("qumlVersion", 1.1.asInstanceOf[AnyRef]), "schemaVersion" -> node.getMetadata.getOrDefault("schemaVersion", "1.1").asInstanceOf[String])
    val actor = Map("id" -> s"${node.getObjectType.toLowerCase().replace("image", "")}-publish", "type" -> "System".asInstanceOf[AnyRef])
    val context = Map("channel" -> metadata.getOrDefault("channel", ""), "pdata" -> Map("id" -> "org.sunbird.platform", "ver" -> "1.0").asJava, "env" -> Platform.getString("cloud_storage.env", "dev"))
    val objData = Map("id" -> identifier, "ver" -> metadata.getOrDefault("versionKey", ""))
    val eData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef] {
      {
        put("action", "publish")
        put("requestId", requestId)
        put("featureName", featureName)
        put("publish_type", publishType)
        put("metadata", eventMetadata.asJava)
      }
    }
    (actor, context, objData, eData)
  }

  def getQuestionSetMetadata(node: Node, fields: util.List[String])(implicit oec: OntologyEngineContext, ec: ExecutionContext): util.Map[String, AnyRef] = {
    val version: AnyRef = node.getMetadata.getOrDefault("schemaVersion", 1.0.asInstanceOf[AnyRef])
    val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, List().asJava, node.getObjectType.toLowerCase.replace("Image", ""), version.toString)
    val updatedMeta = if (version.toString == "1.0") AssessmentV5Manager.getTransformedQuestionSetMetadata(metadata) else AssessmentV5Manager.convertOneOfProps(node, metadata)
    if (CollectionUtils.isNotEmpty(fields))
      updatedMeta.keySet.retainAll(fields)
    else updatedMeta.remove("outcomeDeclaration")
    updatedMeta.put("identifier", node.getIdentifier.replace(".img", ""))
    updatedMeta
  }

  def processMaxScore(data: util.Map[String, AnyRef]): Unit = {
    if (data.containsKey("maxScore")) {
      val maxScore = data.remove("maxScore")
      val outcomeDeclaration = Map("maxScore" -> Map("cardinality" -> "single", "type" -> "integer", "defaultValue" -> maxScore).asJava).asJava
      data.put("outcomeDeclaration", outcomeDeclaration)
    }
  }

  def processInstructions(data: util.Map[String, AnyRef]): Unit = {
    if (data.containsKey("instructions")) {
      val instructions = data.getOrDefault("instructions", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      if (!instructions.isEmpty && (instructions.keySet().size() == 1 && instructions.keySet().contains("default"))) {
        data.put("instructions", instructions.get("default").asInstanceOf[String])
      }
    }
  }

  def processBloomsLevel(data: util.Map[String, AnyRef]): Unit = {
    if (data.containsKey("bloomsLevel")) {
      val bLevel = data.remove("bloomsLevel")
      data.put("complexityLevel", List(bLevel.toString).asJava)
    }
  }

  def processBooleanProps(data: util.Map[String, AnyRef]): Unit = {
    val booleanProps = List("showSolutions", "showFeedback", "showHints", "showTimer")
    booleanProps.foreach(prop => {
      if (data.containsKey(prop)) {
        val propVal: String = data.get(prop).asInstanceOf[String]
        data.put(prop, getBooleanValue(propVal).asInstanceOf[AnyRef])
      }
    })
  }

  def processTimeLimits(data: util.Map[String, AnyRef]): Unit = {
    if (data.containsKey("timeLimits")) {
		val timeLimits:util.Map[String, AnyRef] = data.get("timeLimits") match {
			case m: util.Map[_, _] => m.asInstanceOf[util.Map[String, AnyRef]]
			case s: String => JsonUtils.deserialize(s, classOf[java.util.Map[String, AnyRef]])
			 case _ => Map().asJava.asInstanceOf[util.Map[String, AnyRef]]
		}
      val maxTime: Integer = timeLimits.getOrDefault("maxTime", "0").asInstanceOf[String].toInt
      val updatedData: util.Map[String, AnyRef] = Map("questionSet" -> Map("max" -> maxTime, "min" -> 0.asInstanceOf[AnyRef]).asJava).asJava.asInstanceOf[util.Map[String, AnyRef]]
      data.put("timeLimits", updatedData)
    }
  }

  def getAnswer(data: util.Map[String, AnyRef]): String = {
    val interactions: util.Map[String, AnyRef] = data.getOrDefault("interactions", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]]
    if (!StringUtils.equalsIgnoreCase(data.getOrDefault("primaryCategory", "").asInstanceOf[String], "Subjective Question") && !interactions.isEmpty) {
      val responseDeclaration: util.Map[String, AnyRef] = data.getOrDefault("responseDeclaration", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]]
      val responseData = responseDeclaration.get("response1").asInstanceOf[util.Map[String, AnyRef]]
      val intractionsResp1: util.Map[String, AnyRef] = interactions.getOrDefault("response1", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      val options = intractionsResp1.getOrDefault("options", List().asJava).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      val answerData = responseData.get("cardinality").asInstanceOf[String] match {
        case "single" => {
          val correctResp = responseData.getOrDefault("correctResponse", Map().asJava).asInstanceOf[util.Map[String, AnyRef]].get("value").asInstanceOf[Integer]
          val label = options.asScala.filter(op => op.get("value").asInstanceOf[Integer] == correctResp).head.get("label").asInstanceOf[String]
          val answer = """<div class="anwser-container"><div class="anwser-body">answer_html</div></div>""".replace("answer_html", label)
          answer
        }
        case "multiple" => {
          val correctResp = responseData.getOrDefault("correctResponse", Map().asJava).asInstanceOf[util.Map[String, AnyRef]].get("value").asInstanceOf[util.List[Integer]]
          val singleAns = """<div class="anwser-body">answer_html</div>"""
          val answerList: List[String] = options.asScala.filter(op => correctResp.contains(op.get("value").asInstanceOf[Integer])).map(op => singleAns.replace("answer_html", op.get("label").asInstanceOf[String])).toList
          val answer = """<div class="anwser-container">answer_div</div>""".replace("answer_div", answerList.mkString(""))
          answer
        }
      }
      answerData
    } else data.getOrDefault("answer", "").asInstanceOf[String]
  }

  def processInteractions(data: util.Map[String, AnyRef]): Unit = {
    val interactions: util.Map[String, AnyRef] = data.getOrDefault("interactions", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]]
    if (!interactions.isEmpty) {
      val validation = interactions.getOrDefault("validation", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      val resp1: util.Map[String, AnyRef] = interactions.getOrDefault("response1", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      val resValData = interactions.getOrDefault("response1", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]].getOrDefault("validation", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      if (!resValData.isEmpty) resValData.putAll(validation) else resp1.put("validation", validation)
      interactions.remove("validation")
      interactions.put("response1", resp1)
      data.put("interactions", interactions)
    }
  }

  def processHints(data: util.Map[String, AnyRef]): Unit = {
    val hints = data.getOrDefault("hints", List[String]().asJava).asInstanceOf[util.List[String]]
    if (!hints.isEmpty) {
      val updatedHints: util.Map[String, AnyRef] = hints.asScala.map(hint => UUID.randomUUID().toString -> hint.asInstanceOf[AnyRef]).toMap.asJava
      data.put("hints", updatedHints)
    }
  }

  def processResponseDeclaration(data: util.Map[String, AnyRef]): Unit = {
    val outcomeDeclaration = new util.HashMap[String, AnyRef]()
    // Remove responseDeclaration metadata for Subjective Question
    if (StringUtils.equalsIgnoreCase("Subjective Question", data.getOrDefault("primaryCategory", "").toString)) {
      data.remove("responseDeclaration")
      data.remove("interactions")
      if(data.containsKey("maxScore") && null != data.get("maxScore")) {
        data.put("outcomeDeclaration", Map[String, AnyRef]("cardinality" -> "single", "type" -> "integer", "defaultValue" -> data.get("maxScore")).asJava)
      }
    } else {
      //transform responseDeclaration and populate outcomeDeclaration
      val responseDeclaration: util.Map[String, AnyRef] = data.getOrDefault("responseDeclaration", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]]
      if (!responseDeclaration.isEmpty) {
        for (key <- responseDeclaration.keySet().asScala) {
          val responseData = responseDeclaration.get(key).asInstanceOf[util.Map[String, AnyRef]]
          // remove maxScore and put it under outcomeDeclaration
          val maxScore = Map[String, AnyRef]("cardinality" -> responseData.getOrDefault("cardinality", "").asInstanceOf[String], "type" -> responseData.getOrDefault("type", "").asInstanceOf[String], "defaultValue" -> responseData.get("maxScore"))
          responseData.remove("maxScore")
          outcomeDeclaration.put("maxScore", maxScore.asJava)
          //remove outcome from correctResponse
          responseData.getOrDefault("correctResponse", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]].remove("outcomes")
          // type cast value. data type mismatch seen in quml 1.0 data where type and data was integer but integer data was populated as string
          try {
            if (StringUtils.equalsIgnoreCase("integer", responseData.getOrDefault("type", "").asInstanceOf[String])
              && StringUtils.equalsIgnoreCase("single", responseData.getOrDefault("cardinality", "").asInstanceOf[String])) {
              val correctResp: util.Map[String, AnyRef] = responseData.getOrDefault("correctResponse", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]]
              val correctKey: String = correctResp.getOrDefault("value", "0").asInstanceOf[String]
              correctResp.put("value", correctKey.toInt.asInstanceOf[AnyRef])
            }
          } catch {
            case e: NumberFormatException => e.printStackTrace()
          }
          //update mapping
          val mappingData = responseData.asScala.getOrElse("mapping", List[util.Map[String, AnyRef]]().asJava).asInstanceOf[util.List[util.Map[String, AnyRef]]]
          if (!mappingData.isEmpty) {
            val updatedMapping = mappingData.asScala.toList.map(mapData => {
              Map[String, AnyRef]("value" -> mapData.get("response"), "score" -> mapData.getOrDefault("outcomes", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]].get("score")).asJava
            }).asJava
            responseData.put("mapping", updatedMapping)
          }
        }
        data.put("responseDeclaration", responseDeclaration)
        data.put("outcomeDeclaration", outcomeDeclaration)
      }
    }
  }

  def processSolutions(data: util.Map[String, AnyRef]): Unit = {
    val solutions = data.getOrDefault("solutions", List[util.Map[String, AnyRef]](Map[String, AnyRef]().asJava).asJava).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (!solutions.isEmpty) {
      val updatedSolutions: util.Map[String, AnyRef] = solutions.asScala.toList.map(solution => {
        Map[String, AnyRef](solution.asScala.getOrElse("id", "").asInstanceOf[String] -> getSolutionString(solution, data.asScala.getOrElse("media", List[util.Map[String, AnyRef]](Map[String, AnyRef]().asJava).asJava).asInstanceOf[util.List[util.Map[String, AnyRef]]]))
      }).flatten.toMap.asJava
      data.put("solutions", updatedSolutions)
    }
  }

  def getBooleanValue(str: String): Boolean = {
    str match {
      case "Yes" => true
      case _ => false
    }
  }

  def getSolutionString(data: util.Map[String, AnyRef], media: util.List[util.Map[String, AnyRef]]): String = {
    if (!data.isEmpty) {
      data.getOrDefault("type", "").asInstanceOf[String] match {
        case "html" => data.getOrDefault("value", "").asInstanceOf[String]
        case "video" => {
          val value = data.getOrDefault("value", "").asInstanceOf[String]
          val mediaData: util.Map[String, AnyRef] = media.asScala.find(map => StringUtils.equalsIgnoreCase(value, map.asScala.getOrElse("id", "").asInstanceOf[String])).getOrElse(Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]]
          val src = mediaData.getOrDefault("src", "").asInstanceOf[String]
          val thumbnail = mediaData.getOrDefault("thumbnail", "").asInstanceOf[String]
          val solutionStr = """<video data-asset-variable="media_identifier" width="400" controls="" poster="thumbnail_url"><source type="video/mp4" src="media_source_url"><source type="video/webm" src="media_source_url"></video>""".replace("media_identifier", value).replace("thumbnail_url", thumbnail).replace("media_source_url", src)
          solutionStr
        }
      }
    } else ""
  }

  def getTransformedQuestionMetadata(data: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    try {
      if (!data.isEmpty) {
        processResponseDeclaration(data)
        processInteractions(data)
        processSolutions(data)
        processInstructions(data)
        processHints(data)
        processBloomsLevel(data)
        processBooleanProps(data)
        val ans = getAnswer(data)
        if (StringUtils.isNotBlank(ans))
          data.put("answer", ans)
        data.put("compatibilityLevel", 5.asInstanceOf[AnyRef])
        data
      } else data
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new ServerException("ERR_QUML_DATA_TRANSFORM", s"Error Occurred While Converting Data To Quml 1.1 Format for ${data.get("identifier")}")
      }
    }
  }

  def getTransformedHierarchy(data: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    val updatedMeta = getTransformedQuestionSetMetadata(data)
    updatedMeta.remove("outcomeDeclaration")
    val children = updatedMeta.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.List[java.util.Map[String, AnyRef]]]
    if (!children.isEmpty)
      tranformChildren(children)
    updatedMeta
  }

  def tranformChildren(children: util.List[util.Map[String, AnyRef]]): Unit = {
    if (!children.isEmpty) {
      children.asScala.foreach(ch => {
        if (ch.containsKey("version")) ch.remove("version")
        processBloomsLevel(ch)
        processBooleanProps(ch)
        if(StringUtils.equalsIgnoreCase("application/vnd.sunbird.question", ch.getOrDefault("mimeType", "").asInstanceOf[String]))
          ch.put("compatibilityLevel", 5.asInstanceOf[AnyRef])
        if (StringUtils.equalsIgnoreCase("application/vnd.sunbird.questionset", ch.getOrDefault("mimeType", "").asInstanceOf[String])) {
          processTimeLimits(ch)
          processInstructions(ch)
          ch.put("compatibilityLevel", 6.asInstanceOf[AnyRef])
          val nestedChildren = ch.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.List[java.util.Map[String, AnyRef]]]
          tranformChildren(nestedChildren)
        }
      })
    }
  }

  def getTransformedQuestionSetMetadata(data: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    try {
      if (!data.isEmpty) {
        processMaxScore(data)
        data.remove("version")
        processInstructions(data)
        processBloomsLevel(data)
        processBooleanProps(data)
        processTimeLimits(data)
        data.put("compatibilityLevel", 6.asInstanceOf[AnyRef])
        data
      } else data
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new ServerException("ERR_QUML_DATA_TRANSFORM", s"Error Occurred While Converting Data To Quml 1.1 Format for ${data.get("identifier")}")
      }
    }
  }

  def readComment(request: Request, resName: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val fields: util.List[String] = request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null")).toList.asJava
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
