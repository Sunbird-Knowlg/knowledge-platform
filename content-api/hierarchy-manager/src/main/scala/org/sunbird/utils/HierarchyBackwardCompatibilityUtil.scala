package org.sunbird.utils

import java.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform

import scala.collection.JavaConverters._
object HierarchyBackwardCompatibilityUtil {

    val categoryMap: java.util.Map[String, AnyRef] = Platform.getAnyRef("contentTypeToPrimaryCategory",
        new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]

    def setContentAndCategoryTypes(input: java.util.Map[String, AnyRef]): Unit = {
        val contentType = input.get("contentType").asInstanceOf[String]
        val primaryCategory = input.get("primaryCategory").asInstanceOf[String]
        val (updatedContentType, updatedPrimaryCategory): (String, String) = (contentType, primaryCategory) match {
            case (x: String, y: String) => (x, y)
            case ("Resource", y) => (contentType, getCategoryForResource(input.getOrDefault("mimeType", "application/pdf").asInstanceOf[String]))
            case("Asset", y) => (contentType, "Asset")
            case (x: String, y) => (x, categoryMap.get(x).asInstanceOf[String])
            case (x, y: String) => (categoryMap.asScala.filter(entry => StringUtils.equalsIgnoreCase(entry._2.asInstanceOf[String], y)).keys.headOption.getOrElse(""), y)
            case _ => (contentType, primaryCategory)
        }
        input.put("contentType", updatedContentType)
        input.put("primaryCategory", updatedPrimaryCategory)
    }

    private def getCategoryForResource(mimeType:String): String = {
        if(List("video/avi", "video/mpeg", "video/quicktime", "video/3gpp", "video/mp4", "video/ogg", "video/webm", "video/x-youtube").contains(mimeType)) "ExplanationContent"
        if(List("application/pdf", "application/vnd.ekstep.h5p-archive", "application/vnd.ekstep.html-archive").contains(mimeType)) "LearningResource"
        if(List("application/vnd.ekstep.ecml-archive").contains(mimeType)) "LearningResource" else "QuestionSet"
    }

    def setObjectTypeForRead(objectType: String, result: java.util.Map[String, AnyRef]): Unit = {
        val updatedObjectType = objectType match {
            case "Asset" => "Content"
            case "Collection" => "Content"
            case _ => "Content"
        }
        result.put("objectType", updatedObjectType)
    }

}
