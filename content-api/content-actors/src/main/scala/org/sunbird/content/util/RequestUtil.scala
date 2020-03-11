package org.sunbird.content.util

import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.schema.DefinitionNode

object RequestUtil {

	def restrictProperties(request: Request): Unit = {
		val graphId = request.getContext.getOrDefault("graph_id","").asInstanceOf[String]
		val version = request.getContext.getOrDefault("version","").asInstanceOf[String]
		val objectType = request.getContext.getOrDefault("objectType", "").asInstanceOf[String]
		val schemaName = request.getContext.getOrDefault("schemaName","").asInstanceOf[String]
		val operation = request.getOperation.toLowerCase.replace(objectType.toLowerCase, "")
		val restrictedProps =DefinitionNode.getRestrictedProperties(graphId, version, operation, schemaName)
		if (restrictedProps.exists(prop => request.getRequest.containsKey(prop))) throw new ClientException("ERROR_RESTRICTED_PROP", "Properties in list " + restrictedProps + " are not allowed in request")
	}
}
