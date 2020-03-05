package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.sunbird.cloudstore.StorageService
import org.sunbird.common.Platform
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.ecml.ECMLExtractor
import org.sunbird.mimetype.ecml.processor.{JsonParser, Plugin, XmlParser}
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}

import scala.concurrent.{ExecutionContext, Future}

class EcmlMimeTypeMgrImpl(implicit ss: StorageService) extends BaseMimeTypeManager with MimeTypeManager {

	
	private val DEFAULT_PACKAGE_MIME_TYPE = "application/zip"
	private val maxPackageSize = if(Platform.config.hasPath("MAX_CONTENT_PACKAGE_FILE_SIZE_LIMIT")) Platform.config.getDouble("MAX_CONTENT_PACKAGE_FILE_SIZE_LIMIT") else 52428800

	override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
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
		val result: Array[String] = uploadArtifactToCloud(uploadFile, objectId)
		//extractFile
		extractPackageInCloud(objectId, uploadFile, node, "snapshot", true)

		Future{Map("identifier"->objectId,"artifactUrl" -> result(1), "cloudStorageKey" -> result(0), "s3Key" -> result(0), "body" -> processedEcml)}
	}

	override def upload(objectId: String, node: Node, fileUrl: String)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		validateUploadRequest(objectId, node, fileUrl)
		val file: File = copyURLToFile(objectId, fileUrl)
		upload(objectId, node, file)
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
			if(!isValidPackageStructure(file, List("index.json", "index.ecml"))) throw new ClientException("VALIDATOR_ERROR", "INVALID_CONTENT_PACKAGE_STRUCTURE_ERROR | ['index' file and other folders (assets, data & widgets) should be at root location]")
			if(file.length() > maxPackageSize) throw new ClientException("VALIDATOR_ERROR", "INVALID_CONTENT_PACKAGE_SIZE_ERROR | [Content Package file size is too large]")
		}
		else{
			throw new ClientException("ERR_INVALID_FILE", "File does not exists")
		}
	}

}