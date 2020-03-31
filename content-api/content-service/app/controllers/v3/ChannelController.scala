package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext}

@Singleton
class ChannelController  @Inject()(@Named(ActorNames.CHANNEL_ACTOR) channelActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "Channel"
  val schemaName: String = "channel"
  val version = "1.0"

  def create() = Action.async { implicit request =>
    val headers = commonHeaders(Option(List("x-channel-id")))
    val body = requestBody()
    val channel = body.getOrDefault("channel", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    channel.putAll(headers)
    val channelRequest = getRequest(channel, headers, "createChannel")
    setRequestContext(channelRequest, version, objectType, schemaName)
    getResult(ApiId.CREATE_CHANNEL, channelActor, channelRequest)
  }

  def read(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders()
    val channel = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    channel.put("identifier", identifier)
    channel.putAll(headers)
    val readRequest = getRequest(channel, headers, "readChannel")
    setRequestContext(readRequest, version, objectType, schemaName)
    getResult(ApiId.READ_CHANNEL, channelActor, readRequest)
  }

  def update(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders(Option(List("x-channel-id")))
    val body = requestBody()
    val channel = body.getOrElse("channel", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]]
    channel.putAll(headers)
    val channelRequest = getRequest(channel, headers, "updateChannel")
    setRequestContext(channelRequest, version, objectType, schemaName)
    channelRequest.getContext.put("identifier", identifier);
    getResult(ApiId.UPDATE_CHANNEL, channelActor, channelRequest)
  }

  def retire(identifier: String) = Action.async { implicit request =>
    val headers = commonHeaders(Option(List("x-channel-id")))
    val channel =  new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    channel.putAll(headers)
    val channelRequest = getRequest(channel, headers, "retireChannel")
    setRequestContext(channelRequest, version, objectType, schemaName)
    channelRequest.getContext.put("identifier", identifier);
    getResult(ApiId.RETIRE_CHANNEL, channelActor, channelRequest)
  }
}

