package org.sunbird.content.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.content.util.{ContentConstants, DiscardManager, RetireManager}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.common.enums.SystemProperties
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.util.RequestUtil
import org.sunbird.utils.HierarchyConstants

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters._
import scala.concurrent.Future

class EventSetActor @Inject()(implicit oec: OntologyEngineContext, ss: StorageService) extends ContentActor {

  override def onReceive(request: Request): Future[Response] = {
    request.getContext.put(HierarchyConstants.SCHEMA_NAME, HierarchyConstants.EVENT_SET_SCHEMA_NAME)
    request.getOperation match {
      case "createContent" => create(request)
      case "updateContent" => update(request)
      case "publishContent" => publish(request)
      case "getHierarchy" => getHierarchy(request)
      case "readContent" => read(request)
      case "retireContent" => retire(request)
      case "discardContent" => discard(request)
      case _ => ERROR(request.getOperation)
    }
  }

  override def create(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    val originalRequestContent = request.getRequest
    addChildEvents(request)
      .map(nodes => updateRequestWithChildRelations(request, originalRequestContent, nodes))
      .flatMap(req => {
        DataNode.create(req).map(node => {
          ResponseHandler.OK.put("identifier", node.getIdentifier).put("node_id", node.getIdentifier)
            .put("versionKey", node.getMetadata.get("versionKey"))
        })
      }).recoverWith {
      case clientException: ClientException =>
        Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, ResponseCode.CLIENT_ERROR.name(), clientException.getMessage))
      case e: Exception =>
        Future(ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), e.getMessage))
    }
  }

  override def update(request: Request): Future[Response] = {
    if (StringUtils.isBlank(request.getRequest.getOrDefault("versionKey", "").asInstanceOf[String])) throw new ClientException("ERR_INVALID_REQUEST", "Please Provide Version Key!")
    RequestUtil.restrictProperties(request)
    val originalRequestContent = request.getRequest
    DataNode.read(request).flatMap(node => {
      if (!"Draft".equalsIgnoreCase(node.getMetadata.getOrDefault("status", "").toString)) {
        throw new ClientException(ContentConstants.ERR_CONTENT_NOT_DRAFT, "Update not allowed! EventSet status isn't draft")
      }
      deleteExistingEvents(node.getOutRelations, request).flatMap(_ => {
        addChildEvents(request).map(nodes => {
          updateRequestWithChildRelations(request, originalRequestContent, nodes)
        }).flatMap(req =>
          DataNode.update(req).map(node => {
            val identifier: String = node.getIdentifier.replace(".img", "")
            ResponseHandler.OK.put("node_id", identifier).put("identifier", identifier)
              .put("versionKey", node.getMetadata.get("versionKey"))
          })
        )
      })
    }).recoverWith {
      case clientException: ClientException =>
        Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, ResponseCode.CLIENT_ERROR.name(), clientException.getMessage))
      case e: Exception =>
        Future(ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), e.getMessage))
    }
  }

  def publish(request: Request): Future[Response] = {
    DataNode.read(request).flatMap(node => {
      if (!"Draft".equalsIgnoreCase(node.getMetadata.getOrDefault("status", "").toString)) {
        throw new ClientException(ContentConstants.ERR_CONTENT_NOT_DRAFT, "Publish not allowed! EventSet status isn't draft")
      }
      publishChildEvents(node.getOutRelations, request).flatMap(_ => {
        val existingVersionKey = node.getMetadata.getOrDefault("versionKey", "").asInstanceOf[String]
        request.put("versionKey", existingVersionKey)
        request.getContext.put("identifier", node.getIdentifier)
        request.put("status", "Live")
        DataNode.update(request).map(updateNode => {
          val identifier: String = updateNode.getIdentifier.replace(".img", "")
          ResponseHandler.OK.put("node_id", identifier).put("identifier", identifier)
            .put("versionKey", updateNode.getMetadata.get("versionKey"))
        })
      }
      )
    }).recoverWith {
      case clientException: ClientException =>
        Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, ResponseCode.CLIENT_ERROR.name(), clientException.getMessage))
      case e: Exception =>
        Future(ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), e.getMessage))
    }
  }

  override def retire(request: Request): Future[Response] = {
    DataNode.read(request).flatMap(node => {
      retireChildEvents(node.getOutRelations, request)
        .flatMap(_ => RetireManager.retire(request))
    }).recoverWith {
      case clientException: ClientException =>
        Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, ResponseCode.CLIENT_ERROR.name(), clientException.getMessage))
      case e: Exception =>
        Future(ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), e.getMessage))
    }
  }

  override def discard(request: Request): Future[Response] = {
    DataNode.read(request).flatMap(node => discardChildEvents(node.getOutRelations, request)
      .flatMap(_ => DiscardManager.discard(request)))
      .recoverWith {
        case clientException: ClientException =>
          Future(ResponseHandler.ERROR(ResponseCode.CLIENT_ERROR, ResponseCode.CLIENT_ERROR.name(), clientException.getMessage))
        case e: Exception =>
          Future(ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), e.getMessage))
    }
  }

  def getHierarchy(request: Request): Future[Response] = {
    val fields: util.List[String] = seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
    request.getRequest.put("fields", fields)
    DataNode.read(request).map(node => {
      val outRelations = if (node.getOutRelations == null) List[Relation]() else node.getOutRelations.asScala
      val childNodes = outRelations.map(relation => relation.getEndNodeId).toList
      val children = outRelations.map(relation => relation.getEndNodeMetadata).map(metadata => {
        SystemProperties.values().foreach(value => metadata.remove(value.name()))
        metadata
      }).toList

      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, new util.ArrayList[String](), node.getObjectType.toLowerCase.replace("image", ""), request.getContext.get("version").asInstanceOf[String], true)
      metadata.put("identifier", node.getIdentifier.replace(".img", ""))
      metadata.put("childNodes", childNodes.asJava)
      metadata.put("children", children.asJava)
      ResponseHandler.OK.put("eventset", metadata)
    })
  }

  private def updateRequestWithChildRelations(request: Request, originalRequestContent: util.Map[String, AnyRef], nodes: List[Node]) = {
    request.setRequest(originalRequestContent)
    val relations = new util.ArrayList[util.Map[String, String]]()
    nodes.foreach(node => {
      relations.add(Map("identifier" -> node.getIdentifier).asJava)
    })
    request.getRequest.put("collections", relations)
    request
  }

  private def addChildEvents(request: Request) = {
    val newChildEvents = formChildEvents(request)
    Future.sequence(newChildEvents.map(childEvent => {
      val childRequest = new Request(request)
      childRequest.setRequest(childEvent.asJava)
      childRequest.getContext.put("schemaName", "event")
      childRequest.getContext.put("objectType", "Event")
      DataNode.create(childRequest)
    }))
  }

  private def formChildEvents(contentRequest: Request): List[collection.mutable.Map[String, AnyRef]] = {
    val scheduleObject = contentRequest.getRequest.getOrDefault("schedule", new util.HashMap[String, Object]()).asInstanceOf[util.Map[String, Object]]
    val schedules = scheduleObject.getOrDefault("value", new util.ArrayList[util.Map[String, String]]()).asInstanceOf[util.List[util.Map[String, String]]].asScala
    schedules.map(schedule => {
      var event = collection.mutable.Map[String, AnyRef]() ++ contentRequest.getRequest.asScala
      event ++= schedule.asScala
      event -= "schedule"
      event -= "identifier"
      event
    }).toList
  }

  private def deleteExistingEvents(relations: util.List[Relation], request: Request) = {
    if (relations != null)
      Future.sequence(relations.asScala.filter(rel => "Event".equalsIgnoreCase(rel.getEndNodeObjectType)).map(relation => {
        val deleteReq = new Request()
        deleteReq.setContext(request.getContext)
        val delMap = new util.HashMap[String, AnyRef]()
        delMap.put("identifier", relation.getEndNodeId)
        deleteReq.setRequest(delMap)
        DataNode.deleteNode(deleteReq)
      }))
    else
      Future(List())
  }

  private def publishChildEvents(relations: util.List[Relation], request: Request) = {
    if (relations != null)
      Future.sequence(relations.asScala.filter(rel => "Event".equalsIgnoreCase(rel.getEndNodeObjectType)).map(relation => {
        val updateReq = new Request()
        val context = new util.HashMap[String, Object]()
        context.putAll(request.getContext)
        updateReq.setContext(context)
        updateReq.getContext.put("schemaName", "event")
        updateReq.getContext.put("objectType", "Event")
        updateReq.getContext.put("identifier", relation.getEndNodeId)
        val updateMap = new util.HashMap[String, AnyRef]()
        updateMap.put("identifier", relation.getEndNodeId)
        updateMap.put("status", "Live")
        updateReq.setRequest(updateMap)
        DataNode.update(updateReq)
      }))
    else
      Future(List())
  }

  private def retireChildEvents(relations: util.List[Relation], request: Request) = {
    if (relations != null)
      Future.sequence(relations.asScala.filter(rel => "Event".equalsIgnoreCase(rel.getEndNodeObjectType)).map(relation => {
        val retireReq = new Request()
        val context = new util.HashMap[String, Object]()
        context.putAll(request.getContext)
        retireReq.setContext(context)
        retireReq.getContext.put("schemaName", "event")
        retireReq.getContext.put("objectType", "Event")
        retireReq.getContext.put("identifier", relation.getEndNodeId)
        val updateMap = new util.HashMap[String, AnyRef]()
        updateMap.put("identifier", relation.getEndNodeId)
        retireReq.setRequest(updateMap)
        RetireManager.retire(retireReq)

      }))
    else
      Future(List())
  }

  private def discardChildEvents(relations: util.List[Relation], request: Request) = {
    if (relations != null)
      Future.sequence(relations.asScala.filter(rel => "Event".equalsIgnoreCase(rel.getEndNodeObjectType)).map(relation => {
        val discardReq = new Request()
        val context = new util.HashMap[String, Object]()
        context.putAll(request.getContext)
        discardReq.setContext(context)
        discardReq.getContext.put("schemaName", "event")
        discardReq.getContext.put("objectType", "Event")
        discardReq.getContext.put("identifier", relation.getEndNodeId)
        val updateMap = new util.HashMap[String, AnyRef]()
        updateMap.put("identifier", relation.getEndNodeId)
        discardReq.setRequest(updateMap)
        RetireManager.retire(discardReq)

      }))
    else
      Future(List())
  }
}