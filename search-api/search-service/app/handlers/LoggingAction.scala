package handlers

import com.google.inject.Inject
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.telemetry.logger.TelemetryManager
import play.api.Logging
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class LoggingAction @Inject() (parser: BodyParsers.Default)(implicit ec: ExecutionContext)
        extends ActionBuilderImpl(parser)
                with Logging {
    val logEnabled = (Platform.config.hasPath("search.payload.log_enable") && Platform.config.getBoolean("search.payload.log_enable"))

    override def invokeBlock[A](request: play.api.mvc.Request[A], block: (Request[A]) => Future[Result]) = {
        if(logEnabled)
            TelemetryManager.logRequestBody(requestBody(request.asInstanceOf[Request[AnyContent]]))
        block(request)
    }

    def requestBody(request: Request[AnyContent]) = {
        request.body.asJson.getOrElse("{}").toString
    }
}
