package actors

import java.util

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.dac.model.Relation
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
		val readReq = new Request();
		readReq.setContext(request.getContext)
		readReq.put("identifier", identifier)
		readReq.put("fields", new util.ArrayList[String])
		DataNode.read(readReq).map(node => {
			if (CollectionUtils.isNotEmpty(node.getOutRelations)) {
				//process relations with AssessmentItem
				val itemRels: util.List[Relation] = node.getOutRelations.filter((rel: Relation) => StringUtils.equalsAnyIgnoreCase("AssessmentItem", rel.getEndNodeObjectType)).filterNot((reln: Relation) => StringUtils.equalsAnyIgnoreCase("Retired", reln.getEndNodeMetadata.get("status").toString))
				val draftRelIds: List[String] = itemRels.filter((rel: Relation) => StringUtils.equalsAnyIgnoreCase("Draft", rel.getEndNodeMetadata.get("status").toString)).map(rel => rel.getEndNodeId).toList
				if (CollectionUtils.isNotEmpty(draftRelIds)) {
					//TODO: Bulk Update of AssessmentItem - update the status to Review for all draft Ids
				}
				val newRels: util.List[util.HashMap[String, AnyRef]] = itemRels.sortBy((rel: Relation) => rel.getMetadata.get("IL_SEQUENCE_INDEX").asInstanceOf[Long])(Ordering.Long).map(rel => {
					new util.HashMap[String, AnyRef]() {{
							put("identifier", rel.getEndNodeId);
						}}
				}).toList
				request.put("items", newRels);
			}
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
