package org.sunbird.actors

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.managers.FrameworkHierarchyManager
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.mangers.FrameworkManager
import org.sunbird.utils.{CategoryCache, FrameworkCache}
import org.sunbird.utils.{Constants, RequestUtil}

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap
import scala.concurrent.{ExecutionContext, Future}

class FrameworkActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_FRAMEWORK => create(request)
      case Constants.READ_FRAMEWORK => read(request)
      case Constants.UPDATE_FRAMEWORK => update(request)
      case Constants.RETIRE_FRAMEWORK => retire(request)
      case Constants.PUBLISH_FRAMEWORK => publish(request)
      case _ => ERROR(request.getOperation)
    }
  }


  @throws[Exception]
  private def create(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    val code = request.getRequest.getOrDefault(Constants.CODE, "").asInstanceOf[String]
    val channel = request.getRequest.getOrDefault(Constants.CHANNEL, "").asInstanceOf[String]
    if (StringUtils.isNotBlank(code) && StringUtils.isNotBlank(channel)) {
      request.getRequest.put(Constants.IDENTIFIER, code)
      val getChannelReq = new Request()
      getChannelReq.setContext(new util.HashMap[String, AnyRef]() {
        {
          putAll(request.getContext)
        }
      })
      getChannelReq.getContext.put(Constants.SCHEMA_NAME, Constants.CHANNEL_SCHEMA_NAME)
      getChannelReq.getContext.put(Constants.VERSION, Constants.CHANNEL_SCHEMA_VERSION)
      getChannelReq.put(Constants.IDENTIFIER, channel)
      DataNode.read(getChannelReq).map(node => {
        if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, channel)) {
          val name = node.getMetadata.getOrDefault("name", "").asInstanceOf[String]
          val description = node.getMetadata.getOrDefault("description", "").asInstanceOf[String]
          request.getRequest.putAll(Map("name" -> name, "description" -> description).asJava)
          DataNode.create(request).map(node => {
            ResponseHandler.OK.put(Constants.NODE_ID, node.getIdentifier).put("versionKey", node.getMetadata.get("versionKey"))
          })
        } else throw new ClientException("ERR_INVALID_CHANNEL_ID", "Please provide valid channel identifier")
      }).flatMap(f => f)
    } else throw new ClientException("ERR_INVALID_REQUEST", "Invalid Request. Please Provide Required Properties!")

  }

  @throws[Exception]
  private def read(request: Request): Future[Response] = {
    val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get(Constants.FIELDS).asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
    val returnCategories : util.List[String] = JavaConverters.seqAsJavaListConverter(request.get(Constants.CATEGORIES).asInstanceOf[String].split(",").filter(categories => StringUtils.isNotBlank(categories) && !StringUtils.equalsIgnoreCase(categories, "null"))).asJava
    val frameworkId = request.get("identifier").asInstanceOf[String]
    request.getRequest.put(Constants.FIELDS, fields)
    request.getRequest.put(Constants.CATEGORIES, returnCategories)
    var framework = FrameworkCache.get(frameworkId, returnCategories)
    if (MapUtils.isNotEmpty(framework.asJava)) {
      ResponseHandler.OK.put(Constants.FRAMEWORK, framework)
    }
    request.put(Constants.ROOT_ID, request.get(Constants.IDENTIFIER))
    FrameworkHierarchyManager.getHierarchy(request).map(hierarchyResponse => {
      if (!ResponseHandler.checkError(hierarchyResponse)) {
        framework = hierarchyResponse.get(Constants.FRAMEWORK).asInstanceOf[Map[String, AnyRef]]
      }})
    if (MapUtils.isNotEmpty(framework.asJava)) {
      FrameworkManager.filterFrameworkCategories(framework.asJava, returnCategories)
      FrameworkCache.save(framework, returnCategories)
      Future{ResponseHandler.OK.put(Constants.FRAMEWORK, framework)}
    } else {
      DataNode.read(request).map(node => {
        val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, request.getContext.get(Constants.SCHEMA_NAME).asInstanceOf[String], request.getContext.get(Constants.VERSION).asInstanceOf[String])
        ResponseHandler.OK.put(Constants.FRAMEWORK, metadata)
      })
    }
  }

  @throws[Exception]
  private def update(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    DataNode.update(request).map(node => {
      ResponseHandler.OK.put("node_id", node.getIdentifier).put("versionKey", node.getMetadata.get("versionKey"))
    })
  }

  @throws[Exception]
  def retire(request: Request): Future[Response] = {
    request.getRequest.put("status", "Retired")
    DataNode.update(request).map(node => {
      ResponseHandler.OK.put("node_id", node.getIdentifier).put("identifier", node.getIdentifier)
    })
  }


    @throws[Exception]
    private def publish(request: Request): Future[Response] = {
      RequestUtil.restrictProperties(request)
      val frameworkId = request.getRequest.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String]
      val channel = request.getRequest.getOrDefault(Constants.CHANNEL, "").asInstanceOf[String]
      val getChannelReq = new Request()
      getChannelReq.setContext(new util.HashMap[String, AnyRef]() {
        {
          putAll(request.getContext)
        }
      })
      getChannelReq.getContext.put(Constants.SCHEMA_NAME, Constants.CHANNEL_SCHEMA_NAME)
      getChannelReq.getContext.put(Constants.VERSION, Constants.CHANNEL_SCHEMA_VERSION)
      getChannelReq.put(Constants.IDENTIFIER, channel)
      DataNode.read(getChannelReq).map(node => {
        if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, channel)) {
          val name = node.getMetadata.getOrDefault("name", "").asInstanceOf[String]
          val description = node.getMetadata.getOrDefault("description", "").asInstanceOf[String]
          request.getRequest.putAll(Map("name" -> name, "description" -> description).asJava)
          if(StringUtils.isNotBlank(frameworkId)){
            val getFrameworkReq = new Request()
            getFrameworkReq.setContext(new util.HashMap[String, AnyRef]() {
              {
                putAll(request.getContext)
              }
            })
            getFrameworkReq.getContext.put(Constants.SCHEMA_NAME, Constants.FRAMEWORK_SCHEMA_NAME)
            getFrameworkReq.getContext.put(Constants.VERSION, Constants.FRAMEWORK_SCHEMA_VERSION)
            getFrameworkReq.put(Constants.IDENTIFIER, frameworkId)
            DataNode.read(getFrameworkReq).map(node => {
              generateFrameworkHierarchy(getFrameworkReq, node)
              request.getContext.put("identifier", node.getIdentifier)
              request.put("status", "Live")
              DataNode.update(request).map(node => {
                ResponseHandler.OK.put(Constants.PUBLISH_STATUS, s"Publish Event for Framework Id '${node.getIdentifier}' is pushed Successfully!")
              })
            }).flatMap(f => f)
          }
          else throw new ClientException("ERR_INVALID_FRAMEWORK_ID", "Please provide valid framework identifier")
        }
        else throw new ClientException("ERR_INVALID_CHANNEL_ID", "Please provide valid channel identifier")
      }).flatMap(f => f)
    }

    def generateFrameworkHierarchy(request: Request, node: Node): Future[Response] = {
      val id = request.getRequest.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String]
      if (StringUtils.equalsIgnoreCase(node.getObjectType, "Framework")) {
        FrameworkCache.delete(id)
        updateRequestWithMode(request)
        FrameworkHierarchyManager.getHierarchy(request).map(response =>{
          val originHierarchy = response.getResult.getOrDefault(Constants.FRAMEWORK, new HashMap[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
          CategoryCache.setFramework(node.getIdentifier, originHierarchy)
          Future{response}
        }).flatMap(f => f)
      } else throw new ClientException(ResponseCode.CLIENT_ERROR.name, "The object with given identifier is not a framework: " +id )
    }

  private def updateRequestWithMode(request: Request) {
    request.put("rootId", request.get(Constants.IDENTIFIER).asInstanceOf[String])
    request.put("mode", "edit")
  }
}