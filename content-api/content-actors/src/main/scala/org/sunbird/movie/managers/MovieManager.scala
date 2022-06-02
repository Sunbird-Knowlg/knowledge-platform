package org.sunbird.movie.managers

import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.concurrent.{ExecutionContext, Future}

object MovieManager {
	def getValidatedNodeForUpdate(request: Request, errCode: String)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = DataNode.read(request)

	def updateNode(request: Request)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
		DataNode.update(request).map(node => {
			ResponseHandler.OK.putAll(Map("identifier" -> node.getIdentifier.replace(".img", ""), "versionKey" -> node.getMetadata.get("versionKey")).asJava)
		})
	}
}
