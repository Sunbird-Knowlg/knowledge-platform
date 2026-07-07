package org.sunbird.v5.managers

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{DateUtils, HtmlSanitizer, JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.{DefinitionNode, ObjectCategoryDefinition}
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.managers.questionset.HierarchyManager
import org.sunbird.telemetry.util.LogTelemetryEventUtil
import org.sunbird.utils.AssessmentErrorCodes
import org.sunbird.utils.questionset.RequestUtil

import java.util
import java.util.UUID
import scala.collection.JavaConverters
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.collection.convert.ImplicitConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.util.Try

object AssessmentV5Manager {

  val defaultVersion = Platform.config.getNumber("v5_default_qumlVersion")
  val supportedVersions: java.util.List[Number] = Platform.config.getNumberList("v5_supported_qumlVersions")
  val skipValidation: Boolean = Platform.getBoolean("assessment.skip.validation", false)
  val validStatus = List("Draft", "Review")

  // Derived from config — used to validate that a node meets the platform's minimum QuML version requirement.
  val minSupportedVersion: Double = supportedVersions.asScala.map(_.doubleValue()).min

  // Fixed format boundary in the QuML spec: 1.1 is when the interaction/responseDeclaration schema changed.
  // Used only in data-migration transforms (1.0 → 1.1), NOT for platform version validation.
  private val QUML_1_1_FORMAT_VERSION: Double = 1.1

  def validateAndGetVersion(ver: AnyRef): AnyRef = {
    if (supportedVersions.contains(ver)) ver else throw new ClientException(AssessmentErrorCodes.ERR_REQUEST_DATA_VALIDATION, s"Platform doesn't support quml version ${ver} | Currently Supported quml version are: ${supportedVersions}")
  }

  def validateVisibilityForCreate(request: Request, errCode: String): Unit = {
    val visibility: String = request.getRequest.getOrDefault("visibility", "").asInstanceOf[String]
    if (StringUtils.isNotBlank(visibility) && StringUtils.equalsIgnoreCase(visibility, "Parent"))
      throw new ClientException(errCode, "Visibility Cannot Be Parent!")
  }

  def create(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    validateVisibilityForCreate(request, AssessmentErrorCodes.ERR_REQUEST_DATA_VALIDATION)
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
          case _ => metadata.getOrDefault(key, "")
        }
        metadata.put(key, data)
      }
    })
    metadata
  }

  def getQuestionMetadata(node: Node, fields: util.List[String], extFields: util.List[String],
                          lang: String = "")(implicit oec: OntologyEngineContext, ec: ExecutionContext): util.Map[String, AnyRef] = {
    val version: AnyRef = node.getMetadata.getOrDefault("schemaVersion", 1.0.asInstanceOf[AnyRef])
    val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, List().asJava, node.getObjectType.toLowerCase.replace("Image", ""), version.toString)
    val updatedMeta = if (version.toString == "1.0") getTransformedQuestionMetadata(metadata) else convertOneOfProps(node, metadata)
    if (CollectionUtils.isNotEmpty(fields))
      updatedMeta.keySet.retainAll(fields)
    else updatedMeta.keySet().removeAll(extFields)
    updatedMeta.put("identifier", node.getIdentifier.replace(".img", ""))
    if (StringUtils.isNotBlank(lang)) filterByLanguage(updatedMeta, lang)
    updatedMeta
  }

  private val systemDefaultLang: String = Platform.getString("platform.default.language", "en")
  // Fields whose value is directly an i18n map: { "en": "...", "hi": "..." }
  private val directI18nFields: Set[String] = Set("body", "instructions", "answer")
  // Fields whose value is an id-keyed map of i18n values: { "<id>": "..." | { "en": "...", "hi": "..." } }
  private val keyedI18nFields: Set[String] = Set("hints", "feedback", "solutions")

  // Resolve an i18n value to the requested language, falling back to the system default.
  // Returns null when there is nothing to resolve (plain single-language string or unrelated value),
  // signalling the caller to leave the original value untouched.
  private def resolveI18n(value: AnyRef, lang: String): AnyRef = value match {
    case m: util.Map[_, _] =>
      val i18n = m.asInstanceOf[util.Map[String, AnyRef]]
      Option(i18n.get(lang)).orElse(Option(i18n.get(systemDefaultLang))).orNull
    case s: String =>
      // Defensive: the read path normally deserializes oneOf props to maps, but if that step
      // was skipped/failed the value can arrive as a JSON-encoded i18n map string. Parse it so
      // we never leak a raw i18n blob to the client; plain HTML strings won't parse and are left as-is.
      Try(JsonUtils.deserialize(s, classOf[util.Map[String, AnyRef]])).toOption.flatMap { parsed =>
        if (parsed != null && (parsed.containsKey(lang) || parsed.containsKey(systemDefaultLang)))
          Option(parsed.get(lang)).orElse(Option(parsed.get(systemDefaultLang)))
        else None
      }.orNull
    case _ => null
  }

  private[managers] def filterByLanguage(metadata: util.Map[String, AnyRef], lang: String): Unit = {
    directI18nFields.foreach { field =>
      if (metadata.containsKey(field)) {
        val resolved = resolveI18n(metadata.get(field), lang)
        if (resolved != null) metadata.put(field, resolved)
      }
    }
    keyedI18nFields.foreach { field =>
      metadata.get(field) match {
        case m: util.Map[_, _] =>
          val keyed = m.asInstanceOf[util.Map[String, AnyRef]]
          keyed.keySet().asScala.toList.foreach { id =>
            val resolved = resolveI18n(keyed.get(id), lang)
            if (resolved != null) keyed.put(id, resolved)
          }
        case _ =>
      }
    }
    if (metadata.containsKey("interactions")) {
      metadata.get("interactions") match {
        case intMap: util.Map[_, _] =>
          intMap.asInstanceOf[util.Map[String, AnyRef]].values().asScala.foreach {
            case obj: util.Map[_, _] =>
              val interaction = obj.asInstanceOf[util.Map[String, AnyRef]]
              interaction.getOrDefault("options", null) match {
                case flat: util.List[_] =>
                  filterOptionLabels(flat.asInstanceOf[util.List[util.Map[String, AnyRef]]], lang)
                case sides: util.Map[_, _] =>
                  val sidesMap = sides.asInstanceOf[util.Map[String, AnyRef]]
                  Seq("left", "right").foreach { side =>
                    sidesMap.getOrDefault(side, null) match {
                      case list: util.List[_] =>
                        filterOptionLabels(list.asInstanceOf[util.List[util.Map[String, AnyRef]]], lang)
                      case _ =>
                    }
                  }
                case _ =>
              }
            case _ =>
          }
        case _ =>
      }
    }
  }

  private[managers] def filterOptionLabels(options: util.List[util.Map[String, AnyRef]], lang: String): Unit = {
    options.asScala.foreach { opt =>
      Seq("label", "hint").foreach { key =>
        opt.get(key) match {
          case m: util.Map[_, _] =>
            val i18n = m.asInstanceOf[util.Map[String, AnyRef]]
            val v = Option(i18n.get(lang)).orElse(Option(i18n.get(systemDefaultLang))).orNull
            if (v != null) opt.put(key, v)
          case _ =>
        }
      }
    }
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
    val rawComment = request.getRequest.get("reviewComment")
    val commentValue = if (rawComment != null) rawComment.toString else ""
    if (commentValue.trim.isEmpty) {
      Future.failed(new ClientException(errCode, "Comment key is missing or value is empty in the request body."))
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
    val metadata = node.getMetadata
    if (StringUtils.isBlank(metadata.getOrDefault("body", "").asInstanceOf[String]))
      messages += "body"
    if (null != metadata.get("interactionTypes")) {
      if (StringUtils.isBlank(metadata.getOrElse("responseDeclaration", "").asInstanceOf[String])) messages += "responseDeclaration"
      if (StringUtils.isBlank(metadata.getOrElse("interactions", "").asInstanceOf[String])) messages += "interactions"
      if (StringUtils.isBlank(metadata.getOrElse("outcomeDeclaration", "").asInstanceOf[String])) messages += "outcomeDeclaration"
      if (messages.isEmpty) {
        val qType = metadata.getOrDefault("qType", "").asInstanceOf[String]
        val rd = JsonUtils.deserialize(metadata.getOrElse("responseDeclaration", "{}").asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
        val interactions = JsonUtils.deserialize(metadata.getOrElse("interactions", "{}").asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
        qType.toUpperCase match {
          case "FTB"         => messages ++= validateFTB(rd, interactions)
          case "MTF"         => messages ++= validateMTF(rd, interactions)
          case "SEQ" | "REO" => messages ++= validateOrdered(rd, interactions)
          case _             =>
        }
      }
    } else {
      if (StringUtils.isBlank(metadata.getOrDefault("answer", "").asInstanceOf[String]))
        messages += "answer"
    }
    messages.toList
  }

  private[managers] def validateFTB(rd: util.Map[String, AnyRef], interactions: util.Map[String, AnyRef]): List[String] = {
    val errs = ListBuffer[String]()
    rd.keySet().asScala.foreach { key =>
      val resp = rd.get(key).asInstanceOf[util.Map[String, AnyRef]]
      if (!StringUtils.equalsIgnoreCase("single", resp.getOrDefault("cardinality", "").asInstanceOf[String]))
        errs += s"responseDeclaration.$key.cardinality must be 'single' for FTB"
      if (!StringUtils.equalsIgnoreCase("string", resp.getOrDefault("type", "").asInstanceOf[String]))
        errs += s"responseDeclaration.$key.type must be 'string' for FTB"
      val cv = resp.getOrDefault("correctResponse", new util.HashMap[String, AnyRef]())
                 .asInstanceOf[util.Map[String, AnyRef]].getOrDefault("value", null)
      if (cv == null || cv.toString.isBlank) errs += s"responseDeclaration.$key.correctResponse.value is required"
      val intType = interactions.getOrDefault(key, new util.HashMap[String, AnyRef]())
                      .asInstanceOf[util.Map[String, AnyRef]].getOrDefault("type", "").asInstanceOf[String]
      if (!StringUtils.equalsIgnoreCase("text", intType)) errs += s"interactions.$key.type must be 'text' for FTB"
    }
    errs.toList
  }

  private[managers] def validateMTF(rd: util.Map[String, AnyRef], interactions: util.Map[String, AnyRef]): List[String] = {
    val errs = ListBuffer[String]()
    if (rd.size() != 1) errs += "MTF must have exactly one responseDeclaration entry"
    rd.keySet().asScala.foreach { key =>
      val resp = rd.get(key).asInstanceOf[util.Map[String, AnyRef]]
      if (!StringUtils.equalsIgnoreCase("single", resp.getOrDefault("cardinality", "").asInstanceOf[String]))
        errs += s"responseDeclaration.$key.cardinality must be 'single' for MTF"
      if (!StringUtils.equalsIgnoreCase("map", resp.getOrDefault("type", "").asInstanceOf[String]))
        errs += s"responseDeclaration.$key.type must be 'map' for MTF"
      val correctVal = resp.getOrDefault("correctResponse", new util.HashMap[String, AnyRef]())
                         .asInstanceOf[util.Map[String, AnyRef]]
                         .getOrDefault("value", null)
      val correctMap: util.Map[String, AnyRef] = if (correctVal != null && correctVal.isInstanceOf[util.Map[_, _]])
        correctVal.asInstanceOf[util.Map[String, AnyRef]] else null
      if (correctMap == null || correctMap.isEmpty) errs += s"responseDeclaration.$key.correctResponse.value must be a non-empty map"
      val opts = interactions.getOrDefault(key, new util.HashMap[String, AnyRef]())
                   .asInstanceOf[util.Map[String, AnyRef]]
                   .getOrDefault("options", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      val left  = opts.getOrDefault("left",  new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      val right = opts.getOrDefault("right", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      if (left.isEmpty)  errs += s"interactions.$key.options.left must be non-empty for MTF"
      if (right.isEmpty) errs += s"interactions.$key.options.right must be non-empty for MTF"
      if (correctMap != null && !left.isEmpty && !right.isEmpty) {
        val leftVals  = left.asScala.map(_.getOrDefault("value", "").asInstanceOf[String]).toSet
        val rightVals = right.asScala.map(_.getOrDefault("value", "").asInstanceOf[String]).toSet
        correctMap.keySet().asScala.foreach(k => if (!leftVals.contains(k))  errs += s"correctResponse key '$k' not in left options")
        correctMap.values().asScala.foreach(v => if (!rightVals.contains(v.asInstanceOf[String])) errs += s"correctResponse value '$v' not in right options")
        val rhsUsed = correctMap.values().asScala.toList
        if (rhsUsed.size != rhsUsed.toSet.size) errs += "correctResponse.value violates one-to-one constraint (duplicate RHS)"
      }
    }
    errs.toList
  }

  private[managers] def validateOrdered(rd: util.Map[String, AnyRef], interactions: util.Map[String, AnyRef]): List[String] = {
    val errs = ListBuffer[String]()
    if (rd.size() != 1) errs += "Sequence/Reorder must have exactly one responseDeclaration entry"
    rd.keySet().asScala.foreach { key =>
      val resp = rd.get(key).asInstanceOf[util.Map[String, AnyRef]]
      if (!StringUtils.equalsIgnoreCase("ordered", resp.getOrDefault("cardinality", "").asInstanceOf[String]))
        errs += s"responseDeclaration.$key.cardinality must be 'ordered'"
      val rType = resp.getOrDefault("type", "").asInstanceOf[String].toLowerCase
      if (rType != "string" && rType != "integer") errs += s"responseDeclaration.$key.type must be 'string' or 'integer'"
      val correctArr = resp.getOrDefault("correctResponse", new util.HashMap[String, AnyRef]())
                         .asInstanceOf[util.Map[String, AnyRef]]
                         .getOrDefault("value", null)
      val correctList: util.List[AnyRef] = if (correctArr != null && correctArr.isInstanceOf[util.List[_]])
        correctArr.asInstanceOf[util.List[AnyRef]] else null
      if (correctList == null || correctList.isEmpty) errs += s"responseDeclaration.$key.correctResponse.value must be a non-empty array"
      val options = interactions.getOrDefault(key, new util.HashMap[String, AnyRef]())
                      .asInstanceOf[util.Map[String, AnyRef]]
                      .getOrDefault("options", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      if (options.isEmpty) errs += s"interactions.$key.options must be non-empty"
      if (correctList != null && !options.isEmpty) {
        if (correctList.size() != options.size()) errs += s"correctResponse length (${correctList.size}) must equal options length (${options.size})"
        val optVals = options.asScala.map(_.getOrDefault("value", "").asInstanceOf[String]).toSet
        correctList.asScala.foreach(v => if (!optVals.contains(v.asInstanceOf[String])) errs += s"correctResponse value '$v' not in options")
      }
    }
    errs.toList
  }

  def validateHierarchy(request: Request, children: util.List[util.Map[String, AnyRef]], rootUserId: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Unit = {
    children.toList.foreach(content => {
      val version: Double = content.getOrDefault("qumlVersion", 1.0.asInstanceOf[AnyRef]).asInstanceOf[Double]
      if (version < minSupportedVersion)
        throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, s"Children Object with identifier ${content.get("identifier").toString} doesn't have data in QuML $minSupportedVersion format.")
      if ((StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Default")
        && !StringUtils.equals(rootUserId, content.getOrDefault("createdBy", "").asInstanceOf[String]))
        && !StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))
        throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, "Object with identifier: " + content.get("identifier") + " is not Live. Please Publish it.")
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
          throw new ClientException(AssessmentErrorCodes.ERR_OBJECT_VALIDATION, s"Mandatory Fields ${messages.asJava} Missing for ${content.get("identifier").toString}")
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
    children.toList.flatMap(content => {
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
      if (version < minSupportedVersion)
        throw new ClientException(errCode, s"${node.getObjectType().replace("Image", "")} can't be sent for publish as data is not in QuML $minSupportedVersion format.")
      node
    })
  }

  @throws[Exception]
  def pushInstructionEvent(identifier: String, node: Node, requestId: String, featureName: String)(implicit oec: OntologyEngineContext): Unit = {
    val (actor, context, objData, eData) = generateInstructionEventMetadata(identifier.replace(".img", ""), node, requestId, featureName)
    val beJobRequestEvent: String = LogTelemetryEventUtil.logInstructionEvent(actor.asJava, context.asJava, objData.asJava, eData)
    val topic: String = Platform.getString("kafka.publish.request.topic", "sunbirddev.knowlg.publish.job.request")
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
      val timeLimits:util.Map[String, AnyRef] = if(data.get("timeLimits").isInstanceOf[util.Map[String, AnyRef]]) data.getOrDefault("timeLimits", Map().asJava).asInstanceOf[util.Map[String, AnyRef]] else JsonUtils.deserialize(data.get("timeLimits").asInstanceOf[String], classOf[java.util.Map[String, AnyRef]])
      val maxTime: Integer = timeLimits.getOrElse("maxTime", "0").asInstanceOf[String].toInt
      val updatedData: util.Map[String, AnyRef] = Map("questionSet" -> Map("max" -> maxTime, "min" -> 0.asInstanceOf[AnyRef]).asJava).asJava.asInstanceOf[util.Map[String, AnyRef]]
      data.put("timeLimits", updatedData)
    }
  }

  def getAnswer(data: util.Map[String, AnyRef]): String = {
    val interactions: util.Map[String, AnyRef] = data.getOrDefault("interactions", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]]
    if (!StringUtils.equalsIgnoreCase(data.getOrElse("primaryCategory", "").asInstanceOf[String], "Subjective Question") && !interactions.isEmpty) {
      val responseDeclaration: util.Map[String, AnyRef] = data.getOrDefault("responseDeclaration", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]]
      val responseData = responseDeclaration.get("response1").asInstanceOf[util.Map[String, AnyRef]]
      val intractionsResp1: util.Map[String, AnyRef] = interactions.getOrElse("response1", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      val options = intractionsResp1.getOrElse("options", List().asJava).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      val answerData = responseData.get("cardinality").asInstanceOf[String] match {
        case "single" => {
          val correctResp = responseData.getOrDefault("correctResponse", Map().asJava).asInstanceOf[util.Map[String, AnyRef]].get("value").asInstanceOf[Integer]
          val label = options.toList.filter(op => op.get("value").asInstanceOf[Integer] == correctResp).head.get("label").asInstanceOf[String]
          val answer = """<div class="anwser-container"><div class="anwser-body">answer_html</div></div>""".replace("answer_html", HtmlSanitizer.escapeHtml(label))
          answer
        }
        case "multiple" => {
          val correctResp = responseData.getOrDefault("correctResponse", Map().asJava).asInstanceOf[util.Map[String, AnyRef]].get("value").asInstanceOf[util.List[Integer]]
          val singleAns = """<div class="anwser-body">answer_html</div>"""
          val answerList: List[String] = options.toList.filter(op => correctResp.contains(op.get("value").asInstanceOf[Integer])).map(op => singleAns.replace("answer_html", HtmlSanitizer.escapeHtml(op.get("label").asInstanceOf[String]))).toList
          val answer = """<div class="anwser-container">answer_div</div>""".replace("answer_div", answerList.mkString(""))
          answer
        }
        case _ => ""
      }
      answerData
    } else data.getOrDefault("answer", "").asInstanceOf[String]
  }

  def processInteractions(data: util.Map[String, AnyRef]): Unit = {
    val version = Try(data.getOrDefault("qumlVersion", 1.0.asInstanceOf[AnyRef]).toString.toDouble).getOrElse(1.0)
    if (version >= QUML_1_1_FORMAT_VERSION) return
    val interactions: util.Map[String, AnyRef] = data.getOrDefault("interactions", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]]
    if (!interactions.isEmpty) {
      val validation = interactions.getOrElse("validation", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      val resp1: util.Map[String, AnyRef] = interactions.getOrElse("response1", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      val resValData = interactions.getOrElse("response1", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]].getOrElse("validation", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      if (!resValData.isEmpty) resValData.putAll(validation) else resp1.put("validation", validation)
      interactions.remove("validation")
      interactions.put("response1", resp1)
      data.put("interactions", interactions)
    }
  }

  def processHints(data: util.Map[String, AnyRef]): Unit = {
    val hints = data.getOrDefault("hints", List[String]().asJava).asInstanceOf[util.List[String]]
    if (!hints.isEmpty) {
      val updatedHints: util.Map[String, AnyRef] = hints.toList.map(hint => Map[String, AnyRef](UUID.randomUUID().toString -> hint).asJava).flatten.toMap.asJava
      data.put("hints", updatedHints)
    }
  }

  def processResponseDeclaration(data: util.Map[String, AnyRef]): Unit = {
    val outcomeDeclaration = new util.HashMap[String, AnyRef]()
    val version = Try(data.getOrDefault("qumlVersion", 1.0.asInstanceOf[AnyRef]).toString.toDouble).getOrElse(1.0)
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
        for (key <- responseDeclaration.keySet()) {
          val responseData = responseDeclaration.get(key).asInstanceOf[util.Map[String, AnyRef]]
          // remove maxScore and put it under outcomeDeclaration
          val maxScore = Map[String, AnyRef]("cardinality" -> responseData.getOrDefault("cardinality", "").asInstanceOf[String], "type" -> responseData.getOrDefault("type", "").asInstanceOf[String], "defaultValue" -> responseData.get("maxScore"))
          responseData.remove("maxScore")
          outcomeDeclaration.put("maxScore", maxScore.asJava)
          //remove outcome from correctResponse
          responseData.getOrDefault("correctResponse", Map[String, AnyRef]().asJava).asInstanceOf[util.Map[String, AnyRef]].remove("outcomes")
          // type cast value — v1.0 only: integer type with single cardinality stored value as string
          if (version < QUML_1_1_FORMAT_VERSION) {
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
          }
          //update mapping — v1.0 only: mapping used {response, outcomes.score}; v1.1 uses {value, score} directly
          val mappingData = responseData.getOrElse("mapping", List[util.Map[String, AnyRef]]().asJava).asInstanceOf[util.List[util.Map[String, AnyRef]]]
          if (version < QUML_1_1_FORMAT_VERSION && !mappingData.isEmpty) {
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
        Map[String, AnyRef](solution.getOrElse("id", "").asInstanceOf[String] -> getSolutionString(solution, data.getOrElse("media", List[util.Map[String, AnyRef]](Map[String, AnyRef]().asJava).asJava).asInstanceOf[util.List[util.Map[String, AnyRef]]]))
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
          val mediaData: util.Map[String, AnyRef] = media.asScala.toList.filter(map => StringUtils.equalsIgnoreCase(value, map.getOrElse("id", "").asInstanceOf[String])).flatten.toMap.asJava
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
      children.foreach(ch => {
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
