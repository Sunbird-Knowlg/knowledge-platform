package controllers.v3

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import javax.inject.{Inject, Named}
import org.sunbird.common.dto.ResponseHandler
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, JavaJsonUtils}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ChannelController @Inject()(@Named(ActorNames.CHANNEL_ACTOR) channelActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc){

  val objectType = "Channel"
  val schemaName: String = "channel"
  val version = "1.0"

  def create() = Action.async { implicit request =>
    val headers = commonHeaders()
    headers.remove("channel")
    val body = requestBody()
    val channel = body.getOrDefault("channel", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
    channel.putAll(headers)
    val channelRequest = getRequest(channel, headers, "createChannel")
    setRequestContext(channelRequest, version, objectType, schemaName)
    getResult(ApiId.CREATE_CHANNEL, channelActor, channelRequest)
  }


  /**
   * This Api end point takes the parameters
   * Channel Identifier the unique identifier of a channel
   * @param identifier
   */
  def read(identifier: String) = Action.async { implicit request =>
    val result = ResponseHandler.OK()
    val response = JavaJsonUtils.serialize(result)
    Future(Ok(response).as("application/json"))
  }

  def update(identifier: String) = Action.async { implicit request =>
    val result = ResponseHandler.OK()
    val response = JavaJsonUtils.serialize(result)
    Future(Ok(response).as("application/json"))
  }

  def list() = Action.async { implicit request =>
    val result = ResponseHandler.OK()
    val response = JavaJsonUtils.serialize(result)
    Future(Ok(response).as("application/json"))
  }

  def retire(identifier: String) = Action.async { implicit request =>
    val result = ResponseHandler.OK()
    val response = JavaJsonUtils.serialize(result)
    Future(Ok(response).as("application/json"))
  }
}

