package org.sunbird.content.util

import java.util

import org.apache.commons.collections.MapUtils
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.managers.HierarchyManager

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

object ReleaseDialCodesManager {

  def releaseDialCodes(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    if (StringUtils.isEmpty(request.get(ContentConstants.CHANNEL).asInstanceOf[String])) {
      Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, ContentConstants.ERR_CHANNEL_BLANK_OBJECT, "Channel can not be blank or null"))
    }
    request.put(ContentConstants.ROOT_ID, request.get(ContentConstants.IDENTIFIER))
    request.getContext.put(ContentConstants.SCHEMA_NAME, ContentConstants.COLLECTION_SCHEMA_NAME)
    val hierarchyResponse = getHierarchy(request)
    hierarchyResponse.map(hierarchyResponse => {
      val hierarchy = hierarchyResponse.getResult.get(ContentConstants.CONTENT).asInstanceOf[util.Map[String, AnyRef]]
      validateRequest(hierarchy, request.get(ContentConstants.CHANNEL).asInstanceOf[String])
      val assignedDialCodes = new util.HashSet[String]()
      if(ContentConstants.VALID_STATUS.contains(hierarchy.get(ContentConstants.STATUS))){
        assignedDialCodes.addAll(getDialCodes(hierarchy, ContentConstants.PARENT))
        populateAssignedDialCodeOfHierarchyImg(request, assignedDialCodes)
      }
      getAssignedDialCodesFromImgNode(request, assignedDialCodes)
      val releasedDialCodes = new util.HashSet[String]()
      val updatedMap = getUpdatedMap(request, hierarchy, assignedDialCodes, releasedDialCodes)
      getResponse(request, updatedMap, releasedDialCodes, hierarchy)
    }).flatMap(f => f)
  }

  private def getHierarchy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    HierarchyManager.getHierarchy(request).map(hierarchyResponse => {
      if (!ResponseHandler.checkError(hierarchyResponse)) {
        Future(hierarchyResponse)
      } else {
        request.put(ContentConstants.MODE, ContentConstants.EDIT_MODE)
        HierarchyManager.getHierarchy(request).map(imgHierarchyResponse => {
          if (!ResponseHandler.checkError(imgHierarchyResponse)) {
            hierarchyResponse.put(ContentConstants.CONTENT, imgHierarchyResponse.get(ContentConstants.CONTENT))
          } else {
            throw new ResourceNotFoundException(ContentConstants.ERR_CONTENT_NOT_FOUND, "Error! Content not found with id: " + request.get(ContentConstants.IDENTIFIER))
          }
          hierarchyResponse
        })
      }
    }).flatMap(f => f)
  }

  private def validateRequest(hierarchy: util.Map[String, AnyRef], channelId: String)(implicit ec: ExecutionContext): Unit = {
    validateContentForReservedDialCodes(hierarchy)
    validateChannel(hierarchy, channelId)
    validateIsNodeRetired(hierarchy)
  }

  private def validateContentForReservedDialCodes(hierarchy: util.Map[String, AnyRef]) = {
    val validContentType = if (Platform.config.hasPath("learning.reserve_dialcode.content_type")) Platform.config.getStringList("learning.reserve_dialcode.content_type") else new util.ArrayList[String]() {
      add(ContentConstants.TEXT_BOOK)
    }
    if (!validContentType.contains(hierarchy.get(ContentConstants.CONTENT_TYPE))) {
      throw new ClientException(ContentConstants.ERR_CONTENT_CONTENT_TYPE, "Invalid Content Type.")
    }
  }

  private def validateChannel(hierarchy: util.Map[String, AnyRef], channelId: String) = {
    if (!StringUtils.equals(hierarchy.getOrDefault(ContentConstants.CHANNEL, "").asInstanceOf[String], channelId)) {
      throw new ClientException(ContentConstants.ERR_CONTENT_INVALID_CHANNEL, "Invalid Channel Id.")
    }
  }

  private def validateIsNodeRetired(hierarchy: util.Map[String, AnyRef]) = {
    if (StringUtils.equals(hierarchy.get(ContentConstants.STATUS).asInstanceOf[String], ContentConstants.RETIRED))
      throw new ResourceNotFoundException(ContentConstants.ERR_CONTENT_NOT_FOUND, "Error! Content not found with id: " + hierarchy.get(ContentConstants.IDENTIFIER))
  }

  private def getUpdatedMap(request: Request, hierarchy: util.Map[String, AnyRef], assignedDialCodes: util.Set[String], releasedDialCodes: util.Set[String]): util.HashMap[String, AnyRef] = {
    val reservedDialCodeMap = getReservedDialCodes(hierarchy)
    if (MapUtils.isEmpty(reservedDialCodeMap)) {
      throw new ClientException(ContentConstants.ERR_NO_RESERVED_DIALCODES, "Error! No DIAL Codes are Reserved for content: " + request.get(ContentConstants.IDENTIFIER))
    }
    getAssignedDialCodes(hierarchy.getOrDefault(ContentConstants.CHILDREN, new util.ArrayList[AnyRef]).asInstanceOf[util.List[util.Map[String, AnyRef]]], assignedDialCodes, ContentConstants.DEFAULT)
    releasedDialCodes.addAll(getReleasedDialCodes(reservedDialCodeMap, assignedDialCodes))
    if (CollectionUtils.isEmpty(releasedDialCodes)) {
      throw new ClientException(ContentConstants.ERR_ALL_DIALCODES_UTILIZED, "Error! All Reserved DIAL Codes are Utilized.")
    }
    releasedDialCodes.foreach(dialCode => reservedDialCodeMap.remove(dialCode))
    val updatedMap = new util.HashMap[String, AnyRef]()
    if (MapUtils.isEmpty(reservedDialCodeMap)) {
      updatedMap.put(ContentConstants.RESERVED_DIALCODES, null)
    } else {
      updatedMap.put(ContentConstants.RESERVED_DIALCODES, reservedDialCodeMap)
    }
    updatedMap
  }

  private def getReservedDialCodes(hierarchy: util.Map[String, AnyRef]): util.Map[String, Integer] = {
    val reservedDialCode = new util.HashMap[String, Integer]()
    if (MapUtils.isNotEmpty(hierarchy.get(ContentConstants.RESERVED_DIALCODES).asInstanceOf[util.Map[String, Integer]])) {
      reservedDialCode.putAll(hierarchy.get(ContentConstants.RESERVED_DIALCODES).asInstanceOf[util.Map[String, Integer]])
    }
    reservedDialCode
  }

  private def getDialCodes(hierarchy: util.Map[String, AnyRef], visibility: String): util.List[String] = {
    val dialcodes = new util.ArrayList[String]()
    if (hierarchy.containsKey(ContentConstants.DIALCODES) && hierarchy.containsKey(ContentConstants.VISIBILITY) && !StringUtils.equals(hierarchy.get(ContentConstants.VISIBILITY).asInstanceOf[String], visibility)) {
      dialcodes.addAll(hierarchy.get(ContentConstants.DIALCODES).asInstanceOf[util.List[String]])
    }
    dialcodes
  }

  private def populateAssignedDialCodeOfHierarchyImg(request: Request, assignedDialCodes: util.Set[String])(implicit ec: ExecutionContext, oec: OntologyEngineContext) = {
    val req = new Request(request)
    req.put(ContentConstants.MODE, ContentConstants.EDIT_MODE)
    HierarchyManager.getHierarchy(req).map(imgHierarchyResponse => {
      if (!ResponseHandler.checkError(imgHierarchyResponse)) {
        val imgHierarchy = imgHierarchyResponse.getResult.get(ContentConstants.CONTENT).asInstanceOf[util.Map[String, AnyRef]]
        if (CollectionUtils.isNotEmpty(imgHierarchy.get(ContentConstants.CHILDREN).asInstanceOf[util.List[Object]])) {
          getAssignedDialCodes(imgHierarchy.get(ContentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]], assignedDialCodes, ContentConstants.DEFAULT)
        } else {
          throw new ClientException(ContentConstants.ERR_CONTENT_BLANK_OBJECT, "Hierarchy is null for :" + request.get(ContentConstants.IDENTIFIER))
        }
      } else {
        imgHierarchyResponse
      }
    })
  }

  private def getAssignedDialCodes(children: util.List[util.Map[String, AnyRef]], assignedDialCodes: util.Set[String], visibility: String): Unit = {
    if (CollectionUtils.isNotEmpty(children)) {
      val nextChildren = bufferAsJavaList(children.flatMap(child => {
        assignedDialCodes.addAll(getDialCodes(child, visibility))
        if (!child.isEmpty && CollectionUtils.isNotEmpty(child.get(ContentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]]) && !StringUtils.equals(child.get(ContentConstants.VISIBILITY).asInstanceOf[String], ContentConstants.DEFAULT))
          child.get(ContentConstants.CHILDREN).asInstanceOf[util.List[util.Map[String, AnyRef]]]
        else new util.ArrayList[util.Map[String, AnyRef]]
      }))
      getAssignedDialCodes(nextChildren, assignedDialCodes, visibility)
    }
  }

  private def getAssignedDialCodesFromImgNode(request: Request, assignedDialCodes: util.Set[String])(implicit ec: ExecutionContext, oec: OntologyEngineContext) = {
    request.put(ContentConstants.MODE, ContentConstants.EDIT_MODE)
    DataNode.read(request).map(imgNode => {
      assignedDialCodes.addAll(getDialCodes(imgNode.getMetadata, ContentConstants.PARENT))
    })
  }

  private def getReleasedDialCodes(reservedDialCodeMap: util.Map[String, Integer], assignedDialCodes: util.Set[String]): util.List[String] = {
    val reservedDialCodes = new util.ArrayList[String]()
    reservedDialCodes.addAll(reservedDialCodeMap.keySet())
    if (CollectionUtils.isEmpty(assignedDialCodes))
      reservedDialCodes
    else
      reservedDialCodes.filter(!assignedDialCodes.contains(_)).toList
  }

  private def getResponse(request: Request, updateMap: util.Map[String, AnyRef], releasedDialCodes: util.Set[String], hierarchy: util.Map[String, AnyRef])(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    request.put(ContentConstants.IDENTIFIERS, new util.ArrayList[String]() {
      {
        add(request.get(ContentConstants.IDENTIFIER).asInstanceOf[String])
        add(request.get(ContentConstants.IDENTIFIER).asInstanceOf[String] + ContentConstants.IMAGE_SUFFIX)
      }
    })
    val updatedHierarchy = hierarchy + (ContentConstants.RESERVED_DIALCODES -> updateMap.get(ContentConstants.RESERVED_DIALCODES))
    request.put(ContentConstants.METADATA, updateMap)
    DataNode.bulkUpdate(request).map(responseMap => {
      updateHierarchy(request, updatedHierarchy).map(hierarchyResponse => {
        if (!ResponseHandler.checkError(hierarchyResponse)) {
          RedisCache.delete(ContentConstants.HIERARCHY_PREFIX + request.get(ContentConstants.IDENTIFIER))
          RedisCache.delete(request.get(ContentConstants.IDENTIFIER).asInstanceOf[String])
          val response = ResponseHandler.OK()
          response.put(ContentConstants.IDENTIFIER, request.get(ContentConstants.IDENTIFIER))
          response.put(ContentConstants.NODE_ID, request.get(ContentConstants.IDENTIFIER))
          response.put(ContentConstants.RELEASED_DIALCODES, releasedDialCodes)
          response.put(ContentConstants.COUNT, releasedDialCodes.size())
          response
        } else {
          hierarchyResponse
        }
      })
    }).flatMap(f => f)
  }

  private def updateHierarchy(request: Request, hierarchy: util.Map[String, AnyRef])(implicit ec: ExecutionContext): Future[Response] = {
    val req: Request = new Request()
    req.setContext(request.getContext)
    req.put(ContentConstants.HIERARCHY, ScalaJsonUtils.serialize(hierarchy))
    req.put(ContentConstants.IDENTIFIER, request.get(ContentConstants.ROOT_ID))
    ExternalPropsManager.saveProps(req)
  }

}
