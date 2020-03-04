package org.sunbird.utils

import java.util
import java.util.UUID

import org.apache.commons.collections4.CollectionUtils
import org.sunbird.common.Platform
import org.apache.commons.lang3.StringUtils.equalsIgnoreCase

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

object copyCollectionOperation {

  def prepareUpdateHierarchyRequest(children: util.List[util.Map[String, AnyRef]], existingId: String, contentType: String, idMap: util.Map[String, String])(implicit ec: ExecutionContext): Future[util.HashMap[String, AnyRef]] = {
    val nodesModified = new util.HashMap[String, AnyRef]
    val hierarchy = new util.HashMap[String, AnyRef]
    val parentHierarchy = new util.HashMap[String, Any]
    parentHierarchy.put("children", new util.ArrayList[AnyRef])
    parentHierarchy.put("root", true)
    parentHierarchy.put("contentType", contentType)
    hierarchy.put(idMap.get(existingId), parentHierarchy)
    populateHierarchy(children, nodesModified, hierarchy, idMap.get(existingId))
    val data = new util.HashMap[String, AnyRef]
    data.put("nodesModified", nodesModified)
    data.put("hierarchy", hierarchy)
    Future(data)
  }

  def populateHierarchy(children: util.List[util.Map[String, AnyRef]], nodesModified: util.Map[String, AnyRef], hierarchy: util.Map[String, AnyRef], parentId: String): Unit = {
    var nullPropList:util.List[String] = null
    if (Platform.config.hasPath("learning.content.copy.props_to_remove"))
      nullPropList =  Platform.config.getStringList("learning.content.copy.props_to_remove")
    if (CollectionUtils.isNotEmpty(children)) {
      for (child <- children.asScala) {
        var id = child.get("identifier").asInstanceOf[String]
        if (equalsIgnoreCase("Parent", child.get("visibility").asInstanceOf[String])) { // NodesModified and hierarchy
          id = UUID.randomUUID.toString
          val metadata = new util.HashMap[String, AnyRef]
          metadata.putAll(child)
          if (CollectionUtils.isNotEmpty(nullPropList))
            nullPropList.asScala.foreach(prop => metadata.remove(prop))
          metadata.put("children", new util.ArrayList[AnyRef])
          metadata.remove("identifier")
          metadata.remove("parent")
          metadata.remove("index")
          metadata.remove("depth")
          val modifiedNode = new util.HashMap[String, Any]
          modifiedNode.put("metadata", metadata)
          modifiedNode.put("root", false)
          modifiedNode.put("isNew", true)
          modifiedNode.put("setDefaultValue", false)
          nodesModified.put(id, modifiedNode)
        }
        val parentHierarchy = new util.HashMap[String, Any]
        parentHierarchy.put("children", new util.ArrayList[util.Map[String,AnyRef]])
        parentHierarchy.put("root", false)
        parentHierarchy.put("contentType", child.get("contentType"))
        hierarchy.put(id, parentHierarchy)
        hierarchy.get(parentId).asInstanceOf[util.Map[String, AnyRef]].get("children").asInstanceOf[util.ArrayList[AnyRef]].add(id)
        populateHierarchy(child.get("children").asInstanceOf[util.List[util.Map[String, AnyRef]]], nodesModified, hierarchy, id)
      }
    }
  }

}
