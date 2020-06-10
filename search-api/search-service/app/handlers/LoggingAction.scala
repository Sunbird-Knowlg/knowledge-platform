package handlers

import com.google.inject.Inject
import org.sunbird.common.JsonUtils
import org.sunbird.telemetry.logger.TelemetryManager
import play.api.Logging
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class LoggingAction @Inject() (parser: BodyParsers.Default)(implicit ec: ExecutionContext)
        extends ActionBuilderImpl(parser)
                with Logging {

    override def invokeBlock[A](request: play.api.mvc.Request[A], block: (Request[A]) => Future[Result]) = {
        TelemetryManager.info("Search Request : " + requestBody(request.asInstanceOf[Request[AnyContent]]))
        block(request)
    }

    def requestBody(request: Request[AnyContent]) = {
        val body = request.body.asJson.getOrElse("{}").toString
        JsonUtils.deserialize(body, classOf[java.util.Map[String, Object]])
    }
}
