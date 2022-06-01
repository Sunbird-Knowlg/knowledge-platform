package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import controllers.BaseController
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, MovieOperations}

import javax.inject.{Inject, Named}
import scala.concurrent.ExecutionContext

class MovieController @Inject()(@Named(ActorNames.MOVIE_ACTOR) movieActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)
							   (implicit exec: ExecutionContext) extends BaseController(cc) {
	val objectType = "Movie"
	val schemaName: String = "movie"
	val version = "1.0"

	def add() = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val movie = body.getOrDefault("movie", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
		movie.putAll(headers)
		val movieRequest = getRequest(movie,headers,MovieOperations.addMovie.toString)
		setRequestContext(movieRequest, version, objectType,schemaName)
		getResult(ApiId.ADD_MOVIE, movieActor, movieRequest)
	}
}
