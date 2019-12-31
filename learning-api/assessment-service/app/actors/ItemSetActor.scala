package actors

import java.util
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import utils.ItemSetOperations

import scala.collection.JavaConversions._
import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}

class ItemSetActor extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = {
		val operation: String = request.getOperation
		if (ItemSetOperations.createItemSet.toString == operation) create(request)
		else if (ItemSetOperations.readItemSet.toString == operation) read(request)
		else if (ItemSetOperations.updateItemSet.toString == operation) update(request)
		else if (ItemSetOperations.reviewItemSet.toString == operation) review(request)
		else if (ItemSetOperations.retireItemSet.toString == operation) retire(request)
		else ERROR(operation)
	}

	def create(request: Request): Future[Response] = DataNode.create(request).map(node => {
		val response = ResponseHandler.OK
		response.put("identifier", node.getIdentifier)
		response
	})

	def read(request: Request): Future[Response] = {
		val fields: util.List[String] = JavaConverters.asJavaCollectionConverter(request.get("fields").asInstanceOf[String].split(",").toList.filter(field => StringUtils.isNoneBlank(field))).asJavaCollection.toList
		request.getRequest.put("fields", fields)
		DataNode.read(request).map(node => {
			val metadata = NodeUtil.serialize(node, fields, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
			metadata.remove("versionKey")
			val response = ResponseHandler.OK
			response.put("itemset", metadata)
			response
		})
	}

	def update(request: Request): Future[Response] = DataNode.update(request).map(node => {
		val response: Response = ResponseHandler.OK
		response.put("identifier", node.getIdentifier)
		response
	})

	def review(request: Request): Future[Response] = {
		val identifier = request.getContext.get("identifier").asInstanceOf[String]
		request.put("identifier", identifier)
		request.put("fields", new util.ArrayList[String])
		DataNode.read(request).map(node => {
			// process the node relations here
			val relNodes = node.getRelationNodes
			println("relNodes ::" + relNodes)
			DataNode.update(request).map(node => {
				val response: Response = ResponseHandler.OK
				response.put("identifier", node.getIdentifier)
				response
			})
		}).flatMap(f => f)
	}

	def retire(request: Request): Future[Response] = {
		request.put("status", "Retired")
		DataNode.update(request).map(node => {
			val response: Response = ResponseHandler.OK
			response.put("identifier", node.getIdentifier)
			response
		})
	}


}
