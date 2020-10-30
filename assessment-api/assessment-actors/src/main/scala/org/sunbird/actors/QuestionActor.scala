package org.sunbird.actors

import javax.inject.Inject
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext

import scala.concurrent.{ExecutionContext, Future}

class QuestionActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = request.getOperation match {
		case "createQuestion" => create(request)
		case "readQuestion" => read(request)
		case "updateQuestion" => update(request)
		case "reviewQuestion" => review(request)
		case "publishQuestion" => publish(request)
		case "retireQuestion" => retire(request)
		case _ => ERROR(request.getOperation)
	}

	def create(request: Request): Future[Response] = {
		//TODO: Remove mock response
		val response = ResponseHandler.OK
		response.put("identifier", "do_1234")
		Future{response}
	}

	def read(request: Request): Future[Response] = ???

	def update(request: Request): Future[Response] = ???

	def review(request: Request): Future[Response] = ???

	def publish(request: Request): Future[Response] = ???

	def retire(request: Request): Future[Response] = ???
}
