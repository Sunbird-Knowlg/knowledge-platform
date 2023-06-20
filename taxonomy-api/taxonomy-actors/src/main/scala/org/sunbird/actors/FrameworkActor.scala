package org.sunbird.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, SubGraph}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.path.DataSubGraph
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}
import org.sunbird.mangers.FrameworkManager
import org.sunbird.utils.{CategoryCache, FrameworkCache}
import org.sunbird.utils.{Constants, RequestUtil}

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConversions.{mapAsJavaMap}

class FrameworkActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_FRAMEWORK => create(request)
      case Constants.READ_FRAMEWORK => read(request)
      case Constants.UPDATE_FRAMEWORK => update(request)
      case Constants.RETIRE_FRAMEWORK => retire(request)
      case Constants.PUBLISH_FRAMEWORK => publish(request)
      case Constants.COPY_FRAMEWORK => copy(request)
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
    val frameworkId = request.get("identifier").asInstanceOf[String]
    val returnCategories: java.util.List[String] = seqAsJavaListConverter(request.get("categories").asInstanceOf[String].split(",").filter(category => StringUtils.isNotBlank(category) && !StringUtils.equalsIgnoreCase(category, "null"))).asJava
    request.getRequest.put("categories", returnCategories)
    if (StringUtils.isNotBlank(frameworkId)) {
      val framework = FrameworkCache.get(frameworkId, returnCategories)
      if(framework != null){
        Future {
          ResponseHandler.OK.put(Constants.FRAMEWORK, framework)
        }
      } else {
        val frameworkData: Future[Map[String, AnyRef]] = FrameworkManager.getFrameworkHierarchy(request)
        frameworkData.map(framework => {
          if (framework.isEmpty) {
            DataNode.read(request).map(node => {
              if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, frameworkId)) {
                val framework = NodeUtil.serialize(node, null, request.getContext.get(Constants.SCHEMA_NAME).asInstanceOf[String], request.getContext.get(Constants.VERSION).asInstanceOf[String])
                ResponseHandler.OK.put(Constants.FRAMEWORK, framework)
              } else throw new ClientException("ERR_INVALID_REQUEST", "Invalid Request. Please Provide Required Properties!")
            })
          } else {
            Future {
              val filterFrameworkData  = FrameworkManager.filterFrameworkCategories(framework, returnCategories)
              FrameworkCache.save(filterFrameworkData, returnCategories)
              ResponseHandler.OK.put(Constants.FRAMEWORK, filterFrameworkData.asJava)
            }
          }
        }).flatMap(f => f)
      }
    } else throw new ClientException("ERR_INVALID_REQUEST", "Invalid Request. Please Provide Required Properties!")
  }

  @throws[Exception]
  private def update(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    DataNode.update(request).map(node => {
      ResponseHandler.OK.put("node_id", node.getIdentifier).put("versionKey", node.getMetadata.get("versionKey"))
    })
  }

  @throws[Exception]
  private def retire(request: Request): Future[Response] = {
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
          val subGraph: Future[SubGraph] = DataSubGraph.read(request)
          subGraph.map(data => {
            val frameworkHierarchy = FrameworkManager.getCompleteMetadata(frameworkId, data)
            CategoryCache.setFramework(frameworkId, frameworkHierarchy)
            val req = new Request(request)
            req.put("hierarchy", ScalaJsonUtils.serialize(frameworkHierarchy))
            req.put("identifier", frameworkId)
            oec.graphService.saveExternalProps(req)
            ResponseHandler.OK.put(Constants.PUBLISH_STATUS, s"Publish Event for Framework Id '${node.getIdentifier}' is pushed Successfully!")
          })
        } else throw new ClientException("ERR_INVALID_FRAMEWORK_ID", "Please provide valid framework identifier")
      } else throw new ClientException("ERR_INVALID_CHANNEL_ID", "Please provide valid channel identifier")
    }).flatMap(f => f)
  }

  //TODO:
  private def copy(request: Request): Future[Response] = {
    val frameworkId = request.getRequest.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String]
    val code = request.getRequest.getOrDefault(Constants.CODE, "").asInstanceOf[String]
    if(StringUtils.isBlank(code))
      throw new ClientException("ERR_FRAMEWORK_CODE_REQUIRED", "Unique code is mandatory for framework")

    if (StringUtils.equals(frameworkId, code))
      throw new ClientException("ERR_FRAMEWORKID_CODE_MATCHES", "FrameworkId and code should not be same.")

    val getFrameworkReq = new Request()
    getFrameworkReq.setContext(new util.HashMap[String, AnyRef]() {
      {
        putAll(request.getContext)
      }
    })
    getFrameworkReq.getContext.put(Constants.SCHEMA_NAME, Constants.FRAMEWORK_SCHEMA_NAME)
    getFrameworkReq.getContext.put(Constants.VERSION, Constants.FRAMEWORK_SCHEMA_VERSION)
    getFrameworkReq.put(Constants.IDENTIFIER, code)
    DataNode.read(getFrameworkReq).map(node => {
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, code)) {
        throw new ClientException("ERR_FRAMEWORK_EXISTS", "Framework with code: " + code + ", already exists.")
      } else {
        FrameworkManager.validateChannel(request)
        ResponseHandler.OK.put("node_id", node.getIdentifier)
      }
    })
  }

}