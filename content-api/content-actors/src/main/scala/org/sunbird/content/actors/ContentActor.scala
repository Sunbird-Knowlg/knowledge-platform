package org.sunbird.content.actors

import java.util
import java.util.concurrent.CompletionException
import java.io.File

import com.mashape.unirest.http.{HttpResponse, Unirest}
import org.apache.commons.io.FilenameUtils
import javax.inject.Inject
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.core.BaseActor
import org.sunbird.cache.impl.RedisCache
import org.sunbird.content.util.{AcceptFlagManager, CopyManager, DiscardManager, FlagManager, RetireManager}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.{ContentParams, JsonUtils, Platform, Slug}
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.content.dial.DIALManager
import org.sunbird.content.mgr.ImportManager
import org.sunbird.util.{ChannelConstants, RequestUtil}
import org.sunbird.content.upload.mgr.UploadManager
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.NodeUtil

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class ContentActor @Inject() (implicit oec: OntologyEngineContext, ss: StorageService) extends BaseActor {

	implicit val ec: ExecutionContext = getContext().dispatcher

	val ORGANISATIONAL_FRAMEWORK_TERMS = List("organisationFrameworkId", "organisationBoardIds", "organisationGradeLevelIds", "organisationSubjectIds", "organisationMediumIds", "organisationTopicsIds")
	val TARGET_FRAMEWORK_TERMS = List("targetFrameworkIds", "targetBoardIds", "targetGradeLevelIds", "targetSubjectIds", "targetMediumIds", "targetTopicIds")

	override def onReceive(request: Request): Future[Response] = {
		request.getOperation match {
			case "createContent" => create(request)
			case "readContent" => read(request)
			case "updateContent" => update(request)
			case "uploadContent" => upload(request)
			case "retireContent" => retire(request)
			case "copy" => copy(request)
			case "uploadPreSignedUrl" => uploadPreSignedUrl(request)
			case "discardContent" => discard(request)
			case "flagContent" => flag(request)
			case "acceptFlag" => acceptFlag(request)
			case "linkDIALCode" => linkDIALCode(request)
			case "importContent" => importContent(request)
			case _ => ERROR(request.getOperation)
		}
	}

	def create(request: Request): Future[Response] = {
		populateDefaultersForCreation(request)
		RequestUtil.restrictProperties(request)
		DataNode.create(request, dataModifier).map(node => {
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
			val metadata: util.Map[String, AnyRef] = NodeUtil.serialize(node, fields, node.getObjectType.toLowerCase.replace("image", ""), request.getContext.get("version").asInstanceOf[String])
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
		DataNode.update(request, dataModifier).map(node => {
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
			if (null != node & StringUtils.isNotBlank(node.getObjectType))
				request.getContext.put("schemaName", node.getObjectType.toLowerCase())
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
		val filePath: String = request.getRequest.getOrDefault("filePath","").asInstanceOf[String]
			.replaceAll("^/+|/+$", "")
		val identifier: String = request.get("identifier").asInstanceOf[String]
		validatePreSignedUrlRequest(`type`, fileName, filePath)
		DataNode.read(request).map(node => {
			val response = ResponseHandler.OK()
			val objectKey = if (StringUtils.isEmpty(filePath)) "content" + File.separator + `type` + File.separator + identifier + File.separator + Slug.makeSlug(fileName, true)
				else filePath + File.separator + "content" + File.separator + `type` + File.separator + identifier + File.separator + Slug.makeSlug(fileName, true)
			val expiry = Platform.config.getString("cloud_storage.upload.url.ttl")
			val preSignedURL = ss.getSignedURL(objectKey, Option.apply(expiry.toInt), Option.apply("w"))
			response.put("identifier", identifier)
			response.put("pre_signed_url", preSignedURL)
			response.put("url_expiry", expiry)
			response
		}) recoverWith { case e: CompletionException => throw e.getCause }
	}

	def retire(request: Request): Future[Response] = {
		RetireManager.retire(request)
	}
	def discard(request: Request): Future[Response] = {
		RequestUtil.restrictProperties(request)
		DiscardManager.discard(request)
	}

	def flag(request: Request): Future[Response] = {
		FlagManager.flag(request)
	}

	def acceptFlag(request: Request): Future[Response] = {
		AcceptFlagManager.acceptFlag(request)
	}

	def linkDIALCode(request: Request): Future[Response] = DIALManager.link(request)

	def importContent(request: Request): Future[Response] = ImportManager.importContent(request)

	def populateDefaultersForCreation(request: Request) = {
		validateAndSetMultiFrameworks(request)
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
		validateAndSetMultiFrameworks(request)
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
		if(StringUtils.isNotBlank(filePath) && filePath.size > 100)
			throw new ClientException("ERR_CONTENT_INVALID_FILE_PATH", "Please provide valid filepath of character length 100 or Less ")
	}
	
	def dataModifier(node: Node): Node = {
		if(node.getMetadata.containsKey("trackable") && 
				node.getMetadata.getOrDefault("trackable", new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]].containsKey("enabled") &&
		"Yes".equalsIgnoreCase(node.getMetadata.getOrDefault("trackable", new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]].getOrDefault("enabled", "").asInstanceOf[String])) {
			node.getMetadata.put("contentType", "Course")
		}
		node
	}

	private def validateAndSetMultiFrameworks(request: Request): Unit = {
		val orgTermIds: List[String] = request.getRequest.asScala
			.filter(entry => ORGANISATIONAL_FRAMEWORK_TERMS.contains(entry._1))
			.flatMap(entry => entry._2 match {
				case e: String => List(e)
				case e: util.List[String] => e.asScala
			}).toList
		val orgTermMap = getValidatedTerms(orgTermIds)
		if (StringUtils.isNotBlank(request.get("organisationFrameworkId").asInstanceOf[String]))
			request.getRequest.putIfAbsent("framework", request.get("organisationFrameworkId").asInstanceOf[String])
		val boardIds = request.getRequest.getOrDefault("organisationBoardIds", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
		if (CollectionUtils.isNotEmpty(boardIds))
			request.getRequest.putIfAbsent("board", orgTermMap(boardIds.get(0)).asInstanceOf[String])
		val mediumIds = request.getRequest.getOrDefault("organisationMediumIds", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
		if (CollectionUtils.isNotEmpty(mediumIds))
			request.getRequest.putIfAbsent("medium", mediumIds.asScala.map(id => orgTermMap(id)).toList.asJava)
		val subjectIds = request.getRequest.getOrDefault("organisationSubjectIds", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
		if (CollectionUtils.isNotEmpty(subjectIds))
			request.getRequest.putIfAbsent("subject", subjectIds.asScala.map(id => orgTermMap(id)).toList.asJava)
		val gradeIds = request.getRequest.getOrDefault("organisationGradeLevelIds", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
		if (CollectionUtils.isNotEmpty(gradeIds))
			request.getRequest.putIfAbsent("gradeLevel", gradeIds.asScala.map(id => orgTermMap(id)).toList.asJava)
		val topicIds = request.getRequest.getOrDefault("organisationTopicsIds", new util.ArrayList[String]()).asInstanceOf[util.List[String]]
		if (CollectionUtils.isNotEmpty(topicIds))
			request.getRequest.putIfAbsent("topics", topicIds.asScala.map(id => orgTermMap(id)).toList.asJava)
		val targetTermIds: List[String] = request.getRequest.asScala
			.filter(entry => TARGET_FRAMEWORK_TERMS.contains(entry._1))
			.flatMap(_._2.asInstanceOf[util.List[String]].asScala).toList
		getValidatedTerms(targetTermIds)
	}

	private def getValidatedTerms(termIds: List[String]): Map[String, AnyRef] = {
		if(termIds.nonEmpty) {
			val url: String = if (Platform.config.hasPath("composite.search.url")) Platform.config.getString("composite.search.url") else "https://dev.sunbirded.org/action/composite/v3/search"
			val httpResponse: HttpResponse[String] = Unirest.post(url).header("Content-Type", "application/json")
				.body(s"""{"request":{"filters":{"objectType":["Term", "Framework"], "status": [], "identifier": ${termIds.mkString("[\"", "\", \"", "\"]")
				}},"fields":["name"]}}""").asString
			if (200 != httpResponse.getStatus)
				throw new ServerException("ERR_CONTENT_CREATE", "Error while fetching Framework Terms data.")
			val response: Response = JsonUtils.deserialize(httpResponse.getBody, classOf[Response])
			val termList: util.List[util.Map[String, AnyRef]] = response.getResult.getOrDefault("Term", new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]]
			termList.addAll(response.getResult.getOrDefault("Framework",  new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]])
			if (termList.isEmpty)
				throw new ClientException("ERR_CONTENT_CREATE", s"Term ids are not found for list: $termIds ")
			val termMap = termList.asScala.map(entry => entry.getOrDefault("identifier", "").asInstanceOf[String] -> entry.getOrDefault("name", "")).toMap
			val invalidTermIds = CollectionUtils.disjunction(termIds.asJava, termMap.keys.asJava)
			if(CollectionUtils.isNotEmpty(invalidTermIds))
				throw new ClientException("ERR_CONTENT_CREATE", s"Term ids are not found for list: $invalidTermIds ")
			termMap
		} else Map()
	}
}
