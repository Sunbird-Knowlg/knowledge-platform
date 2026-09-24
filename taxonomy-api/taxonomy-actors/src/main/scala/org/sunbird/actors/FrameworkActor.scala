package org.sunbird.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.{JsonUtils, Platform, Slug}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, SubGraph}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.path.DataSubGraph
import org.sunbird.graph.utils.{NodeUtil, ScalaJsonUtils}
import org.sunbird.managers.FrameworkManager
import org.sunbird.utils.{CategoryCache, FrameworkCache}
import org.sunbird.utils.Constants
import org.sunbird.utils.taxonomy.RequestUtil

import java.util
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

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
      case Constants.REVIEW_FRAMEWORK => review(request)
      case Constants.REJECT_FRAMEWORK => reject(request)
      case _ => ERROR(request.getOperation)
    }
  }

  private val REVIEW_ALLOWED_STATUSES: Set[String] = Set("Draft", "Live")

  @throws[Exception]
  private def review(request: Request): Future[Response] = {
    val frameworkId = request.getRequest.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String]
    val graphId = request.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
    FrameworkManager.getLiveEditNode(graphId, frameworkId).flatMap { node =>
      val status = node.getMetadata.getOrDefault("status", "").asInstanceOf[String]
      if (!REVIEW_ALLOWED_STATUSES.contains(status))
        throw new ClientException("ERR_INVALID_REQUEST", s"Cannot send framework for review: current status is '$status'")
      val updateReq = new Request(request)
      updateReq.getContext.put(Constants.IDENTIFIER, node.getIdentifier)
      updateReq.getContext.put("versioning", "disabled")
      updateReq.setRequest(new util.HashMap[String, AnyRef]() {{ put("status", "Review") }})
      DataNode.update(updateReq).map(_ => ResponseHandler.OK.put(Constants.IDENTIFIER, frameworkId).put("status", "Review"))
    }
  }

  @throws[Exception]
  private def reject(request: Request): Future[Response] = {
    val frameworkId = request.getRequest.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String]
    val graphId = request.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
    FrameworkManager.getLiveEditNode(graphId, frameworkId).flatMap { node =>
      val status = node.getMetadata.getOrDefault("status", "").asInstanceOf[String]
      if (!StringUtils.equals(status, "Review"))
        throw new ClientException("ERR_INVALID_REQUEST", s"Cannot reject framework: current status is '$status', expected 'Review'")
      val updateReq = new Request(request)
      updateReq.getContext.put(Constants.IDENTIFIER, node.getIdentifier)
      updateReq.getContext.put("versioning", "disabled")
      updateReq.setRequest(new util.HashMap[String, AnyRef]() {{ put("status", "Draft") }})
      DataNode.update(updateReq).flatMap(_ => FrameworkManager.rejectFrameworkTermsSweep(graphId, frameworkId))
        .map(_ => ResponseHandler.OK.put(Constants.IDENTIFIER, frameworkId).put("status", "Draft"))
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
          FrameworkManager.validateTranslationMap(request)
          DataNode.create(request).map(frameNode => {
            ResponseHandler.OK.put(Constants.NODE_ID, frameNode.getIdentifier).put("versionKey", frameNode.getMetadata.get("versionKey"))
          })
        } else throw new ClientException("ERR_INVALID_CHANNEL_ID", "Please provide valid channel identifier")
      }).flatten
    } else throw new ClientException("ERR_INVALID_REQUEST", "Invalid Request. Please Provide Required Properties!")

  }


  @throws[Exception]
  private def read(request: Request): Future[Response] = {
    val frameworkId = request.get("identifier").asInstanceOf[String]
    val returnCategories: java.util.List[String] = request.get("categories").asInstanceOf[String].split(",").filter(category => StringUtils.isNotBlank(category) && !StringUtils.equalsIgnoreCase(category, "null")).toList.asJava
    request.getRequest.put("categories", returnCategories)
    if (StringUtils.isNotBlank(frameworkId)) {
      val framework = FrameworkCache.get(frameworkId, returnCategories)
      if(framework != null){
        Future {
          ResponseHandler.OK.put(Constants.FRAMEWORK, framework)
        }
      } else {
        val frameworkData: Future[Map[String, AnyRef]] = if (Platform.getBoolean("service.db.cassandra.enabled", true))
          FrameworkManager.getFrameworkHierarchy(request) else {
          val frameworkStr = RedisCache.get("fw:"+frameworkId, (key: String) => "{}")
          Future(JsonUtils.deserialize(frameworkStr, classOf[java.util.Map[String, AnyRef]]).asScala.toMap)
        }
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
              val filterFrameworkData: Map[String, AnyRef] = FrameworkManager.filterFrameworkCategories(framework.asJava, returnCategories)
              FrameworkCache.save(filterFrameworkData, returnCategories)
              val javaMap: java.util.Map[String, AnyRef] = filterFrameworkData.asJava
              ResponseHandler.OK.put(Constants.FRAMEWORK, javaMap)
            }
          }
        }).flatten
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
    val frameworkId = request.getContext.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String]
    val graphId = request.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
    request.getRequest.put("status", "Retired")
    request.getContext.put("versioning", "disabled")
    FrameworkManager.deleteImageNodeIfExists(graphId, frameworkId).flatMap(_ =>
      DataNode.update(request).map(node => {
        ResponseHandler.OK.put("node_id", node.getIdentifier).put("identifier", node.getIdentifier)
      })
    )
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
          val graphId = request.getContext.getOrDefault("graph_id", "domain").asInstanceOf[String]
          val publishReq = new Request(request)
          publishReq.getContext.put(Constants.SCHEMA_NAME, request.getContext.getOrDefault(Constants.SCHEMA_NAME, Constants.FRAMEWORK_SCHEMA_NAME))
          publishReq.getContext.put(Constants.VERSION, request.getContext.getOrDefault(Constants.VERSION, Constants.FRAMEWORK_SCHEMA_VERSION))

          FrameworkManager.publishFramework(publishReq, frameworkId).flatMap { liveNode =>
            FrameworkManager.publishDescendants(graphId, frameworkId).flatMap { _ =>
              val getFrameworkReq = new Request()
              getFrameworkReq.setContext(new util.HashMap[String, AnyRef]() {
                {
                  putAll(request.getContext)
                }
              })
              getFrameworkReq.getContext.put(Constants.SCHEMA_NAME, request.getContext.getOrDefault(Constants.SCHEMA_NAME, Constants.FRAMEWORK_SCHEMA_NAME))
              getFrameworkReq.getContext.put(Constants.VERSION, request.getContext.getOrDefault(Constants.VERSION, Constants.FRAMEWORK_SCHEMA_VERSION))
              getFrameworkReq.put(Constants.IDENTIFIER, frameworkId)
              val subGraph: Future[SubGraph] = DataSubGraph.read(getFrameworkReq)
              subGraph.map(data => {
                val frameworkHierarchy = FrameworkManager.getCompleteMetadata(frameworkId, data, true)
                CategoryCache.setFramework(frameworkId, frameworkHierarchy)
                val hierarchy = ScalaJsonUtils.serialize(frameworkHierarchy)
                if (Platform.getBoolean("service.db.cassandra.enabled", true)) {
                  val req = new Request(request)
                  req.put("hierarchy", hierarchy)
                  req.put("identifier", frameworkId)
                  oec.graphService.saveExternalProps(req)
                } else RedisCache.set("fw:"+frameworkId, hierarchy)
                ResponseHandler.OK.put(Constants.PUBLISH_STATUS, s"Publish Event for Framework Id '${frameworkId}' is pushed Successfully!")
                  .put(Constants.IDENTIFIER, frameworkId)
                  .put("status", "Live")
                  .put("version", liveNode.getMetadata.get("version")) // business publish-counter, distinct from Constants.VERSION
              })
            }
          }
        } else throw new ClientException("ERR_INVALID_FRAMEWORK_ID", "Please provide valid framework identifier")
      } else throw new ClientException("ERR_INVALID_CHANNEL_ID", "Please provide valid channel identifier")
    }).flatten
  }

  //TODO:
  private def copy(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    FrameworkManager.copyHierarchy(request)
  }
}