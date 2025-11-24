package org.sunbird.utils.assessment

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform
import org.sunbird.common.exception.ClientException
import org.sunbird.common.dto.Request
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.JavaJsonUtils

import java.util
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object AssessmentItemUtils {
  def validateRetirePermissions(request: Request, node: Node): Unit = {
    val currentStatus = node.getMetadata.getOrDefault("status", "Draft").asInstanceOf[String]
    if (StringUtils.equalsIgnoreCase(currentStatus, "Processing")) {
      throw new ClientException("ERR_ASSESSMENT_ITEM_RETIRE", "Cannot retire assessment item in processing state")
    }
  }

  def validateAssessmentItemUsage(node: Node): Unit = {
    val inRelations = node.getInRelations
    if (inRelations != null && !inRelations.isEmpty) {
      val activeRelations = inRelations.asScala.filter(rel =>
        rel.getStartNodeMetadata != null &&
        !StringUtils.equalsIgnoreCase(rel.getStartNodeMetadata.getOrDefault("status", "").asInstanceOf[String], "Retired")
      )
      if (activeRelations.nonEmpty) {
        TelemetryManager.warn("Assessment item has active relations but proceeding with retirement: " + node.getIdentifier)
      }
    }
  }

  def replaceMediaItemsWithVariants(assessmentItem: util.Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Unit = {
    val media = assessmentItem.get("media")
    if (media != null && StringUtils.isNotBlank(media.toString)) {
      val mediaList = if (media.isInstanceOf[String]) {
        JavaJsonUtils.deserialize[java.util.List[java.util.Map[String, Object]]](media.toString)
      } else if (media.isInstanceOf[util.List[_]]) {
        media.asInstanceOf[java.util.List[java.util.Map[String, Object]]]
      } else {
        null
      }
      if (mediaList != null && !mediaList.isEmpty) {
        var replaced = false
        val resolution = Platform.getString("assessment.media.resolution", "low")
        val processedMediaList = mediaList.asScala.map { mediaItem =>
          processMediaItem(mediaItem, resolution) match {
            case Some(updatedItem) =>
              replaced = true
              updatedItem
            case None => mediaItem
          }
        }.asJava
        if (replaced) {
          val updatedMedia = JavaJsonUtils.serialize(processedMediaList)
          assessmentItem.put("media", updatedMedia)
        }
      }
    }
  }

  def processMediaItem(mediaItem: java.util.Map[String, Object], resolution: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Option[java.util.Map[String, Object]] = {
    var assetId = mediaItem.get("asset_id")
    if (assetId == null) {
      assetId = mediaItem.get("assetId")
    }
    if (assetId != null && StringUtils.isNotBlank(assetId.toString)) {
      val assetRequest = new Request()
      assetRequest.getContext.put("identifier", assetId.toString)
      assetRequest.setOperation("getDataNode")
      DataNode.read(assetRequest).map { assetNode =>
        if (assetNode != null) {
          val variantsJSON = assetNode.getMetadata.get("variants")
          if (variantsJSON != null && StringUtils.isNotBlank(variantsJSON.toString)) {
            val variants = JavaJsonUtils.deserialize[java.util.Map[String, String]](variantsJSON.toString)
            if (variants != null && !variants.isEmpty) {
              val variantURL = variants.get(resolution)
              if (StringUtils.isNotEmpty(variantURL)) {
                val updatedMediaItem = new java.util.HashMap[String, Object](mediaItem)
                updatedMediaItem.put("src", variantURL)
                if (variants.containsKey("high")) {
                  updatedMediaItem.put("highResolutionSrc", variants.get("high"))
                }
                if (variants.containsKey("medium")) {
                  updatedMediaItem.put("mediumResolutionSrc", variants.get("medium"))
                }
                return Some(updatedMediaItem)
              }
            }
          }
        }
      }
    }
    None
  }

  def getDefaultFramework(): String = {
    Platform.getString("assessment.default.framework", "NCF")
  }

  def getSkipValidation(requestData: util.Map[String, AnyRef]): Boolean = {
    requestData.getOrDefault("skipValidation", false.asInstanceOf[AnyRef]).asInstanceOf[Boolean]
  }

  def extractMetadata(requestData: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    if (requestData.containsKey("metadata")) {
      requestData.get("metadata").asInstanceOf[util.Map[String, AnyRef]]
    } else {
      throw new ClientException("ERR_ASSESSMENT_ITEM", "Assessment Item metadata is missing")
    }
  }

  def populateDefaults(metadata: util.Map[String, AnyRef]): Unit = {
    if (!metadata.containsKey("objectType")) metadata.put("objectType", "AssessmentItem")
    if (!metadata.containsKey("mimeType")) metadata.put("mimeType", "application/vnd.sunbird.assessmentitem")
    if (!metadata.containsKey("framework") || StringUtils.isBlank(metadata.get("framework").asInstanceOf[String])) metadata.put("framework", getDefaultFramework())
    if (!metadata.containsKey("version")) metadata.put("version", java.lang.Integer.valueOf(1))
    if (metadata.containsKey("level")) metadata.remove("level")
  }

  def flattenMetadataToRequest(request: Request, metadata: util.Map[String, AnyRef]): Unit = {
    request.getRequest.remove("metadata")
    request.getRequest.putAll(metadata)
    if (!request.getRequest.containsKey("objectType")) {
      request.getRequest.put("objectType", "AssessmentItem")
    }
  }
}
