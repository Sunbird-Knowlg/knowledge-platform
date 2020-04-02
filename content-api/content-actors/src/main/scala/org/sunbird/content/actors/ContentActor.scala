package org.sunbird.content.actors

import java.util
import java.util.concurrent.CompletionException
import java.io.File

import org.apache.commons.io.FilenameUtils
import javax.inject.Inject
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cache.impl.RedisCache
import org.sunbird.content.util.CopyManager
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.{ContentParams, DateUtils, Platform, Slug}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.util.RequestUtil
import org.sunbird.content.upload.mgr.UploadManager
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil

import scala.collection.JavaConverters
import scala.concurrent.{ExecutionContext, Future}

class ContentActor @Inject() (implicit oec: OntologyEngineContext, ss: StorageService) extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	private val CONTENT_OBJECT_TYPE = "Content"

	protected val FLAGGABLE_STATUS: util.List[String] = util.Arrays.asList("Live", "Unlisted", "Processing", "Flagged")


	override def onReceive(request: Request): Future[Response] = {
		request.getOperation match {
			case "createContent" => create(request)
			case "readContent" => read(request)
			case "updateContent" => update(request)
			case "uploadContent" => upload(request)
			case "copy" => copy(request)
			case "uploadPreSignedUrl" => uploadPreSignedUrl(request)
			case "flagContent" => flag(request)
			case _ => ERROR(request.getOperation)
		}
	}

	def create(request: Request): Future[Response] = {
		populateDefaultersForCreation(request)
		RequestUtil.restrictProperties(request)
		DataNode.create(request).map(node => {
			val response = ResponseHandler.OK
			response.put("identifier", node.getIdentifier)
			response.put("node_id", node.getIdentifier)
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
		val readReq = new Request(request)
		readReq.put("identifier", identifier)
		readReq.put("fields", new util.ArrayList[String])
		DataNode.read(readReq).map(node => {
			UploadManager.upload(request, node)
		}).flatMap(f => f)
	}

	def copy(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		CopyManager.copy(request)
	}

	def uploadPreSignedUrl(request: Request): Future[Response] = {
		val `type`: String = request.get("type").asInstanceOf[String].toLowerCase()
		val fileName: String = request.get("fileName").asInstanceOf[String]
		val filePath: String = request.getRequest.getOrDefault("filePath", "").asInstanceOf[String]
				.replaceAll("^/+|/+$", "")
		val identifier: String = request.get("identifier").asInstanceOf[String]
		validatePreSignedUrlRequest(`type`, fileName, filePath)
		DataNode.read(request).map(node => {
			val response = ResponseHandler.OK()
			val objectKey = if (StringUtils.isEmpty(filePath)) "content" + File.separator + `type` + File.separator + identifier + File.separator + Slug.makeSlug(fileName, true)
			else "content" + File.separator + filePath + File.separator + `type` + File.separator + identifier + File.separator + Slug.makeSlug(fileName, true)
			val expiry = Platform.config.getString("cloud_storage.upload.url.ttl")
			val preSignedURL = ss.getSignedURL(objectKey, Option.apply(expiry.toInt), Option.apply("w"))
			response.put("identifier", identifier)
			response.put("pre_signed_url", preSignedURL)
			response.put("url_expiry", expiry)
			response
		}) recoverWith { case e: CompletionException => throw e.getCause }
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

	private def validatePreSignedUrlRequest(`type`: String, fileName: String, filePath: String): Unit = {
		if (StringUtils.isEmpty(fileName))
			throw new ClientException("ERR_CONTENT_BLANK_FILE_NAME", "File name is blank")
		if (StringUtils.isBlank(FilenameUtils.getBaseName(fileName)) || StringUtils.length(Slug.makeSlug(fileName, true)) > 256)
			throw new ClientException("ERR_CONTENT_INVALID_FILE_NAME", "Please Provide Valid File Name.")
		if (!preSignedObjTypes.contains(`type`))
			throw new ClientException("ERR_INVALID_PRESIGNED_URL_TYPE", "Invalid pre-signed url type. It should be one of " + StringUtils.join(preSignedObjTypes, ","))
		if (StringUtils.isNotBlank(filePath) && filePath.size > 100)
			throw new ClientException("ERR_CONTENT_INVALID_FILE_PATH", "Please provide valid filepath of character length 100 or Less ")
	}

	def flag(request: Request): Future[Response] = {
		DataNode.read(request).map(node => {
			val contentId: String = request.get("identifier").asInstanceOf[String]
			val flagReasons: util.List[String] = request.get("flagReasons").asInstanceOf[util.ArrayList[String]]
			val flaggedBy: String = request.get("flaggedBy").asInstanceOf[String]
			val flags: util.List[String] = request.get("flags").asInstanceOf[util.ArrayList[String]]
			val mimeType: String = node.getMetadata.get("mimeType").asInstanceOf[String]
			val versionKey = node.getMetadata.get("versionKey").asInstanceOf[String]

			val objectType = node.getObjectType

			if (CONTENT_OBJECT_TYPE.equalsIgnoreCase(objectType)) {
				val metadata: util.Map[String, Object] = node.getMetadata.asInstanceOf[util.Map[String, Object]]
				val status: String = metadata.get("status").asInstanceOf[String]
				request.put("identifier", contentId)
				if (FLAGGABLE_STATUS.contains(status)) {
					val flaggedList: util.List[String] = addFlaggedBy(flaggedBy, metadata)
					if (CollectionUtils.isNotEmpty(flaggedList)) {
						request.put("lastUpdatedBy", flaggedBy)
					}
					request.put("flaggedBy", flaggedList)
					request.put("flags", flags)
					request.put("status", "Flagged")
					request.put("lastFlaggedOn", DateUtils.formatCurrentDate())
					if (CollectionUtils.isNotEmpty(flagReasons)) {
						request.put("flagReasons", addFlagReasons(flagReasons, metadata));
					}
					request.put("objectType", CONTENT_OBJECT_TYPE)
					request.getContext.put("versioning", "disable")
					request.put("versionkey",versionKey)

					/*this is cassandra codgirt e for updating the status 
					if(mimeType == "application/vnd.ekstep.content-collection"){
						val req = new Request(request)
						req.getContext.put("schemaName", "collection")
						req.put("identifier",contentId)
						ExternalPropsManager.fetchProps(req,List("hierarchy")).map(resp=>
						{
							if(resp != null){
								val deserializedData = JSONUtils.deserialize(resp.get("hierarchy").asInstanceOf[String])
								deserializedData.setStatus("Flagged")
								//update the flaggedby to flaggedList
								//update the flags to flags
								//update the lastflaggedon to DateUtils.formatCurrentDate()
								//update the flagResons 
								ExternalPropsManager.saveProps(req).map(resp =>
								{ 
								ResponseHandler.OK()
								})
							}
						})
					}*/

					DataNode.update(request).map(resp => {
						if (resp != null) {
							val response = ResponseHandler.OK
							val identifier: String = resp.getIdentifier.replace(".img", "")
							response.put("node_id", identifier)
							response.put("identifier", identifier)
							response.put("versionKey", resp.getMetadata.get("versionKey"))
							RedisCache.delete(contentId)
							response
						}
						else
							throw new ClientException("ERR_NODE_NOT_FOUND", "Cannot update the status")
					})
				}
				else
					throw new ClientException("ERR_CONTENT_NOT_FLAGGABLE", "Unpublished Content" + contentId + "cannot be flagged")
			}
			else
				throw new ClientException("ERR_NODE_NOT_FOUND", objectType + " " + contentId + " not found")

		}).flatMap(f => f)
	}

	def addFlagReasons(flagReasons: util.List[String], metadata: util.Map[String, Object]): util.List[String] = {
		val existingFlagReasons = metadata.get("flagReasons")
		if (existingFlagReasons != null) {
			var existingFlagReasonsList: util.List[String] = null
			if (existingFlagReasons.isInstanceOf[util.Arrays]) {
				existingFlagReasonsList = util.Arrays.asList(existingFlagReasons.asInstanceOf[String])
			}
			else if (existingFlagReasons.isInstanceOf[util.List[Object]]) {
				existingFlagReasonsList = existingFlagReasons.asInstanceOf[util.List[String]]
			}
			if (CollectionUtils.isNotEmpty(existingFlagReasonsList)) {
				val flagReasonsSet: util.Set[String] = new util.HashSet[String](existingFlagReasonsList)
				flagReasonsSet.addAll(flagReasons)
				return new util.ArrayList[String](flagReasonsSet)
			}
		}
		flagReasons
	}

	def addFlaggedBy(flaggedBy: String, metadata: util.Map[String, Object]): util.List[String] = {
		val flaggedByList: util.List[String] = new util.ArrayList[String]()
		flaggedByList.add(flaggedBy)
		val existingFlaggedBy = metadata.get("flaggedBy")
		if (existingFlaggedBy != null) {
			var existingFlaggedByList: util.List[String] = null
			if (existingFlaggedBy.isInstanceOf[util.Arrays]) {
				existingFlaggedByList = util.Arrays.asList(existingFlaggedBy.asInstanceOf[String])
			}
			else if (existingFlaggedBy.isInstanceOf[util.List[Object]]) {
				
				existingFlaggedByList = existingFlaggedBy.asInstanceOf[util.List[String]]
			}
			if (CollectionUtils.isNotEmpty(existingFlaggedByList)) {
				val flaggedBySet: util.Set[String] = new util.HashSet[String](existingFlaggedByList)
				flaggedBySet.addAll(flaggedByList)
				return new util.ArrayList[String](flaggedBySet)
			}
		}
		flaggedByList
	}

	
}
