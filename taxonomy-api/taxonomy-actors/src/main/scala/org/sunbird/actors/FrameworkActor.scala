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
import org.sunbird.mangers.FrameworkManager
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
      case _ => ERROR(request.getOperation)
    }
  }

  // ─── Private helpers ─────────────────────────────────────────────────────────

  /**
   * Builds a Request scoped to the channel schema, reads the channel node,
   * and returns it. Used by both create() and publish().
   */
  private def readChannelNode(base: Request, channelId: String): Future[Node] = {
    val req = new Request()
    req.setContext(new util.HashMap[String, AnyRef]() {{ putAll(base.getContext) }})
    req.getContext.put(Constants.SCHEMA_NAME, Constants.CHANNEL_SCHEMA_NAME)
    req.getContext.put(Constants.VERSION, Constants.CHANNEL_SCHEMA_VERSION)
    req.put(Constants.IDENTIFIER, channelId)
    DataNode.read(req)
  }

  /**
   * Fetches the framework hierarchy from the configured backing store.
   * Falls back to Redis when Cassandra is disabled.
   */
  private def fetchFrameworkFromStorage(request: Request, frameworkId: String): Future[Map[String, AnyRef]] =
    if (Platform.getBoolean("service.db.cassandra.enabled", true))
      FrameworkManager.getFrameworkHierarchy(request)
    else {
      val frameworkStr = RedisCache.get("fw:" + frameworkId, (_: String) => "{}")
      Future(JsonUtils.deserialize(frameworkStr, classOf[java.util.Map[String, AnyRef]]).asScala.toMap)
    }

  /**
   * Reads the framework SubGraph, builds its complete hierarchy metadata,
   * persists it, and returns the publish-OK response.
   */
  private def persistFrameworkHierarchy(request: Request, frameworkId: String): Future[Response] = {
    val getFrameworkReq = new Request()
    getFrameworkReq.setContext(new util.HashMap[String, AnyRef]() {{ putAll(request.getContext) }})
    getFrameworkReq.getContext.put(Constants.SCHEMA_NAME, Constants.FRAMEWORK_SCHEMA_NAME)
    getFrameworkReq.getContext.put(Constants.VERSION, Constants.FRAMEWORK_SCHEMA_VERSION)
    getFrameworkReq.put(Constants.IDENTIFIER, frameworkId)
    DataSubGraph.read(getFrameworkReq).map { data =>
      val frameworkHierarchy = FrameworkManager.getCompleteMetadata(frameworkId, data, includeRelations = true)
      CategoryCache.setFramework(frameworkId, frameworkHierarchy)
      val hierarchy = ScalaJsonUtils.serialize(frameworkHierarchy)
      if (Platform.getBoolean("service.db.cassandra.enabled", true)) {
        val req = new Request(request)
        req.put("hierarchy", hierarchy)
        req.put("identifier", frameworkId)
        oec.graphService.saveExternalProps(req)
      } else RedisCache.set("fw:" + frameworkId, hierarchy)
      ResponseHandler.OK.put(Constants.PUBLISH_STATUS,
        s"Publish Event for Framework Id '$frameworkId' is pushed Successfully!")
    }
  }

  // ─── Operations ──────────────────────────────────────────────────────────────

  @throws[Exception]
  private def create(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    val code    = request.getRequest.getOrDefault(Constants.CODE,    "").asInstanceOf[String]
    val channel = request.getRequest.getOrDefault(Constants.CHANNEL, "").asInstanceOf[String]
    if (StringUtils.isNotBlank(code) && StringUtils.isNotBlank(channel)) {
      request.getRequest.put(Constants.IDENTIFIER, code)
      readChannelNode(request, channel).flatMap { node =>
        if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, channel)) {
          FrameworkManager.validateTranslationMap(request)
          DataNode.create(request).map { frameNode =>
            ResponseHandler.OK
              .put(Constants.NODE_ID, frameNode.getIdentifier)
              .put("versionKey", frameNode.getMetadata.get("versionKey"))
          }
        } else throw new ClientException("ERR_INVALID_CHANNEL_ID", "Please provide valid channel identifier")
      }
    } else throw new ClientException("ERR_INVALID_REQUEST", "Invalid Request. Please Provide Required Properties!")
  }

  @throws[Exception]
  private def read(request: Request): Future[Response] = {
    val frameworkId    = request.get("identifier").asInstanceOf[String]
    val returnCategories: java.util.List[String] = request.get("categories").asInstanceOf[String]
      .split(",")
      .filter(c => StringUtils.isNotBlank(c) && !StringUtils.equalsIgnoreCase(c, "null"))
      .toList.asJava
    request.getRequest.put("categories", returnCategories)

    if (StringUtils.isNotBlank(frameworkId)) {
      val cached = FrameworkCache.get(frameworkId, returnCategories)
      if (cached != null) {
        Future(ResponseHandler.OK.put(Constants.FRAMEWORK, cached))
      } else {
        fetchFrameworkFromStorage(request, frameworkId).flatMap { framework =>
          if (framework.isEmpty) {
            DataNode.read(request).map { node =>
              if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, frameworkId)) {
                val fw = NodeUtil.serialize(node, null,
                  request.getContext.get(Constants.SCHEMA_NAME).asInstanceOf[String],
                  request.getContext.get(Constants.VERSION).asInstanceOf[String])
                ResponseHandler.OK.put(Constants.FRAMEWORK, fw)
              } else throw new ClientException("ERR_INVALID_REQUEST", "Invalid Request. Please Provide Required Properties!")
            }
          } else {
            Future {
              val filterFrameworkData = FrameworkManager.filterFrameworkCategories(framework.asJava, returnCategories)
              FrameworkCache.save(filterFrameworkData, returnCategories)
              ResponseHandler.OK.put(Constants.FRAMEWORK, filterFrameworkData.asJava)
            }
          }
        }
      }
    } else throw new ClientException("ERR_INVALID_REQUEST", "Invalid Request. Please Provide Required Properties!")
  }

  @throws[Exception]
  private def update(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    DataNode.update(request).map(node =>
      ResponseHandler.OK.put("node_id", node.getIdentifier).put("versionKey", node.getMetadata.get("versionKey"))
    )
  }

  @throws[Exception]
  private def retire(request: Request): Future[Response] = {
    request.getRequest.put("status", "Retired")
    DataNode.update(request).map(node =>
      ResponseHandler.OK.put("node_id", node.getIdentifier).put("identifier", node.getIdentifier)
    )
  }

  @throws[Exception]
  private def publish(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    val frameworkId = request.getRequest.getOrDefault(Constants.IDENTIFIER, "").asInstanceOf[String]
    val channel     = request.getRequest.getOrDefault(Constants.CHANNEL,     "").asInstanceOf[String]
    readChannelNode(request, channel).flatMap { node =>
      if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, channel)) {
        val name        = node.getMetadata.getOrDefault("name",        "").asInstanceOf[String]
        val description = node.getMetadata.getOrDefault("description", "").asInstanceOf[String]
        request.getRequest.putAll(Map("name" -> name, "description" -> description).asJava)
        if (StringUtils.isNotBlank(frameworkId))
          persistFrameworkHierarchy(request, frameworkId)
        else
          throw new ClientException("ERR_INVALID_FRAMEWORK_ID", "Please provide valid framework identifier")
      } else throw new ClientException("ERR_INVALID_CHANNEL_ID", "Please provide valid channel identifier")
    }
  }

  //TODO:
  private def copy(request: Request): Future[Response] = {
    RequestUtil.restrictProperties(request)
    FrameworkManager.copyHierarchy(request)
  }
}
