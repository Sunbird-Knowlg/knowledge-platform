package org.sunbird.janus.service.util

import org.apache.commons.lang3.{BooleanUtils, StringUtils}
import org.apache.tinkerpop.gremlin.driver.Client
import org.sunbird.common.DateUtils
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.common.enums.{AuditProperties, GraphDACParams, SystemProperties}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.service.common.{DACErrorCodeConstants, DACErrorMessageConstants}

import java.util
import scala.collection.convert.ImplicitConversions.{`collection AsScalaIterable`, `map AsJavaMap`, `map AsScala`}

object VertexUtil {

  def createVertexQuery(parameterMap: util.Map[String, AnyRef]): String = {
    val templateQuery: StringBuilder = new StringBuilder()
    if (null != parameterMap) {
      val graphId = parameterMap.getOrDefault(GraphDACParams.graphId.name, "").asInstanceOf[String]
      val node = parameterMap.getOrDefault(GraphDACParams.node.name, null).asInstanceOf[Node]

      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Node' Query Generation Failed.]")

      if (null == node)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Create Node Query Generation Failed.]")

      val date: String = DateUtils.formatCurrentDate

      val finalMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
      finalMap.putAll(getMetadataCypherQueryMap(node))
      finalMap.putAll(getSystemPropertyMap(node, date))
      finalMap.putAll(getAuditPropertyMap(node, date, false))
      finalMap.putAll(getVersionPropertyMap(node, date))

      templateQuery.append("g.addV('")
        .append(node.getGraphId)
        .append("')")

      finalMap.foreach { case (key, value) =>
        templateQuery.append(".property('")
          .append(key)
          .append("', '")
          .append(value)
          .append("')")
      }
    }
    templateQuery.toString()
  }

  def updateVertexQuery(parameterMap: util.Map[String, AnyRef]): String = {
    val templateQuery: StringBuilder = new StringBuilder()
    if (null != parameterMap) {
      val node = parameterMap.getOrDefault(GraphDACParams.node.name, null).asInstanceOf[Node]

      if (null == node)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name,
          DACErrorMessageConstants.INVALID_NODE + " | [Upsert Node Query Generation Failed.]")

      if (StringUtils.isBlank(node.getIdentifier))
        node.setIdentifier(Identifier.getIdentifier(node.getGraphId, Identifier.getUniqueIdFromTimestamp))

      val graphId =  parameterMap.getOrDefault(GraphDACParams.graphId.name, null).asInstanceOf[String]
      val date: String = DateUtils.formatCurrentDate

      val ocsMap: util.Map[String, AnyRef] = getOnCreateSetMap(node, date)
      val omsMap: util.Map[String, AnyRef] = getOnMatchSetMap(node, date, merge = true)
      val finalMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
      finalMap.putAll(ocsMap)
      finalMap.putAll(omsMap)

      templateQuery.append("g.V().has('")
        .append(graphId)
        .append("','")
        .append(SystemProperties.IL_UNIQUE_ID.name)
        .append("','")
        .append(node.getIdentifier)
        .append("')")

      finalMap.foreach { case (key, value) =>
        templateQuery.append(".property('")
          .append(key)
          .append("', '")
          .append(value)
          .append("')")
      }
    }
    templateQuery.toString()
  }

  def getMetadataCypherQueryMap(node: Node): util.Map[String, AnyRef] = {
    val metadataPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && null != node.getMetadata && !node.getMetadata.isEmpty) {
      node.getMetadata.foreach { case (key, value) => metadataPropertyMap.put(key, value) }
    }
    metadataPropertyMap
  }

  def getSystemPropertyMap(node: Node, date: String): util.Map[String, AnyRef] = {
    val systemPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && StringUtils.isNotBlank(date)) {
      if (StringUtils.isBlank(node.getIdentifier))
        node.setIdentifier(Identifier.getIdentifier(node.getGraphId, Identifier.getUniqueIdFromTimestamp))
      systemPropertyMap.put(SystemProperties.IL_UNIQUE_ID.name, node.getIdentifier)
      systemPropertyMap.put(SystemProperties.IL_SYS_NODE_TYPE.name, node.getNodeType)
      systemPropertyMap.put(SystemProperties.IL_FUNC_OBJECT_TYPE.name, node.getObjectType)
    }
    systemPropertyMap
  }

  def getAuditPropertyMap(node: Node, date: String, isUpdateOnly: Boolean): util.Map[String, AnyRef] = {
    val auditPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && StringUtils.isNotBlank(date)) {
      if (BooleanUtils.isFalse(isUpdateOnly)) {
        auditPropertyMap.put(AuditProperties.createdOn.name,
          if (node.getMetadata.containsKey(AuditProperties.createdOn.name))
            node.getMetadata.get(AuditProperties.createdOn.name)
          else date)
      }
      if (null != node.getMetadata && null == node.getMetadata.get(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name))
        auditPropertyMap.put(AuditProperties.lastUpdatedOn.name, date)
    }
    auditPropertyMap
  }

  def getVersionPropertyMap(node: Node, date: String): util.Map[String, AnyRef] = {
    val versionPropertyMap = new util.HashMap[String, AnyRef]
    if (null != node && StringUtils.isNotBlank(date))
      versionPropertyMap.put(GraphDACParams.versionKey.name, DateUtils.parse(date).getTime.toString)
    versionPropertyMap
  }

  def getElementMap(id: AnyRef, client: Client): util.Map[AnyRef, AnyRef] = {
    val query = s"g.V($id).elementMap()"
    val resultSet = client.submit(query)
    val result = resultSet.one()
    val elementMap = result.getObject.asInstanceOf[util.Map[AnyRef, AnyRef]]
    elementMap
  }

  private def getOnCreateSetMap(node: Node, date: String): util.Map[String, Object] = {
    val paramMap = new util.HashMap[String, Object]()

    if (node != null && StringUtils.isNotBlank(date)) {
      if (StringUtils.isBlank(node.getIdentifier)) {
        node.setIdentifier(Identifier.getIdentifier(node.getGraphId, Identifier.getUniqueIdFromTimestamp))
      }

      paramMap.put(SystemProperties.IL_UNIQUE_ID.name, node.getIdentifier)
      paramMap.put(SystemProperties.IL_SYS_NODE_TYPE.name, node.getNodeType)

      if (StringUtils.isNotBlank(node.getObjectType)) {
        paramMap.put(SystemProperties.IL_FUNC_OBJECT_TYPE.name, node.getObjectType)
      }
      paramMap.put(AuditProperties.createdOn.name, date)

      if (!node.getMetadata.containsKey(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name)) {
        paramMap.put(AuditProperties.lastUpdatedOn.name, date)
      }
      val versionKey = DateUtils.parse(date).getTime.toString
      paramMap.put(GraphDACParams.versionKey.name, versionKey)
    }

    paramMap
  }

  private def getOnMatchSetMap(node: Node, date: String, merge: Boolean): util.Map[String, Object] = {
    val paramMap = new util.HashMap[String, Object]()
    if (node != null && StringUtils.isNotBlank(date)) {
      if (node.getMetadata != null) {
        node.getMetadata.foreach {
          case (key, value) => if (key != GraphDACParams.versionKey.name) paramMap.put(key, value)
        }
      }
      if (!node.getMetadata.containsKey(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name)) {
        paramMap.put(AuditProperties.lastUpdatedOn.name, date)
      }
      if (node.getMetadata != null &&
        StringUtils.isBlank(node.getMetadata.get(GraphDACParams.versionKey.name()).toString)) {
        val versionKey = DateUtils.parse(date).getTime.toString
        paramMap.put(GraphDACParams.versionKey.name, versionKey)
      }
    }
    paramMap
  }



}
