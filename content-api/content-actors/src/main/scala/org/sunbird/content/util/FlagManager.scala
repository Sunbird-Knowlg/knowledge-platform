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
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.HierarchyConstants
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConversions._

object FlagManager {
  private val FLAGGABLE_STATUS: util.List[String] = util.Arrays.asList("Live", "Unlisted", "Flagged")
  private val COLLECTION_SCHEMA_NAME = "collection"
  private val COLLECTION_CACHE_KEY_PREFIX = "hierarchy_"

  def flag(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {

    DataNode.read(request).map(node => {
      val flagReasons: util.List[String] = request.get("flagReasons").asInstanceOf[util.ArrayList[String]]
      val flaggedBy: String = request.get("flaggedBy").asInstanceOf[String]
      val flags: util.List[String] = request.get("flags").asInstanceOf[util.ArrayList[String]]
      
      val metadata: util.Map[String, Object] = node.getMetadata.asInstanceOf[util.HashMap[String, Object]]
      val status: String = metadata.get("status").asInstanceOf[String]
      val versionKey = node.getMetadata.get("versionKey").asInstanceOf[String]
      request.put("identifier", node.getIdentifier)

      if (!FLAGGABLE_STATUS.contains(status))
        throw new ClientException("ERR_CONTENT_NOT_FLAGGABLE", "Unpublished Content " + node.getIdentifier + " cannot be flagged")

      val flaggedByList: util.List[String] = if(StringUtils.isNotBlank(flaggedBy)) util.Arrays.asList(flaggedBy) else new util.ArrayList[String]
      val flaggedList: util.List[String] = addDataIntoList(flaggedByList,metadata)//addFlaggedBy(flaggedBy, metadata)
      if (CollectionUtils.isNotEmpty(flaggedList))
      request.put("lastUpdatedBy", flaggedBy)
      request.put("flaggedBy", flaggedList)
      request.put("flags", flags)
      request.put("status", "Flagged")
      request.put("lastFlaggedOn", DateUtils.formatCurrentDate())
      if (CollectionUtils.isNotEmpty(flagReasons))
        request.put("flagReasons", addDataIntoList(flagReasons, metadata))
        //request.put("flagReasons", addFlagReasons(flagReasons, metadata))
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

  def updateContentFlag(node: Node, request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Node] ={
    val mimeType: String = node.getMetadata.get("mimeType").asInstanceOf[String]
    RedisCache.delete(node.getIdentifier)
    RedisCache.delete(COLLECTION_CACHE_KEY_PREFIX + node.getIdentifier)
    if(StringUtils.equalsIgnoreCase(mimeType, CopyConstants.COLLECTION_MIME_TYPE)){
      request.getContext().put("schemaName", COLLECTION_SCHEMA_NAME)
      updateCollection(request)
    }else
      DataNode.update(request)
  }

  /*def addFlaggedBy(flaggedBy: String, metadata: util.Map[String, Object]): util.List[String] = {
    val flaggedByList: util.List[String] = if(StringUtils.isNotBlank(flaggedBy)) util.Arrays.asList(flaggedBy) else new util.ArrayList[String]
    val existingFlaggedBy = metadata.getOrDefault("flaggedBy", new util.ArrayList[String]).asInstanceOf[util.ArrayList[String]]

    if (CollectionUtils.isEmpty(existingFlaggedBy)) {
      flaggedByList
    }else{
      val existingFlaggedByList = {if(existingFlaggedBy.isInstanceOf[Array[String]])
        existingFlaggedBy.asInstanceOf[Array[String]].toList.asJava else if(existingFlaggedBy.isInstanceOf[util.List[String]]) existingFlaggedBy.asInstanceOf[util.List[String]] else existingFlaggedBy}
    }
      /*var existingFlaggedByList: util.List[String] = null
      if (existingFlaggedBy.isInstanceOf[Array[String]]) {
        existingFlaggedByList = existingFlaggedBy.asInstanceOf[Array[String]].toList.asJava
      }
      else if (existingFlaggedBy.isInstanceOf[util.List[Object]]) {
        existingFlaggedByList = existingFlaggedBy.asInstanceOf[util.List[String]]
      }
      if (CollectionUtils.isNotEmpty(existingFlaggedByList)) {
        val flaggedBySet: util.Set[String] = new util.HashSet[String](existingFlaggedByList)
        flaggedBySet.addAll(flaggedByList)
        return new util.ArrayList[String](flaggedBySet)
      }*/

    //flaggedByList
  }*/
  /*def addFlaggedBy(flaggedBy: String, metadata: util.Map[String, Object]): util.List[String] = {
    val flaggedByList: util.List[String] = if(StringUtils.isNotBlank(flaggedBy)) util.Arrays.asList(flaggedBy) else new util.ArrayList[String]
    val existingFlaggedBy = metadata.getOrDefault("flaggedBy", new util.ArrayList[String]).asInstanceOf[util.ArrayList[String]]
    if (CollectionUtils.isEmpty(existingFlaggedBy)) {
      flaggedByList
    }else{
      existingFlaggedBy.addAll(flaggedByList)
      new util.ArrayList[String](existingFlaggedBy.toSet)
      //existingFlaggedBy.stream().distinct().collect(Collectors.toList)
    }
  }

  def addFlagReasons(flagReasons: util.List[String], metadata: util.Map[String, Object]): util.List[String] = {
    val existingFlagReasons = metadata.getOrDefault("flagReasons", new util.ArrayList[String]).asInstanceOf[util.ArrayList[String]]
    if (CollectionUtils.isEmpty(existingFlagReasons)) {
      flagReasons
    }else{
      existingFlagReasons.addAll(flagReasons)
      new util.ArrayList[String](existingFlagReasons.toSet)
      //existingFlagReasons.stream().distinct().collect(Collectors.toList)
    }
  }*/

  def addDataIntoList(flagReasons: util.List[String], metadata: util.Map[String, Object]): util.List[String] = {
    val existingFlagReasons = metadata.getOrDefault("flagReasons", new util.ArrayList[String]).asInstanceOf[util.ArrayList[String]]
    if (CollectionUtils.isEmpty(existingFlagReasons)) {
      flagReasons
    }else{
      existingFlagReasons.addAll(flagReasons)
      new util.ArrayList[String](existingFlagReasons.toSet)
      //existingFlagReasons.stream().distinct().collect(Collectors.toList)
    }
  }


  /*def addFlagReasons1(flagReasons: util.List[String], metadata: util.Map[String, Object]): util.List[String] = {
    val existingFlagReasons = metadata.get("flagReasons")
    if (existingFlagReasons != null) {
      var existingFlagReasonsList: util.List[String] = null
      if (existingFlagReasons.isInstanceOf[Array[String]]) {
        existingFlagReasonsList = existingFlagReasons.asInstanceOf[Array[String]].toList.asJava
      }
      else if (existingFlagReasons.isInstanceOf[util.List[Object]]) {
        existingFlagReasonsList = existingFlagReasons.asInstanceOf[util.List[String]]
      }
      if (CollectionUtils.isNotEmpty(existingFlagReasonsList)) {
        val flagReasonsSet: util.Set[String] = new util.HashSet[String](existingFlagReasonsList)
        flagReasonsSet.addAll(flagReasons)
        return new util.ArrayList[String](flagReasonsSet)
      }
    }
    flagReasons
  }*/

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
}