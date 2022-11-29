package org.sunbird.graph.util

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.sunbird.common.dto.Property
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.dac.model.Node

import scala.collection.JavaConverters._

object CSPMetaUtil {
  private[this] val logger = LoggerFactory.getLogger(classOf[CSPMetaUtil])

  def updateAbsolutePath(data: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
    val cspMeta = Platform.getStringList("cloudstorage.metadata.list", new java.util.ArrayList[String]()).asScala.toList
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
    val cspMeta = Platform.getStringList("cloudstorage.metadata.list", new java.util.ArrayList[String]())
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
    logger.info("CSPMetaUtil ::: updateRelativePath util.Map[String, AnyRef] ::: data before url replace :: " + data)
    val cspMeta: java.util.List[String] = Platform.getStringList("cloudstorage.metadata.list", new java.util.ArrayList[String]())
    val validCSPSource: List[String] = Platform.getStringList("cloudstorage.write_base_path", new java.util.ArrayList[String]()).asScala.toList
    val basePaths: Array[String] = validCSPSource.map(source => source + java.io.File.separator + Platform.getString("cloud_storage_container", "")).toArray
    val repArray = getReplacementData(basePaths, "CLOUD_STORAGE_BASE_PATH")
    val result = if (MapUtils.isNotEmpty(data)) {
      val updatedMeta: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]
      data.asScala.map(x =>
        if (cspMeta.contains(x._1))
          updatedMeta.put(x._1, getBasePath(x._1, x._2, basePaths, repArray))
        else updatedMeta.put(x._1, x._2)
      ).asJava
      updatedMeta
    } else data
    logger.info("CSPMetaUtil ::: updateRelativePath util.Map[String, AnyRef] ::: data after url replace :: " + result)
    result
  }

  def saveExternalRelativePath(data: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
    logger.info("CSPMetaUtil ::: saveExternalRelativePath util.Map[String, AnyRef] ::: data before url replace :: " + data)
    val validCSPSource: List[String] = Platform.getStringList("cloudstorage.write_base_path", new java.util.ArrayList[String]()).asScala.toList
    val basePaths: Array[String] = validCSPSource.map(source => source + java.io.File.separator + Platform.getString("cloud_storage_container", "")).toArray
    val repArray = getReplacementData(basePaths, "CLOUD_STORAGE_BASE_PATH")

    val updatedData: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]
    data.asScala.map(field => {
      updatedData.put(field._1, StringUtils.replaceEach(field._2.asInstanceOf[String], basePaths, repArray).asInstanceOf[AnyRef])
    }).asJava

    logger.info("CSPMetaUtil ::: saveExternalRelativePath util.Map[String, AnyRef] ::: data after url replace :: " + updatedData)
    updatedData
  }

  def updateExternalRelativePath(data: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
    logger.info("CSPMetaUtil ::: updateExternalRelativePath util.Map[String, AnyRef] ::: data before url replace :: " + data)

    val values = data.get("values")
    val updatedValues = values match {
      case x: List[AnyRef] => {
        val validCSPSource: List[String] = Platform.getStringList("cloudstorage.write_base_path", new java.util.ArrayList[String]()).asScala.toList
        val basePaths: Array[String] = validCSPSource.map(source => source + java.io.File.separator + Platform.getString("cloud_storage_container", "")).toArray
        val repArray = getReplacementData(basePaths, "CLOUD_STORAGE_BASE_PATH")

        x.map(value => StringUtils.replaceEach(value.asInstanceOf[String], basePaths, repArray))
      }
      case _ => values
    }

    data.put("values", updatedValues)
    logger.info("CSPMetaUtil ::: updateExternalRelativePath util.Map[String, AnyRef] ::: data after url replace :: " + data)
    data
  }

  private def getBasePath(key: String, value: AnyRef, oldPath: Array[String], newPath: Array[String]): AnyRef = {
    logger.info(s"CSPMetaUtil ::: getBasePath ::: Updating Path for Key : $key & Value : $value")
    val res = if (null != value) {
      value match {
        case x: String => if (StringUtils.isNotBlank(x)) StringUtils.replaceEach(x, oldPath, newPath) else x
        case y: Map[String, AnyRef] => {
          val dStr = JsonUtils.serialize(y)
          val result = StringUtils.replaceEach(dStr, oldPath, newPath)
          val output: Map[String, AnyRef] = JsonUtils.deserialize(result, classOf[Map[String, AnyRef]])
          output
        }
        case z: java.util.Map[String, AnyRef] => {
          val dStr = JsonUtils.serialize(z)
          val result = StringUtils.replaceEach(dStr, oldPath, newPath)
          val output: java.util.Map[String, AnyRef] = JsonUtils.deserialize(result, classOf[java.util.Map[String, AnyRef]])
          output
        }
      }
    } else value
    logger.info(s"CSPMetaUtil ::: getBasePath ::: Updated Path for Key : $key & Updated Value is : $res")
    res
  }

  private def getReplacementData(oldPath: Array[String], repStr: String): Array[String] = {
    val repArray = new Array[String](oldPath.length)
    for (i <- oldPath.indices) {
      repArray(i) = repStr
    }
    repArray
  }

}

class CSPMetaUtil {}
