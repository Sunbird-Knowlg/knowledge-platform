package org.sunbird.mimetype.mgr

import java.io.{File, FileInputStream, FileOutputStream, IOException}
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.apache.tika.Tika
import org.sunbird.cloudstore.CloudStore
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.common.{Platform, Slug}
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.BaseMimeTypeManager.generateZipEntry
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._


class BaseMimeTypeManager {

	protected val TEMP_FILE_LOCATION = Platform.getString("content.upload.temp_location", "/tmp/content")
	private val CONTENT_FOLDER = "cloud_storage.content.folder"
	private val ARTIFACT_FOLDER = "cloud_storage.artifact.folder"
	private val validator = new UrlValidator()

	protected val UPLOAD_DENIED_ERR_MSG = "FILE_UPLOAD_ERROR | Upload operation not supported for given mimeType"

	def validateUploadRequest(objectId: String, node: Node, data: AnyRef)(implicit ec: ExecutionContext): Unit = {
		if (StringUtils.isBlank(objectId))
			throw new ClientException("ERR_INVALID_ID", "Please Provide Valid Identifier!")
		if (null == node)
			throw new ClientException("ERR_INVALID_NODE", "Please Provide Valid Node!")
		if (null == data)
			throw new ClientException("ERR_INVALID_DATA", "Please Provide Valid File Or File Url!")
		if (data.isInstanceOf[String])
			validateUrl(data.toString)
		else if (data.isInstanceOf[File])
			validateFile(data.asInstanceOf[File])
	}

	def validateFile(file: File): Unit = {
		if(null==file || !file.exists())
			throw new ClientException("ERR_INVALID_FILE", "Please Provide Valid File!")
	}

	def validateUrl(fileUrl: String): Unit = {
		if (!validator.isValid(fileUrl))
			throw new ClientException("ERR_INVALID_FILE_URL", "Please Provide Valid File Url!")
	}

	def uploadArtifactToCloud(uploadedFile: File, identifier: String): Array[String] = {
		var urlArray = new Array[String](2)
		try {
			val folder = Platform.getString(CONTENT_FOLDER, "content") + File.separator + Slug.makeSlug(identifier, true) + File.separator + Platform.getString(ARTIFACT_FOLDER, "artifact")
			urlArray = CloudStore.uploadFile(folder, uploadedFile, true)
		} catch {
			case e: Exception =>
				TelemetryManager.error("Error while uploading the file.", e)
				throw new ServerException("ERR_CONTENT_UPLOAD_FILE", "Error while uploading the File.", e)
		}
		urlArray
	}

	def getBasePath(objectId: String): String = {
		if (!StringUtils.isBlank(objectId)) TEMP_FILE_LOCATION + File.separator + System.currentTimeMillis + "_temp" + File.separator + objectId else ""
	}

	def delete(file: File): Unit = {
		if (null != file && file.isDirectory)
			FileUtils.deleteDirectory(file)
		else file.delete()
	}

	def copyURLToFile(objectId: String, fileUrl: String): File = try {
		val fileName = getBasePath(objectId) + File.separator + getFieNameFromURL(fileUrl)
		val file = new File(fileName)
		FileUtils.copyURLToFile(new URL(fileUrl), file)
		file
	} catch {
		case e: IOException =>
			throw new ClientException("ERR_INVALID_FILE_URL", "Please Provide Valid File Url!")
	}

	def getFieNameFromURL(fileUrl: String): String = {
		var fileName = FilenameUtils.getBaseName(fileUrl) + "_" + System.currentTimeMillis
		if (!FilenameUtils.getExtension(fileUrl).isEmpty) fileName += "." + FilenameUtils.getExtension(fileUrl)
		fileName
	}

	def getFileSize(file: File): Double = {
		if (null != file && file.exists) file.length else 0
	}

	def isValidPackageStructure(file: File, checkParams: List[String]): Boolean = {
		if (null != file && file.exists()) {
			val zipFile: ZipFile = new ZipFile(file)
			try {
				val entries = checkParams.filter(fileName => null != zipFile.getEntry(fileName))
				null != entries && !entries.isEmpty
			}
			catch {
				case e: Exception => throw new ClientException("ERR_INVALID_FILE", "Please Provide Valid File!")
			} finally {
				if (null != zipFile) zipFile.close()
			}
		} else false
	}

	def extractPackage(file: File, basePath: String) = {
		val zipFile = new ZipFile(file)
		for (entry <- zipFile.entries().asScala) {
			val path = Paths.get(basePath + File.separator + entry.getName)
			if (entry.isDirectory) Files.createDirectories(path)
			else {
				Files.createDirectories(path.getParent)
				Files.copy(zipFile.getInputStream(entry), path)
			}
		}
	}

	protected def getCloudStoredFileSize(key: String): Double = {
		val size = 0
		if (StringUtils.isNotBlank(key)) try return CloudStore.getObjectSize(key)
		catch {
			case e: Exception =>
				TelemetryManager.error("Error While getting the file size from Cloud Storage: " + key, e)
		}
		size
	}

	def getFileMimeType(file: File): String = {
		val tika = new Tika()
		try tika.detect(file)
		catch {
			case e: IOException => {
				e.printStackTrace()
				""
			}
		}
	}

	def createZipPackage(basePath: String, zipFileName: String): Unit =
		if (!StringUtils.isBlank(zipFileName)) {
			TelemetryManager.log("Creating Zip File: " + zipFileName)
			val fileList: List[String] = generateFileList(basePath)
			zipIt(zipFileName, fileList, basePath)
		}


	private def generateFileList(sourceFolder: String): List[String] =
		Files.walk(Paths.get(new File(sourceFolder).getPath)).toArray()
			.map(path => path.asInstanceOf[Path])
			.filter(path => Files.isRegularFile(path))
			.map(path => generateZipEntry(path.asInstanceOf[String], sourceFolder)).toList


	private def generateZipEntry(file: String, sourceFolder: String): String = file.substring(sourceFolder.length, file.length)

	private def zipIt(zipFile: String, fileList: List[String], sourceFolder: String): Unit = {
		val buffer = new Array[Byte](1024)
		var zos: ZipOutputStream = _
		try {
			zos = new ZipOutputStream(new FileOutputStream(zipFile))
			TelemetryManager.log("Creating Zip File: " + zipFile)
			fileList.foreach(file => {
				val ze = new ZipEntry(file)
				zos.putNextEntry(ze)
				val in = new FileInputStream(sourceFolder + File.separator + file)
				try {
					val len = in.read(buffer)
					while (len > 0) zos.write(buffer, 0, len)
				} finally if (in != null) in.close()
				zos.closeEntry()
			})
		} catch {
			case ex: IOException => TelemetryManager.error("Error! Something Went Wrong While Creating the ZIP File: " + ex.getMessage, ex)
		} finally if (zos != null) zos.close()
	}
}


