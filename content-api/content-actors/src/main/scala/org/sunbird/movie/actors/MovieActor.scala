package org.sunbird.movie.actors

import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.util.RequestUtil

import javax.inject.Inject
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.{ExecutionContext, Future}

class MovieActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor{
	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = {
		request.getOperation match {
			case "addMovie" => add(request)
			/*
			case "updateMovie" => update(request)
			case "readMovie" => read(request)
			case "removeMovie" => remove(request)

			 */
			case _ => ERROR(request.getOperation)
		}
	}

	def add(request: Request): Future[Response]={
		RequestUtil.restrictProperties(request)
		DataNode.create(request).map(node =>{
			val response = ResponseHandler.OK
			response.putAll(Map("identifier"-> node.getIdentifier, "versionKey" -> node.getMetadata.get("versionKey")).asJava)
			response
		})
	}

	/*
	def update(request: Request): Future[Response]={
		RequestUtil.restrictProperties(request)
	}

	def read(request: Request): Future[Response]={
		RequestUtil.restrictProperties(request)
	}

	def remove(request: Request): Future[Response]={
		RequestUtil.restrictProperties(request)

	}

	 */
}
