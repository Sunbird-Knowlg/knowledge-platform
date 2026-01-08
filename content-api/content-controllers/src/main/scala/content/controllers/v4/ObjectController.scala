package content.controllers.v4

import content.controllers.BaseController
import content.utils.{ActorNames, ApiId}
import org.apache.pekko.actor.ActorRef
import scala.jdk.CollectionConverters._
import play.api.mvc.ControllerComponents
import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext

class ObjectController  @Inject()(@Named(ActorNames.OBJECT_ACTOR) objectActor: ActorRef, cc: ControllerComponents)(implicit exec: ExecutionContext) extends BaseController(cc) {

  val version = "1.0"
  val apiVersion = "4.0"

  def read(identifier: String, fields: Option[String]) = Action.async { implicit request =>
    val headers = commonHeaders()
    val app = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
    app.putAll(headers)
    app.putAll(Map("identifier" -> identifier, "mode" -> "read", "fields" -> fields.getOrElse("")).asJava)
    val readRequest = getRequest(app, headers, "readObject")
    setRequestContext(readRequest, version,"Content","content")
    getResult(ApiId.READ_OBJECT, objectActor, readRequest, version = apiVersion)
  }
}
