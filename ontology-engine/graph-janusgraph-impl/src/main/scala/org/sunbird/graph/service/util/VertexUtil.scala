package org.sunbird.graph.service.util

import org.apache.commons.lang3.{BooleanUtils, StringUtils}
import org.apache.tinkerpop.gremlin.driver.Client
import org.sunbird.common.DateUtils
import org.sunbird.graph.util.ScalaJsonUtil
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.common.Identifier
import org.sunbird.graph.common.enums.{AuditProperties, GraphDACParams, SystemProperties}
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.service.common.{DACErrorCodeConstants, DACErrorMessageConstants}

import java.util
import scala.collection.convert.ImplicitConversions.{`collection AsScalaIterable`, `map AsScala`}

object VertexUtil {

  def createVertexQuery(parameterMap: util.Map[String, AnyRef]): String = {
    val query: StringBuilder = new StringBuilder()
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
      val templateQuery: StringBuilder = new StringBuilder()
      val paramValueMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]

      paramValueMap.putAll(getMetadataQueryMap(node))
      paramValueMap.putAll(getSystemPropertyMap(node, date))
      paramValueMap.putAll(getAuditPropertyMap(node, date, false))
      paramValueMap.putAll(getVersionPropertyMap(node, date))

      templateQuery.append("g.addV('")
        .append(node.getGraphId)
        .append("').")
      appendProperties(templateQuery, paramValueMap)
      templateQuery.delete(templateQuery.length() - 1, templateQuery.length())

      parameterMap.put(GraphDACParams.query.name, templateQuery.toString)
      parameterMap.put(GraphDACParams.paramValueMap.name, prepareParamValueMap(paramValueMap))

    }
    query.toString()
  }

  def upsertVertexQuery(parameterMap: util.Map[String, AnyRef]): String = {
    val query: StringBuilder = new StringBuilder()
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
      val templateQuery: StringBuilder = new StringBuilder()
      val paramValueMap: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]

      templateQuery.append("g.V().has(")
        .append(getMatchCriteriaString(graphId, node))
        .append(")")
        .append(".fold()")
        .append(".coalesce(")
        .append("unfold(), addV('")
        .append(graphId)
        .append("').")

      appendProperties(templateQuery, ocsMap)
      templateQuery.delete(templateQuery.length() - 1, templateQuery.length())
      templateQuery.append(").sideEffect(")
      appendProperties(templateQuery, omsMap)

      templateQuery.delete(templateQuery.length() - 1, templateQuery.length())
      templateQuery.append(").next()")

      paramValueMap.putAll(ocsMap)
      paramValueMap.putAll(omsMap)

      parameterMap.put(GraphDACParams.query.name, templateQuery.toString)
      parameterMap.put(GraphDACParams.paramValueMap.name, prepareParamValueMap(paramValueMap))

    }
    query.toString()
  }

  def upsertRootVertexQuery(parameterMap: util.Map[String, AnyRef]): String = {
    val templateQuery: StringBuilder = new StringBuilder()

    if (null != parameterMap) {
      val rootNode = parameterMap.get(GraphDACParams.rootNode.name).asInstanceOf[Node]

      if (null == rootNode)
        throw new ClientException(DACErrorCodeConstants.INVALID_NODE.name(),
          DACErrorMessageConstants.INVALID_ROOT_NODE + " | [Create Root Node Query Generation Failed.]")

      val graphId = parameterMap.getOrDefault(GraphDACParams.graphId.name, "").asInstanceOf[String]
      val date: String = DateUtils.formatCurrentDate

      templateQuery.append("g.V().has(")
        .append(getMatchCriteriaString(graphId, rootNode))
        .append(")")
        .append(".fold()")
        .append(".coalesce(")
        .append("unfold(), addV('")
        .append(graphId)
        .append("')")
        .append(getOnCreateSetString(date, rootNode))
        .append(").next()")

    }
    templateQuery.toString()
  }

  def upsertVerticesQuery(graphId: String, identifiers: util.List[String], metadata: util.Map[String, AnyRef], params: util.Map[String, AnyRef]): String = {
    val templateQuery: StringBuilder = new StringBuilder()

    templateQuery.append("g.V().hasLabel('")
      .append(graphId)
      .append("').has('")
      .append(SystemProperties.IL_UNIQUE_ID.name)
      .append("', within(")

    templateQuery.append(identifiers.map(id => s"'$id'").mkString(","))
    templateQuery.append(")).")

    appendProperties(templateQuery, metadata)
    templateQuery.delete(templateQuery.length() - 1, templateQuery.length())
    templateQuery.append(".toList()")
    params.putAll(prepareParamValueMap(metadata))
    templateQuery.toString()
  }

  private def appendProperties(templateQuery: StringBuilder, metadata: util.Map[String, AnyRef]): Unit = {
    metadata.foreach { case (key, _) =>
      templateQuery.append("property('")
        .append(key)
        .append("', ")
        .append(key)
        .append(").")
    }
  }

  def prepareParamValueMap(paramValueMap: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    paramValueMap.foreach { case (key, value) =>
      if (value.isInstanceOf[util.List[_]]) {
        paramValueMap.put(key, ScalaJsonUtil.serialize(value))
      }
    }
    paramValueMap
  }

  private def getMatchCriteriaString(graphId: String, node: Node): String = {
    var matchCriteria :String = ""
		if (StringUtils.isNotBlank(graphId) && null != node) {
			if (StringUtils.isBlank(node.getIdentifier))
				node.setIdentifier(Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp))
			if (StringUtils.isBlank(node.getGraphId))
				node.setGraphId(graphId);
    matchCriteria = "'" + graphId + "', '" + SystemProperties.IL_UNIQUE_ID.name + "', '" + node.getIdentifier + "'"
    }
    matchCriteria
  }

  private def getOnCreateSetString(date: String, node: Node): String = {
    val query: StringBuilder = new StringBuilder()

    if (null != node && StringUtils.isNotBlank(date)) {
      if (StringUtils.isBlank(node.getIdentifier)) {
        node.setIdentifier(Identifier.getIdentifier(node.getGraphId, Identifier.getUniqueIdFromTimestamp))
      }
      query.append(".property('").append(SystemProperties.IL_UNIQUE_ID.name)
        .append("'").append(",'").append(node.getIdentifier).append("')")

      query.append(".property('").append(SystemProperties.IL_SYS_NODE_TYPE.name)
        .append("'").append(",'").append(node.getNodeType).append("')")

      if (StringUtils.isNotBlank(node.getObjectType)) {
        query.append(".property('").append(SystemProperties.IL_SYS_NODE_TYPE.name)
          .append("'").append(",'").append(node.getNodeType).append("')")
      }

      query.append(".property('").append(AuditProperties.createdOn.name)
        .append("'").append(",'").append(date).append("')")

      if (null != node.getMetadata &&
        null == node.getMetadata.getOrDefault(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name, ""))
        {
          query.append(".property('").append(AuditProperties.lastUpdatedOn.name)
            .append("'").append(",'").append(date).append("')")
        }

      query.append(".property('").append(GraphDACParams.versionKey.name)
        .append("'").append(",'").append(DateUtils.parse(date).getTime.toString).append("')")
    }
    query.toString()
  }

  def deleteVertexQuery(parameterMap: util.Map[String, AnyRef]): String = {
    val templateQuery: StringBuilder = new StringBuilder()
    if (null != parameterMap) {
      val graphId = parameterMap.get(GraphDACParams.graphId.name).asInstanceOf[String]
      if (StringUtils.isBlank(graphId))
        throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name,
          DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Remove Property Values Query Generation Failed.]")

      val nodeId = parameterMap.get(GraphDACParams.nodeId.name).asInstanceOf[String]
      if (StringUtils.isBlank(nodeId))
        throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name,
          DACErrorMessageConstants.INVALID_IDENTIFIER + " | [Remove Property Values Query Generation Failed.]")

      templateQuery.append("g.V().has('")
        .append(graphId)
        .append("','")
        .append(SystemProperties.IL_UNIQUE_ID.name)
        .append("','")
        .append(nodeId)
        .append("')")
        .append(".fold().coalesce(unfold(), constant(false)).drop().constant(true)")

    }
    templateQuery.toString()
  }

  def getMetadataQueryMap(node: Node): util.Map[String, AnyRef] = {
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

    if (null != node && StringUtils.isNotBlank(date)) {
      if (StringUtils.isBlank(node.getIdentifier)) {
        node.setIdentifier(Identifier.getIdentifier(node.getGraphId, Identifier.getUniqueIdFromTimestamp))
      }

      paramMap.put(SystemProperties.IL_UNIQUE_ID.name, node.getIdentifier)
      paramMap.put(SystemProperties.IL_SYS_NODE_TYPE.name, node.getNodeType)

      if (StringUtils.isNotBlank(node.getObjectType)) {
        paramMap.put(SystemProperties.IL_FUNC_OBJECT_TYPE.name, node.getObjectType)
      }
      paramMap.put(AuditProperties.createdOn.name, date)

      if (null != node.getMetadata && null != node.getMetadata.get(GraphDACParams.SYS_INTERNAL_LAST_UPDATED_ON.name)) {
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
        StringUtils.isBlank(node.getMetadata.getOrDefault(GraphDACParams.versionKey.name(), "").toString)) {
        val versionKey = DateUtils.parse(date).getTime.toString
        paramMap.put(GraphDACParams.versionKey.name, versionKey)
      }
    }
    paramMap
  }



}
