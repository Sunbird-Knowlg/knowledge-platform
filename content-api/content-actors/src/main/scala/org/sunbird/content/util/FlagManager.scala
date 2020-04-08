package org.sunbird.content.util

import java.util
import java.util.concurrent.CompletionException
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{DateUtils, JsonUtils}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.HierarchyConstants
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

object FlagManager {
  private val FLAGGABLE_STATUS: util.List[String] = util.Arrays.asList("Live", "Unlisted", "Flagged")
  private val COLLECTION_SCHEMA_NAME = "collection"
  private val COLLECTION_CACHE_KEY_PREFIX = "hierarchy_"

  def flag(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {

    DataNode.read(request).map(node => {
      val flagReasons: util.List[String] = request.get("flagReasons").asInstanceOf[util.ArrayList[String]]
      val flaggedBy: String = request.get("flaggedBy").asInstanceOf[String]
      val flags: util.List[String] = request.get("flags").asInstanceOf[util.ArrayList[String]]
      
      val metadata: util.Map[String, Object] = NodeUtil.serialize(node, null, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])//node.getMetadata.asInstanceOf[util.HashMap[String, Object]]
      val status: String = metadata.get("status").asInstanceOf[String]
      val versionKey = node.getMetadata.get("versionKey").asInstanceOf[String]
      request.put("identifier", node.getIdentifier)

      if (!FLAGGABLE_STATUS.contains(status))
        throw new ClientException("ERR_CONTENT_NOT_FLAGGABLE", "Unpublished Content " + node.getIdentifier + " cannot be flagged")

      val flaggedByList: util.List[String] = if(StringUtils.isNotBlank(flaggedBy)) util.Arrays.asList(flaggedBy) else new util.ArrayList[String]
      if (StringUtils.isNotEmpty(flaggedBy))
        request.put("flaggedBy", addDataIntoList(flaggedByList, metadata, "flaggedBy"))
      request.put("lastUpdatedBy", flaggedBy)
      request.put("flags", flags)
      request.put("status", "Flagged")
      request.put("lastFlaggedOn", DateUtils.formatCurrentDate())
      if (CollectionUtils.isNotEmpty(flagReasons))
        request.put("flagReasons", addDataIntoList(flagReasons, metadata, "flagReasons"))
      request.getContext.put("versioning", "disable")
      request.put("versionkey", versionKey)
      updateContentFlag(node, request).map(flaggedNode => {
        val response = ResponseHandler.OK
        val identifier: String = flaggedNode.getIdentifier
        response.put("node_id", identifier)
        response.put("identifier", identifier)
        response.put("versionKey", flaggedNode.getMetadata.get("versionKey"))
        response
      })
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
  }

  def updateCollection(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] = {
    fetchHierarchy(request).map(hierarchyString => {
      if (!hierarchyString.asInstanceOf[String].isEmpty) {
        val hierarchyMap = JsonUtils.deserialize(hierarchyString.asInstanceOf[String], classOf[util.HashMap[String, AnyRef]])
        hierarchyMap.put("lastUpdatedBy", request.get("flaggedBy"))
        hierarchyMap.put("flaggedBy", request.get("flaggedBy"))
        hierarchyMap.put("flags", request.get("flags"))
        hierarchyMap.put("status", request.get("status"))
        hierarchyMap.put("lastFlaggedOn", request.get("lastFlaggedOn"))
        hierarchyMap.put("flagReasons", request.get("flagReasons"))

        request.put(HierarchyConstants.HIERARCHY, JsonUtils.serialize(hierarchyMap))
      }
      val updateNode = DataNode.update(request)
      updateNode
    }).flatMap(f => f) recoverWith { case e: CompletionException => throw e.getCause }
  }

  private def fetchHierarchy(request: Request)(implicit ec: ExecutionContext): Future[Any] = {
    ExternalPropsManager.fetchProps(request, List(HierarchyConstants.HIERARCHY)).map(resp => {
      resp.getResult.toMap.getOrElse(HierarchyConstants.HIERARCHY, "").asInstanceOf[String]
    }) recover { case e: ResourceNotFoundException => TelemetryManager.log("No hierarchy is present in cassandra for identifier:" + request.get(HierarchyConstants.IDENTIFIER)) }
  }

  def updateContentFlag(node: Node, request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] ={
    val mimeType: String = node.getMetadata.get("mimeType").asInstanceOf[String]
    RedisCache.delete(node.getIdentifier)
    RedisCache.delete(COLLECTION_CACHE_KEY_PREFIX + node.getIdentifier)
    if(StringUtils.equalsIgnoreCase(mimeType, HierarchyConstants.COLLECTION_MIME_TYPE)){
      request.getContext().put("schemaName", COLLECTION_SCHEMA_NAME)
      updateCollection(request)
    }else
      DataNode.update(request)
  }

  def addDataIntoList(dataList: util.List[String], metadata: util.Map[String, Object], key: String): util.List[String] = {
    val existingData = metadata.getOrDefault(key, new util.ArrayList[String])//.asInstanceOf[util.ArrayList[String]]
    val existingDataList = {if(existingData.isInstanceOf[Array[String]]) existingData.asInstanceOf[Array[String]].toList.asJava else if (existingData.isInstanceOf[util.List[String]]) existingData.asInstanceOf[util.List[String]] else new util.ArrayList[String]}
    val responseDataList = new util.ArrayList[String]
    responseDataList.addAll(existingDataList)
    if (CollectionUtils.isEmpty(responseDataList)) {
      dataList
    }else{
      responseDataList.addAll(dataList)
      new util.ArrayList[String](responseDataList.toSet)
    }
  }
}