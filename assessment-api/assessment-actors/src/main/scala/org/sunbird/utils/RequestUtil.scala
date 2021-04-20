package org.sunbird.utils

import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ErrorCodes, ResponseCode}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.schema.DefinitionNode

import scala.concurrent.ExecutionContext
import scala.collection.JavaConversions._

object RequestUtil {

	private val SYSTEM_UPDATE_RESTRICTED_PROPERTIES = List("screenshots")

	def restrictProperties(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Unit = {
		val graphId = request.getContext.getOrDefault("graph_id","").asInstanceOf[String]
		val version = request.getContext.getOrDefault("version","").asInstanceOf[String]
		val objectType = request.getContext.getOrDefault("objectType", "").asInstanceOf[String]
		val schemaName = request.getContext.getOrDefault("schemaName","").asInstanceOf[String]
		val operation = request.getOperation.toLowerCase.replace(objectType.toLowerCase, "")
		val restrictedProps =DefinitionNode.getRestrictedProperties(graphId, version, operation, schemaName)
		if (restrictedProps.exists(prop => request.getRequest.containsKey(prop))) throw new ClientException("ERROR_RESTRICTED_PROP", "Properties in list " + restrictedProps.mkString("[", ", ", "]") + " are not allowed in request")
	}

	def validateNode(nodes: java.util.List[Node], objectType: String, identifier: String): Unit = {
		if(nodes.isEmpty)
		throw new ClientException(ResponseCode.RESOURCE_NOT_FOUND.name(), s"Error! Node(s) doesn't Exists with identifier : $identifier.")

		nodes.foreach(node => {
			if (node.getMetadata == null && !objectType.equalsIgnoreCase(node.getObjectType) && node.getMetadata.get("status").asInstanceOf[String].equalsIgnoreCase("failed"))
				throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), s"Cannot update content with FAILED status for id : ${node.getIdentifier}.")
		})
	}

	def validateRequest(request: Request): Unit = {
		SYSTEM_UPDATE_RESTRICTED_PROPERTIES.foreach(prop => {
			ResponseCode.CLIENT_ERROR
			if (request.get(prop) != null) throw new ClientException(ErrorCodes.ERR_BAD_REQUEST.name(), s"Properties in list ${SYSTEM_UPDATE_RESTRICTED_PROPERTIES.mkString("[", ", ", "]")} are not allowed in request.")
		})
	}
}
