package org.sunbird.actors

import java.util
import java.util.concurrent.CompletionException

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.common.{JsonUtils, Slug}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.schema.DefinitionNode
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.utils.{Constants, RequestUtil}

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class ObjectCategoryDefinitionActor @Inject()(implicit oec: OntologyEngineContext) extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = {
		request.getOperation match {
			case Constants.CREATE_OBJECT_CATEGORY_DEFINITION => create(request)
			case Constants.READ_OBJECT_CATEGORY_DEFINITION => read(request)
			case Constants.UPDATE_OBJECT_CATEGORY_DEFINITION => update(request)
			case _ => ERROR(request.getOperation)
		}
	}

	private def create(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		val categoryId = request.getRequest.getOrDefault(Constants.CATEGORY_ID, "").asInstanceOf[String]
		val channelId = request.getRequest.getOrDefault(Constants.CHANNEL, "all").asInstanceOf[String]
		val targetObjectType = request.getRequest.getOrDefault(Constants.TARGET_OBJECT_TYPE, "").asInstanceOf[String]
		if (StringUtils.isNotBlank(categoryId) && (StringUtils.isNotBlank(targetObjectType) && StringUtils.isNotBlank(channelId))) {
			val identifier = categoryId + "_" + Slug.makeSlug(targetObjectType) + "_" + Slug.makeSlug(channelId)
			request.put(Constants.IDENTIFIER, identifier)
			val getCategoryReq = new Request()
			getCategoryReq.setContext(new util.HashMap[String, AnyRef](){{
				putAll(request.getContext)
			}})
			getCategoryReq.getContext.put(Constants.SCHEMA_NAME, Constants.OBJECT_CATEGORY_SCHEMA_NAME)
			getCategoryReq.getContext.put(Constants.VERSION, Constants.OBJECT_CATEGORY_SCHEMA_VERSION)
			getCategoryReq.put(Constants.IDENTIFIER, categoryId)
			getCategoryReq.put("fields", new util.ArrayList[String])
			DataNode.read(getCategoryReq).map(node => {
				if (null != node && StringUtils.equalsAnyIgnoreCase(node.getIdentifier, categoryId)) {
					val name = node.getMetadata.getOrDefault("name", "").asInstanceOf[String]
					val description = node.getMetadata.getOrDefault("description", "").asInstanceOf[String]
					request.getRequest.putAll(Map("name" -> name, "description" -> description).asJava)
					convertExternalProperties(request, request.getRequest)
					DataNode.create(request).map(node => {
						val response = ResponseHandler.OK
						response.put(Constants.IDENTIFIER, node.getIdentifier)
						response
					})
				} else throw new ClientException("ERR_INVALID_CATEGORY_ID", "Please provide valid category identifier")
			}).flatMap(f => f)
		} else throw new ClientException("ERR_INVALID_REQUEST", "Invalid Request. Please Provide Required Properties!")
	}

	private def read(request: Request): Future[Response] = {
		val requestMethod = request.getRequest.getOrDefault("REQ_METHOD", "").asInstanceOf[String]
		if(StringUtils.equalsAnyIgnoreCase("POST", requestMethod)) {
			val channel = request.getRequest.getOrDefault("channel", "all").asInstanceOf[String]
			val categoryName = request.getRequest.getOrDefault("name", "").asInstanceOf[String]
			val objectType = request.getRequest.getOrDefault("objectType", "").asInstanceOf[String]
			if(StringUtils.isBlank(categoryName) || StringUtils.isBlank(objectType))
				throw new ClientException("ERR_INVALID_REQUEST", "Please provide required properties!")
			val identifier = Constants.CATEGORY_PREFIX + Slug.makeSlug(categoryName) + "_" + Slug.makeSlug(objectType) + "_" + Slug.makeSlug(channel)
			request.getRequest.put(Constants.IDENTIFIER, identifier)
		}
		val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get(Constants.FIELDS).asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
		request.getRequest.put(Constants.FIELDS, fields)
		DataNode.read(request) recoverWith {
			case e: ResourceNotFoundException => {
				val id = request.get(Constants.IDENTIFIER).asInstanceOf[String]
				println("ObjectCategoryDefinitionActor ::: read ::: node not found with id :" + id + " | Fetching node with _all")
				if (StringUtils.equalsAnyIgnoreCase("POST", requestMethod) && !StringUtils.endsWithIgnoreCase(id, "_all")) {
					request.put(Constants.IDENTIFIER, id.replace(id.substring(id.lastIndexOf("_") + 1), "all"))
					DataNode.read(request)
				} else
					throw e
			}
			case ex: Throwable => throw ex
		} map (node => {
			val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, request.getContext.get(Constants.SCHEMA_NAME).asInstanceOf[String], request.getContext.get(Constants.VERSION).asInstanceOf[String])
			convertExternalProperties(request, metadata)
			val response: Response = ResponseHandler.OK
			response.put(Constants.OBJECT_CATEGORY_DEFINITION, metadata)
			response
		})
	}

	private def update(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		convertExternalProperties(request, request.getRequest)
		DataNode.update(request).map(node => {
			val response: Response = ResponseHandler.OK
			response.put(Constants.IDENTIFIER, node.getIdentifier)
			response
		})
	}

	private def convertExternalProperties(request: Request, dataMap: util.Map[String, AnyRef]): Unit = {
		val extPropNameList = DefinitionNode.getExternalProps(request.getContext.get("graph_id").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String],
			request.getContext.get("schemaName").asInstanceOf[String])
		extPropNameList.filter(prop => dataMap.containsKey(prop)).foreach(prop => {
			val value = dataMap.get(prop).asInstanceOf[java.util.Map[String, AnyRef]]
			value.asScala.foreach(entry => {
				if(StringUtils.equals(request.getOperation, Constants.READ_OBJECT_CATEGORY_DEFINITION)) {
					val internalVal = JsonUtils.deserialize(entry._2.asInstanceOf[String], classOf[util.Map[String, AnyRef]] )
					value.put(entry._1, internalVal)
				} else {
					val internalVal = JsonUtils.serialize(entry._2)
					value.put(entry._1, internalVal)
				}
			})
			dataMap.put(prop, value)
		})
	}

}
