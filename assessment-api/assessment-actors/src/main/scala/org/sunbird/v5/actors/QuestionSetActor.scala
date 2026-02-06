package org.sunbird.v5.actors

import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.`object`.importer.{ImportConfig, ImportManager}
import org.apache.pekko.actor.AbstractActor
import org.apache.pekko.pattern.pipe
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.common.{DateUtils, JsonUtils, Platform}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.managers.questionset.HierarchyManager.hierarchyPrefix
import org.sunbird.managers.questionset.{CopyManager, HierarchyManager, UpdateHierarchyManager}
import org.sunbird.utils.AssessmentErrorCodes
import org.sunbird.utils.questionset.RequestUtil
import org.sunbird.v5.managers.AssessmentV5Manager

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class QuestionSetActor @Inject()(implicit oec: OntologyEngineContext) extends AbstractActor {

  implicit val ec: ExecutionContext = getContext().dispatcher
  private lazy val importConfig = getImportConfig()
  private lazy val importMgr = new ImportManager(importConfig)
  val defaultVersion = Platform.config.getNumber("v5_default_qumlVersion")

  def onReceive(request: Request): Future[Response] = request.getOperation match {
    case "createQuestionSet" => AssessmentV5Manager.create(request)
    case "readQuestionSet" => read(request)
    case "readPrivateQuestionSet" => privateRead(request)
    case "getHierarchy" => getHierarchy(request)
    case "reviewQuestionSet" => review(request)
    case "rejectQuestionSet" => reject(request)
    case "publishQuestionSet" => publish(request)
    case "retireQuestionSet" => retire(request)
    case "updateQuestionSet" => update(request)
    case "addQuestion" => HierarchyManager.addLeafNodesToHierarchy(request)
    case "removeQuestion" => HierarchyManager.removeLeafNodesFromHierarchy(request)
    case "updateHierarchy" => UpdateHierarchyManager.updateHierarchy(request)
    case "importQuestionSet" => importQuestionSet(request)
    case "systemUpdateQuestionSet" => systemUpdate(request)
    case "copyQuestionSet" => copy(request)
    case "updateCommentQuestionSet" => updateComment(request)
    case "readCommentQuestionSet" => AssessmentV5Manager.readComment(request, "comments")
    case _ => Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, "INVALID_OPERATION", "Operation '" + request.getOperation + "' not supported"))
  }

  def read(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
    request.getRequest.put("fields", fields)
    DataNode.read(request).map(node => {
      if (StringUtils.equalsIgnoreCase(node.getMetadata.get("visibility").asInstanceOf[String], "Private"))
        throw new ClientException(AssessmentErrorCodes.ERR_ACCESS_DENIED, s"QuestionSet visibility is private, hence access denied")
      ResponseHandler.OK.put("questionset", AssessmentV5Manager.getQuestionSetMetadata(node, fields))
    })
  }

  def privateRead(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
    request.getRequest.put("fields", fields)
    if (StringUtils.isBlank(request.getRequest.getOrDefault("channel", "").asInstanceOf[String])) throw new ClientException("ERR_INVALID_CHANNEL", "Please Provide Channel!")
    DataNode.read(request).map(node => {
      if (!StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("channel", "").asInstanceOf[String], request.getRequest.getOrDefault("channel", "").asInstanceOf[String]))
        throw new ClientException(AssessmentErrorCodes.ERR_ACCESS_DENIED, "Channel Id Is Not Matched. Please Provide Valid Channel Id!")
      ResponseHandler.OK.put("questionset", AssessmentV5Manager.getQuestionSetMetadata(node, fields))
    })
  }

  def getHierarchy(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    HierarchyManager.getHierarchy(request).map(resp => {
      if (StringUtils.equalsIgnoreCase(resp.getResponseCode.toString, "OK")) {
        var hierarchyMap = resp.getResult.get("questionSet").asInstanceOf[util.Map[String, AnyRef]]
        if (MapUtils.isEmpty(hierarchyMap)) {
            hierarchyMap = resp.getResult.get("content").asInstanceOf[util.Map[String, AnyRef]]
        }
        if (MapUtils.isNotEmpty(hierarchyMap)) {
            val hStr: String = JsonUtils.serialize(hierarchyMap)
            val regex = """\"identifier\":\"(.*?)\.img\""""
            val pattern = regex.r
            val updateHStr = pattern.replaceAllIn(hStr, m => s""""identifier":"${m.group(1)}"""")
            val updatedHierarchyMap = JsonUtils.deserialize[util.Map[String, AnyRef]](updateHStr, classOf[util.Map[String, AnyRef]])
            val schemaVersion = updatedHierarchyMap.getOrDefault("schemaVersion", "1.0").asInstanceOf[String]
            val updateHierarchy = if (StringUtils.equalsIgnoreCase("1.0", schemaVersion)) AssessmentV5Manager.getTransformedHierarchy(updatedHierarchyMap) else {
              updatedHierarchyMap
            }
            resp.getResult.remove("questionSet")
            resp.put("questionset", updateHierarchy)
            resp
        } else resp
      } else resp
    })
  }

  @throws[Exception]
  def review(request: Request): Future[Response] = {
    val extPropNameList: util.List[String] = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String], request.getContext.get("schemaName").asInstanceOf[String]).asJava
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    request.getRequest.put("mode", "edit")
    request.getRequest.put("fields", extPropNameList)
    DataNode.read(request).map(node => {
      if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
        throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, s"${node.getObjectType.replace("Image", "")} with visibility Parent, can't be sent for review individually.")
      if (!StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Draft"))
        throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, s"${node.getObjectType.replace("Image", "")} with status other than Draft can't be sent for review.")
      val version: Double = node.getMetadata.getOrDefault("qumlVersion", 1.0.asInstanceOf[AnyRef]).asInstanceOf[Double]
      if (version < 1.1)
        throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, "QuestionSet can't be sent for review as data is not in QuML 1.1 format.")
      val hStr = node.getMetadata.getOrDefault("hierarchy", "").asInstanceOf[String]
      val hierarchy = if (StringUtils.isNotBlank(hStr)) JsonUtils.deserialize(hStr, classOf[java.util.Map[String, AnyRef]]) else new util.HashMap[String, AnyRef]()
      val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.List[java.util.Map[String, AnyRef]]]
      if (children.isEmpty)
        throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, "No hierarchy found for identifier:" + request.getContext.get("identifier"))
      AssessmentV5Manager.validateHierarchy(request, children, node.getMetadata.getOrDefault("createdBy", "").asInstanceOf[String])
      val (updatedHierarchy, nodeIds) = AssessmentV5Manager.updateHierarchy(hierarchy, "Review", node.getMetadata.getOrDefault("createdBy", "").asInstanceOf[String])
      val updateReq = new Request(request)
      val date = DateUtils.formatCurrentDate
      updateReq.putAll(Map("identifiers" -> nodeIds, "metadata" -> Map("status" -> "Review", "prevStatus" -> node.getMetadata.get("status"), "lastStatusChangedOn" -> date, "lastUpdatedOn" -> date).asJava).asJava)
      updateHierarchyNodes(updateReq, node, Map("status" -> "Review", "hierarchy" -> updatedHierarchy), nodeIds)
    }).flatMap(f => f)
  }

  def reject(request: Request): Future[Response] = {
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    request.getRequest.put("mode", "edit")
    AssessmentV5Manager.getValidateNodeForReject(request, AssessmentErrorCodes.ERR_OBJECT_VALIDATION).flatMap(node => {
      request.getContext.put("version", node.getMetadata.getOrDefault("schemaVersion", "1.0").asInstanceOf[String])
      AssessmentV5Manager.getQuestionSetHierarchy(request, node).flatMap(hierarchyString => {
        val hierarchy = if (StringUtils.isNotBlank(hierarchyString)) JsonUtils.deserialize(hierarchyString, classOf[java.util.Map[String, AnyRef]]) else new util.HashMap[String, AnyRef]()
        val (updatedHierarchy, nodeIds) = AssessmentV5Manager.updateHierarchy(hierarchy, "Draft", node.getMetadata.getOrDefault("createdBy", "").asInstanceOf[String])
        val updateReq = new Request(request)
        val date = DateUtils.formatCurrentDate
        updateReq.putAll(Map("identifiers" -> nodeIds, "metadata" -> Map("status" -> "Draft", "prevStatus" -> node.getMetadata.getOrDefault("status", "Review"), "lastStatusChangedOn" -> date, "lastUpdatedOn" -> date).asJava).asJava)
        val metadata: Map[String, AnyRef] = Map("status" -> "Draft", "hierarchy" -> updatedHierarchy)
        val updatedMetadata = if (request.getRequest.containsKey("rejectComment")) (metadata ++ Map("rejectComment" -> request.get("rejectComment").asInstanceOf[String])) else metadata
        updateHierarchyNodes(updateReq, node, updatedMetadata, nodeIds)
      })
    })
  }

  def publish(request: Request): Future[Response] = {
    val lastPublishedBy: String = request.getRequest.getOrDefault("lastPublishedBy", "").asInstanceOf[String]
    val requestId = request.getContext().getOrDefault("requestId","").asInstanceOf[String]
    val featureName = request.getContext().getOrDefault("featureName","").asInstanceOf[String]
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    request.put("mode", "edit")
    AssessmentV5Manager.getValidatedNodeForPublish(request, AssessmentErrorCodes.ERR_OBJECT_VALIDATION).flatMap(node => {
      AssessmentV5Manager.getQuestionSetHierarchy(request, node).map(hierarchyString => {
        val hierarchy = if (StringUtils.isNotBlank(hierarchyString)) JsonUtils.deserialize(hierarchyString, classOf[java.util.Map[String, AnyRef]]) else new util.HashMap[String, AnyRef]()
        val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.List[java.util.Map[String, AnyRef]]]
        if (children.isEmpty)
          throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, "No children's found for identifier:" + request.getContext.get("identifier"))
        AssessmentV5Manager.validateHierarchy(request, children, node.getMetadata.getOrDefault("createdBy", "").asInstanceOf[String])
        if (StringUtils.isNotBlank(lastPublishedBy))
          node.getMetadata.put("lastPublishedBy", lastPublishedBy)
        AssessmentV5Manager.pushInstructionEvent(node.getIdentifier, node, requestId, featureName)
        ResponseHandler.OK.putAll(Map[String, AnyRef]("identifier" -> node.getIdentifier.replace(".img", ""), "message" -> "QuestionSet is successfully sent for Publish").asJava)
      })
    })
  }

  def retire(request: Request): Future[Response] = {
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    AssessmentV5Manager.getValidatedNodeForRetire(request, AssessmentErrorCodes.ERR_OBJECT_VALIDATION).flatMap(node => {
      val updateRequest = new Request(request)
      updateRequest.put("identifiers", java.util.Arrays.asList(request.get("identifier").asInstanceOf[String], request.get("identifier").asInstanceOf[String] + ".img"))
      val date = DateUtils.formatCurrentDate
      val updateMetadata: util.Map[String, AnyRef] = Map("prevStatus" -> node.getMetadata.get("status"), "status" -> "Retired", "lastStatusChangedOn" -> date, "lastUpdatedOn" -> date).asJava
      updateRequest.put("metadata", updateMetadata)
      DataNode.bulkUpdate(updateRequest).map(_ => {
        ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
      })
    })
  }

  def update(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    val versionKey = request.getRequest.getOrDefault("versionKey","").asInstanceOf[String];
    if (null == versionKey || versionKey.isEmpty)
      throw new ClientException("ERR_INVALID_REQUEST", "Please provide valid request.")
    DataNode.read(request).map(node => {
      if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
        throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, node.getMetadata.getOrDefault("objectType", "").asInstanceOf[String].replace("Image", "") + " with visibility Parent, can't be updated individually.")
      val version: Double = node.getMetadata.getOrDefault("qumlVersion", 1.0.asInstanceOf[AnyRef]).asInstanceOf[Double]
      if (version < 1.1)
        throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, "QuestionSet can't be updated as data is not in QuML 1.1 format.")
      val schemaVersion = node.getMetadata.getOrDefault("schemaVersion", "1.0").asInstanceOf[String]
      request.getContext.put("version", schemaVersion)
      val prevStatus = node.getMetadata().getOrDefault("status", "").asInstanceOf[String]
      if (StringUtils.isNotBlank(prevStatus) && List("Live", "Unlisted").contains(prevStatus))
        request.getRequest.put("prevStatus", prevStatus)
      DataNode.update(request).map(node => {
        ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
      })
    }).flatMap(f => f)
  }

  def importQuestionSet(request: Request): Future[Response] = importMgr.importObject(request)

  def getImportConfig(): ImportConfig = {
    val requiredProps = Platform.getStringList("import.required_props.questionset", java.util.Arrays.asList("name", "code", "mimeType", "framework")).asScala.toList
    val validStages = Platform.getStringList("import.valid_stages.questionset", java.util.Arrays.asList("create", "upload", "review", "publish")).asScala.toList
    val propsToRemove = Platform.getStringList("import.remove_props.questionset", java.util.Arrays.asList()).asScala.toList
    val topicName = Platform.config.getString("import.output_topic_name")
    val reqLimit = Platform.getInteger("import.request_size_limit", 200)
    val validSourceStatus = Platform.getStringList("import.valid_source_status", java.util.Arrays.asList("Live", "Unlisted")).asScala.toList
    ImportConfig(topicName, reqLimit, requiredProps, validStages, propsToRemove, validSourceStatus)
  }

  def systemUpdate(request: Request): Future[Response] = {
    val identifier = request.getContext.get("identifier").asInstanceOf[String]
    RequestUtil.validateRequest(request)
    if (Platform.getBoolean("questionset.cache.enable", false))
      RedisCache.delete(hierarchyPrefix + identifier)
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
      DataNode.systemUpdate(request, response, "questionSet", Some(HierarchyManager.getHierarchy))
    }).map(node => ResponseHandler.OK.put("identifier", identifier).put("status", "success"))
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

  def updateNode(request: Request, node: Node, metadata: Map[String, AnyRef]): Future[Response] = {
    val updateRequest = new Request(request)
    val date = DateUtils.formatCurrentDate
    val fMeta: Map[String, AnyRef] = Map("versionKey" -> node.getMetadata.get("versionKey"), "prevStatus" -> node.getMetadata.get("status"), "lastStatusChangedOn" -> date, "lastUpdatedOn" -> date) ++ metadata
    updateRequest.getContext.put("identifier", request.getContext.get("identifier"))
    updateRequest.putAll(fMeta.asJava)
    DataNode.update(updateRequest).map(_ => {
      ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
    })
  }

  def copy(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    CopyManager.copy(request)
  }

  def updateComment(request: Request): Future[Response] = {
    val validatedNode = AssessmentV5Manager.getValidatedNodeForUpdateComment(request, "ERR_QUESTION_SET_UPDATE_COMMENT")
    val commentValue = request.getRequest.getOrDefault("reviewComment", "").asInstanceOf[String]
    validatedNode.flatMap { node =>
      val updateReq = new Request(request)
      updateReq.getRequest.put("rejectComment", commentValue)
      updateReq.getContext.put("identifier", node.getIdentifier)
      DataNode.update(updateReq).map { _ =>
        val responseMap = Map("identifier" -> node.getIdentifier.replace(".img", "").asInstanceOf[AnyRef])
        val response = ResponseHandler.OK
        response.putAll(responseMap.asJava)
        response
      }
    }
  }

  override def createReceive(): AbstractActor.Receive =
    receiveBuilder()
      .`match`(classOf[Request], (req: Request) => {
        onReceive(req).pipeTo(sender())
      })
      .build()
}

