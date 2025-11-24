package org.sunbird.v5.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.`object`.importer.{ImportConfig, ImportManager}
import org.apache.pekko.actor.AbstractActor
import org.apache.pekko.pattern.pipe
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.common.{DateUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.managers.assessment.CopyManager
import org.sunbird.utils.RequestUtil
import utils.Constants
import org.sunbird.v5.managers.AssessmentV5Manager

import java.util
import javax.inject.Inject
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

class QuestionActor @Inject()(implicit oec: OntologyEngineContext) extends AbstractActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  private lazy val importConfig = getImportConfig()
  private lazy val importMgr = new ImportManager(importConfig)
  val defaultVersion = Platform.config.getNumber("v5_default_qumlVersion")

  def onReceive(request: Request): Future[Response] = request.getOperation match {
    case "createQuestion" => AssessmentV5Manager.create(request)
    case "readQuestion" => read(request)
    case "updateQuestion" => update(request)
    case "listQuestions" => listQuestions(request)
    case "readPrivateQuestion" => privateRead(request)
    case "reviewQuestion" => review(request)
    case "rejectQuestion" => reject(request)
    case "publishQuestion" => publish(request)
    case "retireQuestion" => retire(request)
    case "importQuestion" => importQuestion(request)
    case "systemUpdateQuestion" => systemUpdate(request)
    case "copyQuestion" => copy(request)
    case _ => Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, "INVALID_OPERATION", "Operation '" + request.getOperation + "' not supported"))
  }

  def read(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val fields: util.List[String] = request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null")).toList.asJava
    val extPropNameList:util.List[String] = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String], request.getContext.get("schemaName").asInstanceOf[String]).asJava
    request.getRequest.put("fields", extPropNameList)
    DataNode.read(request).map(node => {
      if (StringUtils.equalsIgnoreCase(node.getMetadata.get("visibility").asInstanceOf[String], "Private"))
        throw new ClientException(Constants.ERR_ACCESS_DENIED, s"Question visibility is private, hence access denied")
      ResponseHandler.OK.put("question", AssessmentV5Manager.getQuestionMetadata(node, fields, extPropNameList))
    })
  }

  def update(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    RequestUtil.restrictProperties(request)
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    request.getRequest.put("artifactUrl", null)
    DataNode.read(request).map(node => {
      if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent")) {
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, node.getMetadata.getOrDefault("objectType", "").asInstanceOf[String].replace("Image", "") + " with visibility Parent, can't be updated individually.")
      }
      val version: Double = node.getMetadata.getOrDefault("qumlVersion", 1.0.asInstanceOf[AnyRef]).asInstanceOf[Double]
      if (version < 1.1)
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, s"${node.getObjectType().replace("Image","")} cannot be updated as data is not in QuML 1.1 format.")
      val schemaVersion = node.getMetadata.getOrDefault("schemaVersion", "1.0").asInstanceOf[String]
      request.getContext.put("version", schemaVersion)
      DataNode.update(request).map(node => {
        ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
      })
    }).flatMap(f => f)
  }

  def listQuestions(request: Request): Future[Response] = {
    RequestUtil.validateListRequest(request)
    val fields: util.List[String] = request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null")).toList.asJava
    request.getRequest.put("fields", fields)
    DataNode.search(request).map(nodeList => {
      val questionList = nodeList.map(node => AssessmentV5Manager.getQuestionMetadata(node, fields, List().asJava)).asJava
      ResponseHandler.OK.put("questions", questionList).put("count", questionList.size)
    })
  }

  def privateRead(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val fields: util.List[String] = request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null")).toList.asJava
    val extPropNameList:util.List[String] = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String], request.getContext.get("schemaName").asInstanceOf[String]).asJava
    request.getRequest.put("fields", extPropNameList)
    if (StringUtils.isBlank(request.getRequest.getOrDefault("channel", "").asInstanceOf[String]))
      throw new ClientException(Constants.ERR_REQUEST_DATA_VALIDATION, "Please Provide Channel!")
    DataNode.read(request).map(node => {
      if (!StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("channel", "").asInstanceOf[String], request.getRequest.getOrDefault("channel", "").asInstanceOf[String]))
        throw new ClientException(Constants.ERR_ACCESS_DENIED, "Channel Id Is Not Matched. Please Provide Valid Channel Id!")
      ResponseHandler.OK.put("question", AssessmentV5Manager.getQuestionMetadata(node, fields, extPropNameList))
    })
  }

  def review(request: Request): Future[Response] = {
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    AssessmentV5Manager.getNodeWithExternalProps(request).map(node => {
      val version: Double = node.getMetadata.getOrDefault("qumlVersion", 1.0.asInstanceOf[AnyRef]).asInstanceOf[Double]
      if (version < 1.1)
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, "Question can't be sent for review as data is not in QuML 1.1 format.")
      if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, s"${node.getObjectType.replace("Image", "")} with visibility Parent, can't be sent for review individually.")
      if (!StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Draft"))
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, s"${node.getObjectType.replace("Image", "")} with status other than Draft can't be sent for review.")
      val messages = AssessmentV5Manager.validateQuestionNodeForReview(request, node)
      if (messages.nonEmpty)
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, s"Mandatory Fields ${messages.asJava} Missing for ${node.getIdentifier.replace(".img", "")}")
      val updateRequest = new Request(request)
      updateRequest.getContext.put("identifier", request.get("identifier"))
      updateRequest.getContext.put("version", node.getMetadata.get("schemaVersion").asInstanceOf[String])
      updateRequest.putAll(Map("versionKey" -> node.getMetadata.get("versionKey"), "prevStatus" -> node.getMetadata.get("status").asInstanceOf[String], "status" -> "Review", "lastStatusChangedOn" -> DateUtils.formatCurrentDate).asJava)
      DataNode.update(updateRequest).map(node => {
        ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
      })
    }).flatMap(f => f)
  }

  def reject(request: Request): Future[Response] = {
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    AssessmentV5Manager.getValidateNodeForReject(request, Constants.ERR_OBJECT_VALIDATION).flatMap(node => {
      val updateRequest = new Request(request)
      val date = DateUtils.formatCurrentDate
      updateRequest.getContext.put("identifier", request.getContext.get("identifier"))
      updateRequest.getContext.put("version", node.getMetadata.getOrDefault("schemaVersion", "1.0").asInstanceOf[String])
      if (request.getRequest.containsKey("rejectComment"))
        updateRequest.put("rejectComment", request.get("rejectComment").asInstanceOf[String])
      updateRequest.putAll(Map("versionKey" -> node.getMetadata.get("versionKey"), "status" -> "Draft", "prevStatus" -> node.getMetadata.get("status"), "lastStatusChangedOn" -> date, "lastUpdatedOn" -> date).asJava)
      DataNode.update(updateRequest).map(node => {
        ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
      })
    })
  }

  def publish(request: Request): Future[Response] = {
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    val requestId = request.getContext().getOrDefault("requestId","").asInstanceOf[String]
    val featureName = request.getContext().getOrDefault("featureName","").asInstanceOf[String]
    AssessmentV5Manager.getNodeWithExternalProps(request).map(node => {
      val version: Double = node.getMetadata.getOrDefault("qumlVersion", 1.0.asInstanceOf[AnyRef]).asInstanceOf[Double]
      if (version < 1.1)
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, "Question can't be sent for publish as data is not in QuML 1.1 format.")
      if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, s"${node.getObjectType.replace("Image", "")} with visibility Parent, can't be sent for publish individually.")
      if (StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Processing"))
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, s"${node.getObjectType.replace("Image", "")} having Processing status can't be sent for publish.")
      val messages = AssessmentV5Manager.validateQuestionNodeForReview(request, node)
      if (messages.nonEmpty)
        throw new ClientException(Constants.ERR_OBJECT_VALIDATION, s"Mandatory Fields ${messages.asJava} Missing for ${node.getIdentifier.replace(".img", "")}")
      val lastPublishedBy: String = request.getRequest.getOrDefault("lastPublishedBy", "").asInstanceOf[String]
      if (StringUtils.isNotBlank(lastPublishedBy))
        node.getMetadata.put("lastPublishedBy", lastPublishedBy)
      AssessmentV5Manager.pushInstructionEvent(node.getIdentifier, node, requestId, featureName)
      ResponseHandler.OK.putAll(Map[String, AnyRef]("identifier" -> node.getIdentifier.replace(".img", ""), "message" -> "Question is successfully sent for Publish").asJava)
    })
  }

  def retire(request: Request): Future[Response] = {
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    AssessmentV5Manager.getValidatedNodeForRetire(request, Constants.ERR_OBJECT_VALIDATION).flatMap(node => {
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
    val identifiers = new util.ArrayList[String]() {
      {
        add(identifier)
        if (!identifier.endsWith(".img"))
          add(identifier.concat(".img"))
      }
    }
    readReq.put("identifiers", identifiers)
    DataNode.list(readReq).flatMap(response => {
      DataNode.systemUpdate(request, response, "", None)
    }).map(node => ResponseHandler.OK.put("identifier", identifier).put("status", "success"))
  }

  def copy(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    CopyManager.copy(request)
  }

  override def createReceive(): AbstractActor.Receive =
    receiveBuilder()
      .`match`(classOf[Request], (req: Request) => {
        onReceive(req).pipeTo(sender())
      })
      .build()
}
