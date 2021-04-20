package org.sunbird.utils

import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.DefinitionNode

import scala.concurrent.ExecutionContext
import scala.collection.JavaConversions._

object RequestUtil {

	private val SYSTEM_UPDATE_ALLOWED_CONTENT_STATUS = List("Live", "Unlisted")

	def restrictProperties(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Unit = {
		val graphId = request.getContext.getOrDefault("graph_id","").asInstanceOf[String]
		val version = request.getContext.getOrDefault("version","").asInstanceOf[String]
		val objectType = request.getContext.getOrDefault("objectType", "").asInstanceOf[String]
		val schemaName = request.getContext.getOrDefault("schemaName","").asInstanceOf[String]
		val operation = request.getOperation.toLowerCase.replace(objectType.toLowerCase, "")
		val restrictedProps =DefinitionNode.getRestrictedProperties(graphId, version, operation, schemaName)
		if (restrictedProps.exists(prop => request.getRequest.containsKey(prop))) throw new ClientException("ERROR_RESTRICTED_PROP", "Properties in list " + restrictedProps.mkString("[", ", ", "]") + " are not allowed in request")
	}

	def validateRequest(request: Request): Unit = {
		if (request.getRequest.isEmpty)
			throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), s"Request Body cannot be Empty.")

		if (request.get("status") != null && SYSTEM_UPDATE_ALLOWED_CONTENT_STATUS.contains(request.get("status").asInstanceOf[String]))
			throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), s"Cannot update content status to : ${SYSTEM_UPDATE_ALLOWED_CONTENT_STATUS.mkString("[", ", ", "]")}.")

	}
}
