package org.sunbird.content.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
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
import scala.concurrent.{ExecutionContext, Future}

class EventSetActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getContext.put(HierarchyConstants.SCHEMA_NAME, HierarchyConstants.EVENT_SET_SCHEMA_NAME)
    request.getOperation match {
      case "createContent" => create(request)
      case "updateContent" => update(request)
      case "getHierarchy" => getHierarchy(request)
      case _ => ERROR(request.getOperation)
    }
  }

  def create(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    val originalRequestContent = request.getRequest
    addChildEvents(request)
      .map(nodes => updateRequestWithChildRelations(request, originalRequestContent, nodes))
      .flatMap(req => {
        DataNode.create(req).map(node => {
          val response = ResponseHandler.OK
          response.put("identifier", node.getIdentifier)
          response.put("node_id", node.getIdentifier)
          response.put("versionKey", node.getMetadata.get("versionKey"))
          response
        })
      }).recoverWith {
      case e: Exception =>
        Future(ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), e.getMessage))
    }
  }

  def update(request: Request): Future[Response] = {
    if (StringUtils.isBlank(request.getRequest.getOrDefault("versionKey", "").asInstanceOf[String])) throw new ClientException("ERR_INVALID_REQUEST", "Please Provide Version Key!")
    RequestUtil.restrictProperties(request)
    val originalRequestContent = request.getRequest
    DataNode.read(request).flatMap(node => {
      deleteExistingEvents(node.getOutRelations, request).flatMap(_ => {
        addChildEvents(request).map(nodes => {
          updateRequestWithChildRelations(request, originalRequestContent, nodes)
        }).flatMap(req =>
          DataNode.update(req).map(node => {
            val response: Response = ResponseHandler.OK
            val identifier: String = node.getIdentifier.replace(".img", "")
            response.put("node_id", identifier)
            response.put("identifier", identifier)
            response.put("versionKey", node.getMetadata.get("versionKey"))
            response
          })
        )
      })
    }).recoverWith {
      case e: Exception =>
        Future(ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, ResponseCode.SERVER_ERROR.name(), e.getMessage))
    }
  }

  def getHierarchy(request: Request): Future[Response] = {
    DataNode.read(request).map(node => {
      val childNodes = node.getOutRelations.asScala.map(relation => relation.getEndNodeId).toList
      val children = node.getOutRelations.asScala.map(relation => relation.getEndNodeMetadata).map(metadata => {
        SystemProperties.values().foreach(value => metadata.remove(value.name()))
        metadata
      }).toList

      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, new util.ArrayList[String](), node.getObjectType.toLowerCase.replace("image", ""), request.getContext.get("version").asInstanceOf[String], true)
      metadata.put("identifier", node.getIdentifier.replace(".img", ""))
      metadata.put("childNodes", childNodes.asJava)
      metadata.put("children", children.asJava)
      val response: Response = ResponseHandler.OK
      response.put("content", metadata)
      response
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
}
