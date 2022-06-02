package org.sunbird.movie.actors

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.util.RequestUtil

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.{ExecutionContext, Future}

class MovieActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor{
	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = {
		request.getOperation match {
			case "createMovie" => create(request)
			case "readMovie" => read(request)
			/*
			case "updateMovie" => update(request)

			case "removeMovie" => remove(request)

			 */
			case _ => ERROR(request.getOperation)
		}
	}

	def create(request: Request): Future[Response]={
		RequestUtil.restrictProperties(request)
		DataNode.create(request).map(node =>{
			val response = ResponseHandler.OK
			response.putAll(Map("identifier"-> node.getIdentifier, "versionKey" -> node.getMetadata.get("versionKey")).asJava)
			response
		})
	}

	def read(request: Request): Future[Response]={
		val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
		request.getRequest.put("fields", fields)
		DataNode.read(request).map(node => {
			val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, node.getObjectType.toLowerCase.replace("Image", ""), request.getContext.get("version").asInstanceOf[String])
			metadata.put("identifier", node.getIdentifier.replace(".img", ""))
			ResponseHandler.OK.put("movie", metadata)
		})
	}

	/*
	def update(request: Request): Future[Response]={
		RequestUtil.restrictProperties(request)
	}

	def remove(request: Request): Future[Response]={
		RequestUtil.restrictProperties(request)

	}

	 */
}
