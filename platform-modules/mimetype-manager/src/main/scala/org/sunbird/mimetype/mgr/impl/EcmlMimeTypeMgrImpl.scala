package org.sunbird.mimetype.mgr.impl

import java.io.File

import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.ecml.processor.{JsonParser, Plugin, XmlParser}
import org.sunbird.mimetype.mgr.{BaseMimeTypeManager, MimeTypeManager}
import org.sunbird.mimetype.util.FileUtils

import scala.concurrent.{ExecutionContext, Future}

object EcmlMimeTypeMgrImpl extends BaseMimeTypeManager with MimeTypeManager {

	override def upload(objectId: String, node: Node, uploadFile: File)(implicit ec: ExecutionContext): Future[Map[String, AnyRef]] = {
		FileUtils.validateFilePackage(uploadFile)
		//generateECRF
		val basePath:String = getBasePath(objectId)
		FileUtils.extractPackage(uploadFile, basePath)

		val ecmlType: String = getEcmlType(basePath)
		val ecml = getFileString(basePath, ecmlType)
		// generate ECML
		val ecrf: Plugin = getEcrfObject(ecmlType, ecml);

		//upload file
		uploadArtifactToCloud(uploadFile, objectId)
		//extractFile

		Future{Map("identifier"->objectId,"artifactUrl" -> "http://ecmlartifact.zip")}
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

}