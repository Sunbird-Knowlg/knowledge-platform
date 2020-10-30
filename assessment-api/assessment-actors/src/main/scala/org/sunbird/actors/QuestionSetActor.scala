package org.sunbird.actors

import javax.inject.Inject
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.OntologyEngineContext

import scala.concurrent.{ExecutionContext, Future}

class QuestionSetActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = request.getOperation match {
		case "createQuestionSet" => create(request)
		case "readQuestionSet" => read(request)
		case "updateQuestionSet" => update(request)
		case "reviewQuestionSet" => review(request)
		case "publishQuestionSet" => publish(request)
		case "retireQuestionSet" => retire(request)
		case "addQuestion" => add(request)
		case "removeQuestion" => remove(request)
		case _ => ERROR(request.getOperation)
	}

	def create(request: Request): Future[Response] = ???

	def read(request: Request): Future[Response] = ???

	def update(request: Request): Future[Response] = ???

	def review(request: Request): Future[Response] = ???

	def publish(request: Request): Future[Response] = ???

	def retire(request: Request): Future[Response] = ???

	def add(request: Request): Future[Response] = ???

	def remove(request: Request): Future[Response] = ???
}
