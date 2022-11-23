package org.sunbird.graph.util

import org.apache.commons.collections4.MapUtils
import org.slf4j.LoggerFactory
import org.sunbird.common.Platform
import org.sunbird.common.dto.Property
import org.sunbird.graph.dac.model.Node

import scala.collection.JavaConverters._

object CSPMetaUtil {
  private[this] val logger = LoggerFactory.getLogger(classOf[CSPMetaUtil])
  private val defaultMetadataList = java.util.Arrays.asList("appIcon","posterImage","artifactUrl","downloadUrl","variants","previewUrl","pdfUrl")
  private val defaultWriteBasePath = java.util.Arrays.asList("https://sunbirddev.blob.core.windows.net","https://obj.dev.sunbird.org")

  def updateAbsolutePath(data: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
    val cspMeta = Platform.getStringList("cloudstorage.metadata.list", defaultMetadataList).asScala.toList
    val absolutePath = Platform.getString("cloudstorage.read_base_path", "") + java.io.File.separator + Platform.getString("cloud_storage_container", "")
    if (MapUtils.isNotEmpty(data)) {
      val updatedMeta: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]
      data.asScala.map(x =>
        if (cspMeta.contains(x._1))
          updatedMeta.put(x._1, x._2.asInstanceOf[String].replace("CLOUD_STORAGE_BASE_PATH", absolutePath))
        else updatedMeta.put(x._1, x._2)
      ).asJava
      updatedMeta
    } else data
  }

  def updateAbsolutePath(node: Node): Node = {
    val metadata = updateAbsolutePath(node.getMetadata)
    node.setMetadata(metadata)
    node
  }

  def updateAbsolutePath(nodes: java.util.List[Node]): java.util.List[Node] = {
    nodes.asScala.toList.map(node => {
      updateAbsolutePath(node)
    }).asJava
  }

  def updateAbsolutePath(property: Property): Property = {
    val cspMeta = Platform.getStringList("cloudstorage.metadata.list", defaultMetadataList)
    val absolutePath = Platform.getString("cloudstorage.read_base_path", "") + java.io.File.separator + Platform.getString("cloud_storage_container", "")
    if(cspMeta.contains(property.getPropertyName)) {
      val value = property.getPropertyValue
      value match {
        case str: String =>
          property.setPropertyValue(str.replace("CLOUD_STORAGE_BASE_PATH", absolutePath))
        case _ =>
      }
    }
    property
  }

  def updateRelativePath(data: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
    val cspMeta: java.util.List[String] = Platform.getStringList("cloudstorage.metadata.list", defaultMetadataList)
    val validCSPSource: java.util.List[String] = Platform.getStringList("cloudstorage.write_base_path", defaultWriteBasePath)
    val basePath: List[String] = validCSPSource.asScala.toList.map(source => source + java.io.File.separator + Platform.getString("cloud_storage_container", ""))
    if (MapUtils.isNotEmpty(data)) {
      val updatedMeta: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]
      data.asScala.map(x =>
          if (cspMeta.contains(x._1)) {
            val filteredPath = basePath.filter(path => x._2.asInstanceOf[String].contains(path))
            updatedMeta.put(x._1, if(filteredPath.nonEmpty) x._2.asInstanceOf[String].replaceAll(filteredPath.head, "CLOUD_STORAGE_BASE_PATH") else x._2)
          } else updatedMeta.put(x._1, x._2)
      ).asJava
      updatedMeta
    } else data
  }

}

class CSPMetaUtil {}
