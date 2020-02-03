package org.sunbird.content.actors

import java.io.File
import java.util
import java.util.concurrent.CompletionException

import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cache.impl.RedisCache
import org.sunbird.common.ContentParams
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.{ClientException, ResponseCode}
import org.sunbird.content.util.{CopyOperation, RequestUtil}
import org.sunbird.graph.utils.NodeUtil
import org.sunbird.mimetype.factory.MimeTypeManagerFactory
import org.sunbird.common.dto.Response
import org.sunbird.common.dto.ResponseHandler
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode

import scala.collection.{JavaConverters, mutable}
import scala.concurrent.{ExecutionContext, Future}


class ContentActor extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	override def onReceive(request: Request): Future[Response] = {
		request.getOperation match {
			case "createContent" => create(request)
			case "readContent" => read(request)
			case "updateContent" => update(request)
			case "uploadContent" => upload(request)
			case "copy" => copy(request)
			case _ => ERROR(request.getOperation)
		}
	}

	def create(request: Request): Future[Response] = {
		populateDefaultersForCreation(request)
		RequestUtil.restrictProperties(request)
		DataNode.create(request).map(node => {
			val response = ResponseHandler.OK
			response.put("identifier", node.getIdentifier)
			response.put("versionKey", node.getMetadata.get("versionKey"))
			response
		})
	}

	def read(request: Request): Future[Response] = {
		val fields: util.List[String] = JavaConverters.seqAsJavaListConverter(request.get("fields").asInstanceOf[String].split(",").filter(field => StringUtils.isNotBlank(field) && !StringUtils.equalsIgnoreCase(field, "null"))).asJava
		request.getRequest.put("fields", fields)
		DataNode.read(request).map(node => {
			val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, request.getContext.get("schemaName").asInstanceOf[String], request.getContext.get("version").asInstanceOf[String])
			metadata.put("identifier", node.getIdentifier.replace(".img", ""))
			val response: Response = ResponseHandler.OK
			response.put("content", metadata)
			response
		})
	}

	def update(request: Request): Future[Response] = {
		populateDefaultersForUpdation(request)
		if (StringUtils.isBlank(request.getRequest.getOrDefault("versionKey", "").asInstanceOf[String])) throw new ClientException("ERR_INVALID_REQUEST", "Please Provide Version Key!")
		RequestUtil.restrictProperties(request)
		DataNode.update(request).map(node => {
			val response: Response = ResponseHandler.OK
			val identifier: String = node.getIdentifier.replace(".img", "")
			response.put("node_id", identifier)
			response.put("identifier", identifier)
			response.put("versionKey", node.getMetadata.get("versionKey"))
			response
		})
	}

	def upload(request: Request): Future[Response] = {
		val identifier: String = request.getContext.getOrDefault("identifier", "").asInstanceOf[String]
		val fileUrl: String = request.getRequest.getOrDefault("fileUrl", "").asInstanceOf[String]
		val file = request.getRequest.get("file").asInstanceOf[File]
		val readReq = new Request(request)
		readReq.put("identifier", identifier)
		readReq.put("fields", new util.ArrayList[String])
		DataNode.read(readReq).map(node => {
			val mimeType = node.getMetadata().getOrDefault("mimeType", "").asInstanceOf[String]
			val contentType = node.getMetadata.getOrDefault("contentType", "").asInstanceOf[String]
			val mgr = MimeTypeManagerFactory.getManager(contentType, mimeType)
			val uploadFuture: Future[Map[String, AnyRef]] = if (StringUtils.isNotBlank(fileUrl)) mgr.upload(identifier, node, fileUrl) else mgr.upload(identifier, node, file)
			uploadFuture.map(result => {
				val artifactUrl = result.getOrElse("artifactUrl", "").asInstanceOf[String]
				if (StringUtils.isNotBlank(artifactUrl)) {
					val updateReq = new Request(request)
					updateReq.getContext().put("identifier", identifier)
					updateReq.put("artifactUrl", artifactUrl)
					DataNode.update(updateReq).map(node => {
						val response: Response = ResponseHandler.OK
						val id = node.getIdentifier.replace(".img", "")
						val url = node.getMetadata.get("artifactUrl").asInstanceOf[String]
						response.put("node_id", id)
						response.put("identifier", id)
						response.put("artifactUrl", url)
						response.put("content_url", url)
						response.put("versionKey", node.getMetadata.get("versionKey"))
						response
					})
				} else {
					Future {
						ResponseHandler.ERROR(ResponseCode.SERVER_ERROR, "ERR_UPLOAD_FILE", "Something Went Wrong While Processing Your Request.")
					}
				}
			}).flatMap(f => f)
		}).flatMap(f => f)
	}

	def copy(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		DataNode.read(request).map(node => {
			copyNode(request, node).map(idMap => {
				val response = ResponseHandler.OK
				response.put("node_id", idMap)
				Future(response)
			}).flatMap(f => f)
		}).flatMap(f => f)
	}

	def populateDefaultersForCreation(request: Request) = {
		setDefaultsBasedOnMimeType(request, ContentParams.create.name)
		setDefaultLicense(request)
	}

	private def setDefaultLicense(request: Request): Unit = {
		if (StringUtils.isEmpty(request.getRequest.getOrDefault("license", "").asInstanceOf[String])) {
			val cacheKey = "channel_" + request.getRequest.getOrDefault("channel", "").asInstanceOf[String] + "_license"
			val defaultLicense = RedisCache.get(cacheKey, null, 0)
			if (StringUtils.isNotEmpty(defaultLicense)) request.getRequest.put("license", defaultLicense)
			else System.out.println("Default License is not available for channel: " + request.getRequest.getOrDefault("channel", "").asInstanceOf[String])
		}
	}

	def populateDefaultersForUpdation(request: Request) = {
		if (request.getRequest.containsKey(ContentParams.body.name)) request.put(ContentParams.artifactUrl.name, null)
	}

	private def setDefaultsBasedOnMimeType(request: Request, operation: String): Unit = {
		val mimeType = request.get(ContentParams.mimeType.name).asInstanceOf[String]
		if (StringUtils.isNotBlank(mimeType) && operation.equalsIgnoreCase(ContentParams.create.name)) {
			if (StringUtils.equalsIgnoreCase("application/vnd.ekstep.plugin-archive", mimeType)) {
				val code = request.get(ContentParams.code.name).asInstanceOf[String]
				if (null == code || StringUtils.isBlank(code)) throw new ClientException("ERR_PLUGIN_CODE_REQUIRED", "Unique code is mandatory for plugins")
				request.put(ContentParams.identifier.name, request.get(ContentParams.code.name))
			}
			else request.put(ContentParams.osId.name, "org.ekstep.quiz.app")
			if (mimeType.endsWith("archive") || mimeType.endsWith("vnd.ekstep.content-collection") || mimeType.endsWith("epub")) request.put(ContentParams.contentEncoding.name, ContentParams.gzip.name)
			else request.put(ContentParams.contentEncoding.name, ContentParams.identity.name)
			if (mimeType.endsWith("youtube") || mimeType.endsWith("x-url")) request.put(ContentParams.contentDisposition.name, ContentParams.online.name)
			else request.put(ContentParams.contentDisposition.name, ContentParams.inline.name)
		}
	}

	 def copyNode(request: Request, existingNode: Node): Future[util.HashMap[String, String]] = {
		val validatedExistingNode:Node = CopyOperation.validateCopyContentRequest(existingNode, request.getRequest, request.get("mode").asInstanceOf[String])
		existingNode.setGraphId(request.getContext.get("graph_id").asInstanceOf[String])
		val copyNode = CopyOperation.copyNode(validatedExistingNode, request.getRequest, request.getRequest.get("mode").asInstanceOf[String])
		request.getRequest.clear()
		request.setRequest(copyNode.getMetadata)
		 createCopyNode(request, existingNode).map(node => {
			 Future (new util.HashMap[String, String](){{put(existingNode.getIdentifier, node.getIdentifier)}})
		}).flatMap(f => f)
	}

	def createCopyNode(request: Request, existingNode: Node): Future[Node] = {
		DataNode.create(request).map(node => {
			request.getContext.put("identifier", node.getIdentifier)
			request.getRequest.clear()
			//request.getRequest.put("file" , existingNode.getMetadata.get("artifactUrl"))
			val file: File = CopyOperation.copyURLToFile(existingNode.getMetadata.get("artifactUrl").asInstanceOf[String])
			request.getRequest.put("file" , file)
			upload(request).map(response=>{
				Future(node)
			}).flatMap(f => f)
		}).flatMap(f => f)
	}
}
