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
class ChannelController  @Inject()(@Named(ActorNames.CONTENT_ACTOR) contentActor: ActorRef,@Named(ActorNames.COLLECTION_ACTOR) collectionActor: ActorRef,@Named(ActorNames.CHANNEL_ACTOR) channelActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "Channel"
  val schemaName: String = "channel"
  val version = "1.0"

  def create() = Action.async { implicit request =>

    val result = ResponseHandler.OK()
    val response = JavaJsonUtils.serialize(result)
    Future(Ok(response).as("application/json"))
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
}

