package org.sunbird.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.common.Platform
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.RequestUtil
import org.sunbird.managers.AssessmentManager
import org.sunbird.validators.AssessmentItemValidator
import org.sunbird.utils.JavaJsonUtils
import org.sunbird.utils.AssessmentItemUtils
import org.sunbird.common.{DateUtils}

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class AssessmentItemActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = request.getOperation match {
    case "createItem" => create(request)
    case "readItem" => read(request)
    case "updateItem" => update(request)
    case "retireItem" => retire(request)
    case _ => ERROR(request.getOperation)
  }

  def create(request: Request): Future[Response] = {
    val requestData = request.getRequest
    val skipValidation = AssessmentItemUtils.getSkipValidation(requestData)
    val metadata = AssessmentItemUtils.extractMetadata(requestData)
    AssessmentItemUtils.populateDefaults(metadata)
    AssessmentItemUtils.replaceMediaItemsWithVariants(metadata)
    AssessmentItemUtils.flattenMetadataToRequest(request, metadata)
    if (!skipValidation) AssessmentItemValidator.validateAssessmentItemRequest(requestData, "ASSESSMENT_ITEM_CREATE")
    DataNode.create(request).map { node =>
      ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "node_id" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
    }
  }

  def read(request: Request): Future[Response] = {
    val fieldsParam = Option(request.get("fields")).map(_.asInstanceOf[String]).getOrElse("")
    val requestedFields: util.List[String] = fieldsParam.split(",")
      .filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))
      .toList.asJava
    val extPropNameList: util.List[String] = DefinitionNode.getExternalProps(
      request.getContext.get("graph_id").asInstanceOf[String],
      request.getContext.get("version").asInstanceOf[String],
      request.getContext.get("schemaName").asInstanceOf[String]
    ).asJava
    val mergedFields: util.List[String] = (requestedFields.asScala ++ extPropNameList.asScala).distinct.asJava
    request.getRequest.put("fields", mergedFields)
    
    DataNode.read(request).map(node => {
      if (NodeUtil.isRetired(node)) {
        throw new ResourceNotFoundException("ERR_ASSESSMENT_ITEM_NOT_FOUND", "Assessment Item not found with identifier: " + node.getIdentifier)
      }
      
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, requestedFields, node.getObjectType.toLowerCase.replace("image", ""), request.getContext.get("version").asInstanceOf[String]) 
      val externalData = node.getExternalData
      val bodyFromExternal = if (externalData != null && externalData.containsKey("body")) externalData.get("body") else null
      val bodyValue = if (bodyFromExternal != null) bodyFromExternal else node.getMetadata.get("body")
      if (bodyValue != null) metadata.put("body", bodyValue)
      metadata.put("identifier", node.getIdentifier.replace(".img", ""))
      ResponseHandler.OK.put("assessment_item", metadata)
    })
  }

  def update(request: Request): Future[Response] = {
    val requestData = request.getRequest
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    DataNode.read(request).flatMap(existingNode => {
      if (NodeUtil.isRetired(existingNode)) {
        throw new ClientException("ERR_ASSESSMENT_ITEM_UPDATE", "Cannot update retired assessment item: " + existingNode.getIdentifier)
      }
      val skipValidation = AssessmentItemUtils.getSkipValidation(requestData)
      val metadata = AssessmentItemUtils.extractMetadata(requestData)
      AssessmentItemUtils.populateDefaults(metadata)
      AssessmentItemUtils.replaceMediaItemsWithVariants(metadata)
      AssessmentItemUtils.flattenMetadataToRequest(request, metadata)
      if (!skipValidation) AssessmentItemValidator.validateAssessmentItemRequest(requestData, "ASSESSMENT_ITEM_UPDATE")
      DataNode.update(request).map { node =>
        ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "node_id" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
      }
    })
  }

  def retire(request: Request): Future[Response] = {
    request.getRequest.put("identifier", request.getContext.get("identifier"))
    DataNode.read(request).flatMap(node => {
      if (NodeUtil.isRetired(node)) {
        throw new ClientException("ERR_ASSESSMENT_ITEM_RETIRE", "Assessment Item is already retired: " + node.getIdentifier)
      }
      AssessmentItemUtils.validateRetirePermissions(request, node)
      AssessmentItemUtils.validateAssessmentItemUsage(node)
      val identifier = request.get("identifier").asInstanceOf[String]
      val updateRequest = new Request(request)
      val identifiers = java.util.Arrays.asList(identifier, identifier + ".img")
      updateRequest.put("identifiers", identifiers)
      val date = DateUtils.formatCurrentDate
      val updateMetadata: util.Map[String, AnyRef] = Map(
        "prevStatus" -> node.getMetadata.get("status"),
        "status" -> "Retired",
        "lastStatusChangedOn" -> date,
        "lastUpdatedOn" -> date
      ).asJava
      updateRequest.put("metadata", updateMetadata)
      DataNode.bulkUpdate(updateRequest).map(_ => {
        ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "node_id" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
      })
    })
  }
}
