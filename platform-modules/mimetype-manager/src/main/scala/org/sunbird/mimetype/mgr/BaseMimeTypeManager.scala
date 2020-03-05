package org.sunbird.mimetype.mgr

import java.io.{File, FileInputStream, FileOutputStream, IOException}
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.apache.tika.Tika
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.common.{Platform, Slug}
import org.sunbird.graph.dac.model.Node
import org.sunbird.telemetry.logger.TelemetryManager

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


class BaseMimeTypeManager(implicit ss: StorageService) {

	protected val TEMP_FILE_LOCATION = Platform.getString("content.upload.temp_location", "/tmp/content")
	private val CONTENT_FOLDER = "cloud_storage.content.folder"
	private val ARTIFACT_FOLDER = "cloud_storage.artifact.folder"
	private val validator = new UrlValidator()
	protected val extractableMimeTypes = List("application/vnd.ekstep.ecml-archive", "application/vnd.ekstep.html-archive", "application/vnd.ekstep.plugin-archive", "application/vnd.ekstep.h5p-archive")
	protected val extractablePackageExtensions = List(".zip", ".h5p", ".epub")
	private val H5P_MIMETYPE: String = "application/vnd.ekstep.h5p-archive"
	private val H5P_LIBRARY_PATH: String = Platform.config.getString("content.h5p.library.path")
	val DASH= "-"
	val CONTENT_PLUGINS = "content-plugins"
	val FILENAME_EXTENSION_SEPARATOR = "."
	val DEFAULT_ZIP_EXTENSION = "zip"
	private val tika: Tika = new Tika()

	val IDX_S3_KEY = 0
	val IDX_S3_URL = 1

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
			urlArray = ss.uploadFile(folder, uploadedFile)
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
		val fileName = getBasePath(objectId) + File.separator + getFileNameFromURL(fileUrl)
		val file = new File(fileName)
		FileUtils.copyURLToFile(new URL(fileUrl), file)
		file
	} catch {
		case e: IOException =>
			throw new ClientException("ERR_INVALID_FILE_URL", "Please Provide Valid File Url!")
	}

	def getFileNameFromURL(fileUrl: String): String = {
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
	
	def isValidMimeType(file: File, expectedMimeType: String): Boolean = {
		val mimeType = tika.detect(file)
		expectedMimeType.equalsIgnoreCase(mimeType)
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

	protected def getCloudStoredFileSize(key: String)(implicit ss: StorageService): Double = {
		val size = 0
		if (StringUtils.isNotBlank(key)) try return ss.getObjectSize(key)
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

	def extractH5pPackage(objectId: String, extractionBasePath: String) = {
		val h5pLibraryDownloadPath:String = getBasePath(objectId)
		try{
			val url: URL = new URL(H5P_LIBRARY_PATH)
			val file = new File(h5pLibraryDownloadPath + File.separator + getFileNameFromURL(url.getPath))
			FileUtils.copyURLToFile(url, file)
			extractPackage(file, extractionBasePath)
		}
		finally{
			if(new File(h5pLibraryDownloadPath).exists()){
				FileUtils.deleteDirectory(new File(h5pLibraryDownloadPath))
			}
		}
	}

	def getExtractionPath(objectId: String, node: Node, extractionType: String, mimeType: String): String = {
		val baseFolder = Platform.config.getString(CONTENT_FOLDER)
		val pathSuffix: String = {
			if(extractionType.equalsIgnoreCase("version")) {
				val version = String.valueOf(node.getMetadata.get("pkgVersion").asInstanceOf[Double])
				if("application/vnd.ekstep.plugin-archive".equalsIgnoreCase(mimeType) && StringUtils.isNotBlank(node.getMetadata.get("semanticVersion").asInstanceOf[String])){
					node.getMetadata.get("semanticVersion").asInstanceOf[String]
				} else version
			}else extractionType
		}
		
		mimeType match {
			case "application/vnd.ekstep.ecml-archive" => baseFolder + File.separator + "ecml" + File.separator + objectId + DASH + pathSuffix
			case "application/vnd.ekstep.html-archive" => baseFolder + File.separator + "html" + File.separator + objectId + DASH + pathSuffix
			case "application/vnd.ekstep.h5p-archive" => baseFolder + File.separator + "h5p" + File.separator + objectId + DASH + pathSuffix
			case "application/vnd.ekstep.plugin-archive" => CONTENT_PLUGINS + File.separator + objectId + DASH + pathSuffix
			case _ => ""
		}
	}

	def extractPackageInCloud(objectId: String, uploadFile: File, node: Node, extractionType: String, slugFile: Boolean)(implicit ss: StorageService) = {
		val file = Slug.createSlugFile(uploadFile)
		val mimeType = node.getMetadata.get("mimeType").asInstanceOf[String]

		if(!file.exists() || (!extractablePackageExtensions.contains(FILENAME_EXTENSION_SEPARATOR + FilenameUtils.getExtension(file.getName)) && extractableMimeTypes.contains(mimeType)))
			throw new ClientException("INVALID_FILE", "Error! File doesn't Exist.")
		if(null == extractionType)
			throw new ClientException("INVALID_EXTRACTION", "Error! Invalid Content Extraction Type.")
		if(extractableMimeTypes.contains(mimeType)){
			val extractionBasePath = getBasePath(objectId)
			if(H5P_MIMETYPE.equalsIgnoreCase(mimeType)){
				extractH5pPackage(objectId, extractionBasePath)
				extractPackage(file, extractionBasePath + File.separator + "content")
				ss.uploadDirectoryAsync(getExtractionPath(objectId, node, extractionType, mimeType), new File(extractionBasePath), Option(slugFile))(ExecutionContext.Implicits.global)
			} else {
				extractPackage(file, extractionBasePath)
				ss.uploadDirectory(getExtractionPath(objectId, node, extractionType, mimeType), new File(extractionBasePath), Option(slugFile))
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
			.map(path => generateZipEntry(path.toString, sourceFolder)).toList


	private def generateZipEntry(file: String, sourceFolder: String): String = file.substring(sourceFolder.length, file.length)

	private def zipIt(zipFile: String, fileList: List[String], sourceFolder: String): Unit = {
		val buffer = new Array[Byte](1024)
		var zos: ZipOutputStream = null
		try {
			zos = new ZipOutputStream(new FileOutputStream(zipFile))
			TelemetryManager.log("Creating Zip File: " + zipFile)
			fileList.foreach(file => {
				val ze = new ZipEntry(file)
				zos.putNextEntry(ze)
				val in = new FileInputStream(sourceFolder + File.separator + file)
				try {
					val len = in.read(buffer)
					/*while (len > 0)*/ zos.write(buffer, 0, len)
				} finally if (in != null) in.close()
				zos.closeEntry()
			})
		} catch {
			case e: IOException => TelemetryManager.error("Error! Something Went Wrong While Creating the ZIP File: " + e.getMessage, e)
		} finally if (zos != null) zos.close()
	}
}


