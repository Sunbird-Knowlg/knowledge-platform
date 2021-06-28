package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.apache.commons.lang3.StringUtils
import org.sunbird.models.UploadParams
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.ecml.{ECMLExtractor, ECMLProcessor}
import org.sunbird.mimetype.ecml.processor.{JsonParser, Plugin, XmlParser}
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}

class EcmlMimeTypeMgrImpl(implicit ss: StorageService) extends BaseMimeTypeManager with MimeTypeManager {

	
	private val DEFAULT_PACKAGE_MIME_TYPE = "application/zip"
	private val maxPackageSize = if(Platform.config.hasPath("MAX_CONTENT_PACKAGE_FILE_SIZE_LIMIT")) Platform.config.getDouble("MAX_CONTENT_PACKAGE_FILE_SIZE_LIMIT") else 52428800

	override def upload(objectId: String, node: Node, uploadFile: File, filePath: Option[String], params: UploadParams)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateFilePackage(uploadFile)
		//generateECRF
		val basePath:String = getBasePath(objectId)
		extractPackage(uploadFile, basePath)

		val ecmlType: String = getEcmlType(basePath)
		val ecml = getFileString(basePath, ecmlType)
		// generate ECML
		val ecrf: Plugin = getEcrfObject(ecmlType, ecml);

		val processedEcrf: Plugin = new ECMLExtractor(basePath, objectId).process(ecrf)
		val processedEcml: String = getEcmlStringFromEcrf(processedEcrf, ecmlType)
		//upload file
		val result: Array[String] = uploadArtifactToCloud(uploadFile, objectId, filePath)
		//extractFile
		extractPackageInCloud(objectId, uploadFile, node, "snapshot", true)

		Future{Map("identifier"->objectId,"artifactUrl" -> result(1), "cloudStorageKey" -> result(0), "s3Key" -> result(0), "body" -> processedEcml)}
	}

	override def upload(objectId: String, node: Node, fileUrl: String, filePath: Option[String], params: UploadParams)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, fileUrl)
		val file: File = copyURLToFile(objectId, fileUrl)
		upload(objectId, node, file, filePath, params)
	}

	def getEcmlType(basePath: String):String = {
		val jsonFile = new File(basePath + File.separator + "index.json")
		val xmlFile = new File(basePath + File.separator + "index.ecml")
		if(null != jsonFile && jsonFile.exists() && null != xmlFile && xmlFile.exists()) throw new ClientException("MULTIPLE_ECML", "MULTIPLE_ECML_FILES_FOUND | [index.json and index.ecml]")
		if(jsonFile.exists()) "json"
		else if(xmlFile.exists()) "ecml"
		else ""
	}

	def getEcrfObject(ecmlType: String, ecml: String): Plugin = {
		ecmlType match {
			case "ecml" => XmlParser.parse(ecml)
			case "json" => JsonParser.parse(ecml)
			case _ => classOf[Plugin].newInstance()
		}
	}

	def getFileString(basePath: String, ecmlType: String):String = {
		ecmlType match {
			case "ecml" => org.apache.commons.io.FileUtils.readFileToString(new File(basePath + File.separator + "index.ecml"), "UTF-8")
			case "json" => org.apache.commons.io.FileUtils.readFileToString(new File(basePath + File.separator + "index.json"), "UTF-8")
			case _ => ""
		}
	}

	def getEcmlStringFromEcrf(processedEcrf: Plugin, ecmlType: String): String = {
		ecmlType match {
			case "ecml" => XmlParser.toString(processedEcrf)
			case "json" => JsonParser.toString(processedEcrf)
			case _ => ""
		}
	}

	def validateFilePackage(file: File) = {
		if(null != file && file.exists()){
			if(!isValidMimeType(file, DEFAULT_PACKAGE_MIME_TYPE)) throw new ClientException("VALIDATOR_ERROR", "INVALID_CONTENT_PACKAGE_FILE_MIME_TYPE_ERROR | [The uploaded package is invalid]")
			if(!isValidPackageStructure(file, List("index.json", "index.ecml", "/index.json", "/index.ecml"))) throw new ClientException("VALIDATOR_ERROR", "INVALID_CONTENT_PACKAGE_STRUCTURE_ERROR | ['index' file and other folders (assets, data & widgets) should be at root location]")
			if(file.length() > maxPackageSize) throw new ClientException("VALIDATOR_ERROR", "INVALID_CONTENT_PACKAGE_SIZE_ERROR | [Content Package file size is too large]")
		}
		else{
			throw new ClientException("ERR_INVALID_FILE", "File does not exists")
		}
	}

	override def review(objectId: String, node: Node)(implicit ec: ExecutionContext, ontologyEngineContext: OntologyEngineContext): Future[Map[String, AnyRef]] = {
		validate(node)
		Future(getEnrichedMetadata(node.getMetadata.getOrDefault("status", "").asInstanceOf[String]))
	}

	def validate(node: Node)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Unit = {
		val artifactUrl = node.getMetadata.getOrDefault("artifactUrl", "").asInstanceOf[String]
		val req = new Request()
		req.setContext(Map[String, AnyRef]("schemaName" -> node.getObjectType.toLowerCase.replaceAll("image", ""), "version"->"1.0").asJava)
		req.put("identifier", node.getIdentifier)
		val responseFuture = oec.graphService.readExternalProps(req, List("body"))
		responseFuture.map(response => {
			if (!ResponseHandler.checkError(response)) {
				val body = response.getResult.toMap.getOrDefault("body", "").asInstanceOf[String]
				if(StringUtils.isBlank(artifactUrl) && StringUtils.isBlank(body))
					throw new ClientException("VALIDATOR_ERROR", MISSING_REQUIRED_FIELDS + " | [Either 'body' or 'artifactUrl' are required for processing of ECML content!")
				if(StringUtils.isNotBlank(body)) {
					val ecrf: Plugin = getEcrfObject("ecml", body)
					val processedEcrf: Plugin = new ECMLProcessor(getBasePath(node.getIdentifier), node.getIdentifier).process(ecrf)
				}
			} else if (ResponseHandler.checkError(response) && StringUtils.isBlank(artifactUrl)) {
				throw new ClientException("VALIDATOR_ERROR", MISSING_REQUIRED_FIELDS + " | [Either 'body' or 'artifactUrl' are required for processing of ECML content!")
			}
		})

	}
}
