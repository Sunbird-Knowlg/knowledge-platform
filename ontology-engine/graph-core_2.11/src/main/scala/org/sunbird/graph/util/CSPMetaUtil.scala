package org.sunbird.graph.util

import java.util

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.sunbird.common.dto.Property
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.graph.dac.model.Node

import scala.collection.JavaConverters._
import scala.collection.immutable.Map

object CSPMetaUtil {
  private[this] val logger = LoggerFactory.getLogger(classOf[CSPMetaUtil])

  def updateAbsolutePath(data: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
    logger.info("CSPMetaUtil ::: updateAbsolutePath util.Map[String, AnyRef] ::: data before url replace :: " + data)
    val relativePathPrefix: String = Platform.getString("cloudstorage.relative_path_prefix", "")
    val cspMeta = Platform.getStringList("cloudstorage.metadata.list", new java.util.ArrayList[String]()).asScala.toList
    val absolutePath = Platform.getString("cloudstorage.read_base_path", "") + java.io.File.separator + Platform.getString("cloud_storage_container", "")
    val returnData = if (MapUtils.isNotEmpty(data)) {
      val updatedMeta: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]
      data.asScala.map(x =>
        if (cspMeta.contains(x._1))
          updatedMeta.put(x._1, x._2.asInstanceOf[String].replace(relativePathPrefix, absolutePath))
        else updatedMeta.put(x._1, x._2)
      ).asJava
      updatedMeta
    } else data
    logger.info("CSPMetaUtil ::: updateAbsolutePath util.Map[String, AnyRef] ::: updateAbsolutePath returnData :: " + returnData)
    returnData
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
    val relativePathPrefix: String = Platform.getString("cloudstorage.relative_path_prefix", "")
    val cspMeta = Platform.getStringList("cloudstorage.metadata.list", new java.util.ArrayList[String]())
    val absolutePath = Platform.getString("cloudstorage.read_base_path", "") + java.io.File.separator + Platform.getString("cloud_storage_container", "")
    if(cspMeta.contains(property.getPropertyName)) {
      val value = property.getPropertyValue
      value match {
        case str: String =>
          property.setPropertyValue(str.replace(relativePathPrefix, absolutePath))
        case _ =>
      }
    }
    property
  }

  def updateRelativePath(data: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
    logger.info("CSPMetaUtil ::: updateRelativePath util.Map[String, AnyRef] ::: data before url replace :: " + data)
    val relativePathPrefix: String = Platform.getString("cloudstorage.relative_path_prefix", "")
    val cspMeta: java.util.List[String] = Platform.getStringList("cloudstorage.metadata.list", new java.util.ArrayList[String]())
    val validCSPSource: List[String] = Platform.getStringList("cloudstorage.write_base_path", new java.util.ArrayList[String]()).asScala.toList
    val basePaths: Array[String] = validCSPSource.map(source => source + java.io.File.separator + Platform.getString("cloud_storage_container", "")).toArray
    val repArray = getReplacementData(basePaths, relativePathPrefix)
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
    val relativePathPrefix: String = Platform.getString("cloudstorage.relative_path_prefix", "")
    val validCSPSource: List[String] = Platform.getStringList("cloudstorage.write_base_path", new java.util.ArrayList[String]()).asScala.toList
    val basePaths: Array[String] = validCSPSource.map(source => source + java.io.File.separator + Platform.getString("cloud_storage_container", "")).toArray
    val repArray = getReplacementData(basePaths, relativePathPrefix)

    val updatedObjString = StringUtils.replaceEach(JsonUtils.serialize(data), basePaths, repArray)
    val updatedData = JsonUtils.deserialize(updatedObjString, classOf[java.util.Map[String, AnyRef]])

    logger.info("CSPMetaUtil ::: saveExternalRelativePath util.Map[String, AnyRef] ::: data after url replace :: " + updatedData)
    updatedData
  }

  def updateExternalRelativePath(data: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
    logger.info("CSPMetaUtil ::: updateExternalRelativePath util.Map[String, AnyRef] ::: data before url replace :: " + data)
    val relativePathPrefix: String = Platform.getString("cloudstorage.relative_path_prefix", "")
    val validCSPSource: List[String] = Platform.getStringList("cloudstorage.write_base_path", new java.util.ArrayList[String]()).asScala.toList
    val basePaths: Array[String] = validCSPSource.map(source => source + java.io.File.separator + Platform.getString("cloud_storage_container", "")).toArray
    val repArray = getReplacementData(basePaths, relativePathPrefix)
    val values = data.get("values")
    val updatedValues = values match {
      case x: List[AnyRef] => x.map(value => getBasePath("", value, basePaths, repArray))
      case _ => values
    }
    data.put("values", updatedValues)
    logger.info("CSPMetaUtil ::: updateExternalRelativePath util.Map[String, AnyRef] ::: data after url replace :: " + data)
    data
  }

  def updateExternalAbsolutePath(data: java.util.Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
    //No need to check the metadata fields because that will be taken care while writing data.
    logger.info("CSPMetaUtil ::: updateExternalAbsolutePath util.Map[String, AnyRef] ::: data before url replace :: " + data)
    val relativePathPrefix: String = Platform.getString("cloudstorage.relative_path_prefix", "")
    //Not Implemented logic based on external field key, because while writing data it is not considered.
    //val extFieldList = Platform.getStringList("cloudstorage.external_field_list", new java.util.ArrayList[String]()).asScala.toList
    val absolutePath = Platform.getString("cloudstorage.read_base_path", "") + java.io.File.separator + Platform.getString("cloud_storage_container", "")
    val returnData = if (MapUtils.isNotEmpty(data)) {
      val updatedMeta: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]
      data.asScala.map(x => updatedMeta.put(x._1, getBasePath(x._1, x._2, Array(relativePathPrefix), Array(absolutePath)))
      ).asJava
      updatedMeta
    } else data
    logger.info("CSPMetaUtil ::: updateExternalAbsolutePath util.Map[String, AnyRef] ::: data before url replace :: " + returnData)
    returnData
  }

  private def getBasePath(key: String, value: AnyRef, oldPath: Array[String], newPath: Array[String]): AnyRef = {
    logger.info(s"CSPMetaUtil ::: getBasePath ::: Updating Path for Key : $key & Value : $value")
    val res = if (null != value) {
      value match {
        case p: String => if (StringUtils.isNotBlank(p)) StringUtils.replaceEach(p, oldPath, newPath) else p
        case q: Map[String, AnyRef] => {
          val updatedObjString = StringUtils.replaceEach(ScalaJsonUtil.serialize(q), oldPath, newPath)
          val updatedData = ScalaJsonUtil.deserialize[Map[String, AnyRef]](updatedObjString)
          updatedData
        }
        case r: java.util.Map[String, AnyRef] => {
          val updatedObjString = StringUtils.replaceEach(JsonUtils.serialize(r), oldPath, newPath)
          val updatedData = JsonUtils.deserialize(updatedObjString, classOf[java.util.Map[String, AnyRef]])
          updatedData
        }
        case s: util.List[AnyRef] => {
          val updatedObjString = StringUtils.replaceEach(JsonUtils.serialize(s), oldPath, newPath)
          val updatedData = JsonUtils.deserialize(updatedObjString, classOf[java.util.List[AnyRef]])
          updatedData
        }
        case t: List[AnyRef] => {
          val updatedObjString = StringUtils.replaceEach(ScalaJsonUtil.serialize(t), oldPath, newPath)
          val updatedData = ScalaJsonUtil.deserialize[List[AnyRef]](updatedObjString)
          updatedData
        }
        case _ => value
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
