package controllers.taxonomy.v3

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import com.google.inject.Singleton
import controllers.BaseController
import utils.Constants
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId}

import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext

@Singleton
class LockController @Inject()(@Named(ActorNames.LOCK_ACTOR) lockActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val objectType = "Lock"
  def createLock() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    body.putAll(headers)
    val lockRequest = getRequest(body, headers, Constants.CREATE_LOCK)
    setRequestContext(lockRequest, Constants.LOCK_SCHEMA_VERSION, objectType, Constants.LOCK_SCHEMA_NAME)
    getResult(ApiId.CREATE_LOCK, lockActor, lockRequest)
  }

  def refreshLock() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    body.putAll(headers)
    val lockRequest = getRequest(body, headers, Constants.REFRESH_LOCK)
    setRequestContext(lockRequest, Constants.LOCK_SCHEMA_VERSION, objectType, Constants.LOCK_SCHEMA_NAME)
    getResult(ApiId.REFRESH_LOCK, lockActor, lockRequest)
  }

  def retireLock() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    body.putAll(headers)
    val lockRequest = getRequest(body, headers, Constants.RETIRE_LOCK)
    setRequestContext(lockRequest, Constants.LOCK_SCHEMA_VERSION, objectType, Constants.LOCK_SCHEMA_NAME)
    getResult(ApiId.RETIRE_LOCK, lockActor, lockRequest)
  }

  def listLock() = Action.async { implicit request =>
    val headers = commonHeaders()
    val body = requestBody()
    body.putAll(headers)
    val lockRequest = getRequest(body, headers, Constants.LIST_LOCK)
    setRequestContext(lockRequest, Constants.LOCK_SCHEMA_VERSION, objectType, Constants.LOCK_SCHEMA_NAME)
    getResult(ApiId.LIST_LOCK, lockActor, lockRequest)
  }

}
