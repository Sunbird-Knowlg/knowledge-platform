package controllers.v4

import akka.actor.{ActorRef, ActorSystem}
import controllers.BaseController
import play.api.mvc.ControllerComponents
import utils.{ActorNames, ApiId, MovieOperations}

import javax.inject.{Inject, Named}
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.ExecutionContext

class MovieController @Inject()(@Named(ActorNames.MOVIE_ACTOR) movieActor: ActorRef, cc: ControllerComponents, actorSystem: ActorSystem)
							   (implicit exec: ExecutionContext) extends BaseController(cc) {
	val objectType = "Movie"
	val schemaName: String = "movie"
	val version = "1.0"

	def create() = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val movie = body.getOrDefault("movie", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
		movie.putAll(headers)
		val movieRequest = getRequest(movie, headers, MovieOperations.createMovie.toString)
		setRequestContext(movieRequest, version, objectType, schemaName)
		getResult(ApiId.CREATE_MOVIE, movieActor, movieRequest)
	}

	def read(identifier: String, mode: Option[String], fields: Option[String]) = Action.async { implicit request =>
		val headers = commonHeaders()
		val movie = new java.util.HashMap().asInstanceOf[java.util.Map[String, Object]]
		movie.putAll(headers)
		movie.putAll(Map("identifier" -> identifier, "fields" -> fields.getOrElse(""), "mode" -> mode.getOrElse("read")).asJava)
		val movieRequest = getRequest(movie, headers, MovieOperations.readMovie.toString)
		setRequestContext(movieRequest, version, objectType, schemaName)
		getResult(ApiId.READ_MOVIE, movieActor, movieRequest)
	}

	def update(identifier: String) = Action.async { implicit request =>
		val headers = commonHeaders()
		val body = requestBody()
		val movie = body.getOrDefault("movie", new java.util.HashMap()).asInstanceOf[java.util.Map[String, Object]];
		movie.putAll(headers)
		val movieRequest = getRequest(movie, headers, MovieOperations.updateMovie.toString)
		setRequestContext(movieRequest, version, objectType, schemaName)
		movieRequest.getContext.put("identifier", identifier)
		getResult(ApiId.UPDATE_MOVIE, movieActor, movieRequest)
	}
}
