package actors

import akka.dispatch.Futures
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}

import scala.concurrent.Future

class HealthActor extends BaseActor {

	@throws[Throwable]
	override def onReceive(request: Request): Future[Response] = {
		val result = ResponseHandler.OK
		result.put("healthy", true)
		Futures.successful(result)
	}
}
