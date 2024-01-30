package org.sunbird.content.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode, ServerException}
import org.sunbird.content.util.ContentConstants
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.util.RequestUtil

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`map AsScala`
import scala.concurrent.{ExecutionContext, Future}

class ObjectActor @Inject() (implicit oec: OntologyEngineContext, ss: StorageService) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case "readObject" => read(request)
      case "createObject" => create(request)
      case "updateObject" => update(request)
      case "retireObject" => retire(request)
      case _ => handleDefault(request) //ERROR(request.getOperation)
    }
  }

  @throws[Exception]
  private def read(request: Request): Future[Response] = {
    val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
    request.getRequest.put("fields", fields)
    val schemaName = request.getContext.getOrDefault("schemaName", "object").asInstanceOf[String]
    DataNode.read(request).map(node => {
      if (NodeUtil.isRetired(node)) ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.name, "Object not found with identifier: " + node.getIdentifier)
      val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields,null, request.getContext.get("version").asInstanceOf[String])
      ResponseHandler.OK.put(schemaName, metadata)
    })
  }
  @throws[Exception]
  private def create(request: Request): Future[Response] = {
    try {
      RequestUtil.restrictProperties(request)
    } catch {
      case e: Exception => throw new ClientException("INVALID_OBJECT", "The schema does not exist for the provided object.")
    }
    DataNode.create(request).map(node => {
      ResponseHandler.OK.put("identifier", node.getIdentifier).put("versionKey", node.getMetadata.get("versionKey"))
    })
  }

  @throws[Exception]
  private def update(request: Request): Future[Response] = {
    if (StringUtils.isBlank(request.getRequest.getOrDefault("versionKey", "").asInstanceOf[String])) throw new ClientException("ERR_INVALID_REQUEST", "Please Provide Version Key!")
    try {
      RequestUtil.restrictProperties(request)
      DataNode.update(request).map(node => {
        val identifier: String = node.getIdentifier.replace(".img", "")
        ResponseHandler.OK.put("identifier", identifier).put("versionKey", node.getMetadata.get("versionKey"))
      })
    } catch {
      case e: Exception => throw new ClientException("INVALID_OBJECT", "The schema does not exist for the provided object.")
    }
  }

  @throws[Exception]
  private def retire(request: Request): Future[Response] = {
    request.getRequest.put("status", "Retired")
    try {
      DataNode.update(request).map(node => {
        ResponseHandler.OK.put("identifier", node.getIdentifier)
      })
    } catch {
      case e: Exception => throw new ClientException("INVALID_OBJECT", "The schema does not exist for the provided object.")
    }
  }

  private def handleDefault(request: Request): Future[Response] = {
    val req = new Request(request)
    val graph_id = req.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
    val schemaName = req.getContext.getOrDefault("schemaName", "").asInstanceOf[String]
    val schemaVersion = req.getContext.getOrDefault("schemaVersion", "1.0").asInstanceOf[String]
    try {
      val transitionProps = DefinitionNode.getTransitionProps(graph_id, schemaVersion, schemaName)
      val operation = request.getOperation
      if(transitionProps.contains(operation)){
        val transitionData = transitionProps.asJava.get(operation).asInstanceOf[util.Map[String, AnyRef]]
        val fromStatus = transitionData.get("from").asInstanceOf[util.List[String]]
        val identifier = request.getContext.get("identifier").asInstanceOf[String]
        val readReq = new Request();
        readReq.setContext(request.getContext)
        readReq.put("identifier", identifier)
        DataNode.read(readReq).map(node => {
          if (!fromStatus.contains(node.getMetadata.get("status").toString)) {
            throw new ClientException(ContentConstants.ERR_CONTENT_NOT_DRAFT, "Transition not allowed! "+ schemaName.capitalize +" Object status should be one of :" + fromStatus.toString())
          }
          val toStatus = transitionData.get("to").asInstanceOf[String]
          val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, null, node.getObjectType.toLowerCase.replace("image", ""), request.getContext.get("version").asInstanceOf[String])
          val completeMetaData =  metadata ++ request.getRequest
          val requiredProps = transitionData.getOrDefault("required", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
          if(!completeMetaData.isEmpty && !requiredProps.isEmpty){
            val errors: util.List[String] = new util.ArrayList[String]
            requiredProps.forEach(prop => {
              if(!completeMetaData.contains(prop))
                errors.add(s"Required Metadata $prop not set")
            })
            if (!errors.isEmpty)
              throw new ClientException("CLIENT_ERROR", "Validation Errors.", errors)
          }
          request.getRequest.put("status", toStatus)
          DataNode.update(request).map(updateNode => {
            ResponseHandler.OK.put("transition", s"Transition of the object is successful!")
          })
        }).flatMap(f => f)
      } else {
        ERROR(request.getOperation)
      }
    } catch {
      case e: Exception => {
        throw new ClientException("INVALID_OBJECT", "The schema does not exist for the provided object.")
      }
    }
  }

}
