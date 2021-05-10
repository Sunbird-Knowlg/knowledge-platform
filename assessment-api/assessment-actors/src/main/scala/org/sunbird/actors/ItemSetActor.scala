package org.sunbird.actors

import java.util

import javax.inject.Inject
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Relation
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.parseq.Task

import scala.collection.JavaConversions._
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.concurrent.{ExecutionContext, Future}

class ItemSetActor @Inject() (implicit oec: OntologyEngineContext) extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = request.getOperation match {
		case "createItemSet" => create(request)
		case "readItemSet" => read(request)
		case "updateItemSet" => update(request)
		case "reviewItemSet" => review(request)
		case "retireItemSet" => retire(request)
		case _ => ERROR(request.getOperation)
	}


	def create(request: Request): Future[Response] = DataNode.create(request).map(node => {
		ResponseHandler.OK.put("identifier", node.getIdentifier)
	})

	def read(request: Request): Future[Response] = {
		val fields = request.getRequest.getOrDefault("fields", "").asInstanceOf[String]
		  .split(",").filter((field: String) => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null")).toList.asJava
		request.getRequest.put("fields", fields)
		DataNode.read(request).map(node => {
			val metadata = NodeUtil.serialize(node, fields, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
			metadata.remove("versionKey")
			ResponseHandler.OK.put("itemset", metadata)
		})
	}

	def update(request: Request): Future[Response] = DataNode.update(request).map(node => {
		ResponseHandler.OK.put("identifier", node.getIdentifier)
	})

	def review(request: Request): Future[Response] = {
		val identifier = request.getContext.get("identifier").asInstanceOf[String]
		var flag = false
		val readReq = new Request();
		val reqContext = request.getContext
		readReq.setContext(reqContext)
		readReq.put("identifier", identifier)
		readReq.put("fields", new util.ArrayList[String])
		val updateReq = new Request()
		updateReq.setContext(reqContext)
		DataNode.read(readReq).map(node => {
			if (CollectionUtils.isNotEmpty(node.getOutRelations)) {
				//process relations with AssessmentItem
				val itemRels: util.List[Relation] = node.getOutRelations.filter((rel: Relation) => StringUtils.equalsAnyIgnoreCase("AssessmentItem", rel.getEndNodeObjectType)).filterNot((reln: Relation) => StringUtils.equalsAnyIgnoreCase("Retired", reln.getEndNodeMetadata.get("status").toString))
				val draftRelIds: List[String] = itemRels.filter((rel: Relation) => StringUtils.equalsAnyIgnoreCase("Draft", rel.getEndNodeMetadata.get("status").toString)).map(rel => rel.getEndNodeId).toList
				if (CollectionUtils.isNotEmpty(draftRelIds)) {
					updateReq.put("identifiers", draftRelIds.asJava)
					updateReq.put("metadata", new util.HashMap[String, AnyRef]() {{put("status", "Review")}})
					flag = true
				}
				val newRels: util.List[util.HashMap[String, AnyRef]] = itemRels.sortBy((rel: Relation) => rel.getMetadata.get("IL_SEQUENCE_INDEX").asInstanceOf[Long])(Ordering.Long).map(rel => {
					new util.HashMap[String, AnyRef]() {{put("identifier", rel.getEndNodeId);}}}).toList
				request.put("items", newRels);
			}
			request.put("status", "Review")
			val func = flag match {
				case true => DataNode.bulkUpdate(updateReq).map(f => ResponseHandler.OK())
				case false => Future(ResponseHandler.OK())
			}
			val futureList = Task.parallel[Response](func,
				DataNode.update(request).map(node => {
					ResponseHandler.OK.put("identifier", node.getIdentifier)
				}))
			futureList
		}).flatMap(f => f).map(f => f.get(1))
	}

	def retire(request: Request): Future[Response] = {
		request.put("status", "Retired")
		DataNode.update(request).map(node => {
			ResponseHandler.OK.put("identifier", node.getIdentifier)
		})
	}


}
