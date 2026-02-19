package org.sunbird.utils.content

import java.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.dac.model.Node
import scala.jdk.CollectionConverters._


object HierarchyBackwardCompatibilityUtil {

    val categoryMap: java.util.Map[String, AnyRef] = Platform.getAnyRef("contentTypeToPrimaryCategory",
        new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
    val categoryMapForMimeType: java.util.Map[String, AnyRef] = Platform.getAnyRef("mimeTypeToPrimaryCategory",
        new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
    val categoryMapForResourceType: java.util.Map[String, AnyRef] = Platform.getAnyRef("resourceTypeToPrimaryCategory",
        new util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
    val mimeTypesToCheck = List("application/vnd.ekstep.h5p-archive", "application/vnd.ekstep.html-archive", "application/vnd.android.package-archive",
        "video/webm", "video/x-youtube", "video/mp4")
    val objectTypes = List("Content", "Collection")

    private def getStringValue(input: util.Map[String, AnyRef], key: String): String = {
        input.get(key) match {
            case s: String => s
            case l: java.util.List[_] => if (l.isEmpty) "" else l.get(0).toString
            case _ => ""
        }
    }

    def setContentAndCategoryTypes(input: util.Map[String, AnyRef], objType: String = ""): Unit = {
        if(StringUtils.isBlank(objType) || objectTypes.contains(objType)) {
            val contentType = getStringValue(input, "contentType")
            val primaryCategory = getStringValue(input, "primaryCategory")

            val (updatedContentType, updatedPrimaryCategory): (String, String) = (contentType, primaryCategory) match {
                case (x: String, y: String) => (x, y)
                case ("Resource", y) => (contentType, getCategoryForResource(input.getOrDefault("mimeType", "").asInstanceOf[String],
                    input.getOrDefault("resourceType", "").asInstanceOf[String]))
                case (x: String, y) => (x, categoryMap.get(x).asInstanceOf[String])
                case (x, y: String) => (categoryMap.asScala.filter(entry => StringUtils.equalsIgnoreCase(entry._2.asInstanceOf[String], y)).keys.headOption.getOrElse(""), y)
                case _ => (contentType, primaryCategory)
            }

            input.put("contentType", updatedContentType)
            input.put("primaryCategory", updatedPrimaryCategory)
        }
    }

    private def getCategoryForResource(mimeType: String, resourceType: String): String = (mimeType, resourceType) match {
        case ("", "") => "Learning Resource"
        case (x: String, "") => categoryMapForMimeType.get(x).asInstanceOf[util.List[String]].asScala.headOption.getOrElse("Learning Resource")
        case (x: String, y: String) => if (mimeTypesToCheck.contains(x)) categoryMapForMimeType.get(x).asInstanceOf[util.List[String]].asScala.headOption.getOrElse("Learning Resource") else categoryMapForResourceType.getOrDefault(y, "Learning Resource").asInstanceOf[String]
        case _ => "Learning Resource"
    }
    def setObjectTypeForRead(result: java.util.Map[String, AnyRef], objectType: String = ""): Unit = {
        if(objectTypes.contains(objectType))
            result.put("objectType", "Content")
    }

    def setNewObjectType(node: Node) = {
        val metadata = node.getMetadata
        val mimeType = metadata.getOrDefault("mimeType", "").asInstanceOf[String]
        val contentType = metadata.getOrDefault("contentType", "").asInstanceOf[String]
        val objectType = metadata.getOrDefault("objectType", "").asInstanceOf[String]
        val primaryCategory = metadata.getOrDefault("primaryCategory", "").asInstanceOf[String]

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

    def deserializeStringifiedLists(input: java.util.Map[String, AnyRef]): Unit = {
        val keys = input.keySet().asScala.toList
        keys.foreach(key => {
            val value = input.get(key)
            if(value.isInstanceOf[String]) {
                val strValue = value.asInstanceOf[String]
                if(strValue.startsWith("[") && strValue.endsWith("]")) {
                    try {
                        val list = JsonUtils.deserialize(strValue, classOf[java.util.List[String]])
                        input.put(key, list)
                    } catch {
                        case e: Exception => // ignore
                    }
                }
            }
        })
    }
}