package org.sunbird.utils

import java.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform
import org.sunbird.graph.dac.model.Node

import scala.collection.JavaConverters._

object HierarchyBackwardCompatibilityUtil {

    val categoryMap: java.util.Map[String, AnyRef] = Platform.getAnyRef("contentTypeToPrimaryCategory",
        new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]

    def setContentAndCategoryTypes(input: java.util.Map[String, AnyRef]): Unit = {
        val contentType = input.get("contentType").asInstanceOf[String]
        println("HierarchyBackwardCompatibilityUtil :: setContentAndCategoryTypes :::: identifier " + input.get("identifier") )
        println("HierarchyBackwardCompatibilityUtil :: setContentAndCategoryTypes :::: contentType " + contentType )
        val primaryCategory = input.get("primaryCategory").asInstanceOf[String]
        println("HierarchyBackwardCompatibilityUtil :: setContentAndCategoryTypes :: primaryCategory " + primaryCategory )
        val (updatedContentType, updatedPrimaryCategory): (String, String) = (contentType, primaryCategory) match {
            case (x: String, y: String) => (x, y)
            case ("Resource", y) => (contentType, getCategoryForResource(input.getOrDefault("mimeType", "application/pdf").asInstanceOf[String]))
            case (x: String, y) => (x, categoryMap.get(x).asInstanceOf[String])
            case (x, y: String) => (categoryMap.asScala.filter(entry => StringUtils.equalsIgnoreCase(entry._2.asInstanceOf[String], y)).keys.headOption.getOrElse(""), y)
            case _ => (contentType, primaryCategory)
        }
        println("HierarchyBackwardCompatibilityUtil :: setContentAndCategoryTypes :: updated CT " + updatedContentType )
        println("HierarchyBackwardCompatibilityUtil :: setContentAndCategoryTypes :: updated PC " + updatedPrimaryCategory )
        input.put("contentType", updatedContentType)
        input.put("primaryCategory", updatedPrimaryCategory)
    }

    private def getCategoryForResource(mimeType:String): String = {
        if(List("video/avi", "video/mpeg", "video/quicktime", "video/3gpp", "video/mp4", "video/ogg", "video/webm", "video/x-youtube").contains(mimeType)) "ExplanationContent"
        if(List("application/pdf", "application/vnd.ekstep.h5p-archive", "application/vnd.ekstep.html-archive").contains(mimeType)) "LearningResource"
        if(List("application/vnd.ekstep.ecml-archive").contains(mimeType)) "LearningResource" else "QuestionSet"
    }

    def setObjectTypeForRead(result: java.util.Map[String, AnyRef]): Unit = {
        result.put("objectType", "Content")
    }

    def setNewObjectType(node: Node) = {
        val metadata = node.getMetadata
        val mimeType = metadata.getOrDefault("mimeType", "").asInstanceOf[String]
        val contentType = metadata.getOrDefault("contentType", "").asInstanceOf[String]
        val objectType = metadata.getOrDefault("objectType", "").asInstanceOf[String]
        val primaryCategory = metadata.getOrDefault("primaryCategory", "").asInstanceOf[String]
        println("HierarchyBackwardCompatibility::setNewObjectType:: mimeType :: " + mimeType + " primaryCategory " + primaryCategory + " contentType " + contentType + " objectType " + objectType)
        if (StringUtils.isNotBlank(mimeType) && StringUtils.equalsIgnoreCase(mimeType, HierarchyConstants.COLLECTION_MIME_TYPE)) {
            metadata.put(HierarchyConstants.OBJECT_TYPE, HierarchyConstants.COLLECTION_OBJECT_TYPE)
            node.setObjectType(HierarchyConstants.COLLECTION_OBJECT_TYPE)
        } else if ((StringUtils.isNotBlank(contentType) && StringUtils.equalsIgnoreCase(contentType, HierarchyConstants.ASSET_CONTENT_TYPE))
            || (StringUtils.isNotBlank(primaryCategory) && StringUtils.equalsIgnoreCase(primaryCategory, HierarchyConstants.ASSET_CONTENT_TYPE))) {
            metadata.put(HierarchyConstants.OBJECT_TYPE, HierarchyConstants.ASSET_OBJECT_TYPE)
            node.setObjectType(HierarchyConstants.ASSET_OBJECT_TYPE)
        } else {
            metadata.put(HierarchyConstants.OBJECT_TYPE, objectType)
        }
    }
}