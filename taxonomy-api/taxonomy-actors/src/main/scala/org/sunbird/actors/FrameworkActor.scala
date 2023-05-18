package org.sunbird.actors

import org.apache.commons.collections4.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.Slug
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode

import org.sunbird.utils.{Constants, RequestUtil}

import java.util
import javax.inject.Inject
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class FrameworkActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher

  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case Constants.CREATE_FRAMEWORK => create(request)
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
      println("getChannelReq: " + getChannelReq)
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
}