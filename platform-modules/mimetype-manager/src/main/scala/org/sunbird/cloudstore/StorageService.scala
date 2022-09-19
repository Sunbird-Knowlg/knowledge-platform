package org.sunbird.cloudstore

import java.io.File
import org.apache.commons.lang3.StringUtils
import org.sunbird.cloud.storage.BaseStorageService
import org.sunbird.common.Platform
import org.sunbird.cloud.storage.factory.StorageConfig
import org.sunbird.cloud.storage.factory.StorageServiceFactory
import org.sunbird.common.exception.ServerException
import org.sunbird.common.Slug

import java.net.URLConnection
import java.util
import scala.concurrent.{ExecutionContext, Future}

class StorageService {

    val storageType: String = if (Platform.config.hasPath("cloud_storage_type")) Platform.config.getString("cloud_storage_type") else ""
    var storageService: BaseStorageService = null

    @throws[Exception]
    def getService: BaseStorageService = {
        if (null == storageService) {
            if (StringUtils.equalsIgnoreCase(storageType, "azure")) {
                val storageKey = Platform.config.getString("azure_storage_key")
                val storageSecret = Platform.config.getString("azure_storage_secret")
                storageService = StorageServiceFactory.getStorageService(StorageConfig(storageType, storageKey, storageSecret))
            } else if (StringUtils.equalsIgnoreCase(storageType, "aws")) {
                val storageKey = Platform.config.getString("aws_storage_key")
                val storageSecret = Platform.config.getString("aws_storage_secret")
                storageService = StorageServiceFactory.getStorageService(StorageConfig(storageType, storageKey, storageSecret))
            } else if (StringUtils.equalsIgnoreCase(storageType, "gcloud")) {
                val storageKey = Platform.config.getString("gcloud_client_key")
                val storageSecret = Platform.config.getString("gcloud_private_secret")
                storageService = StorageServiceFactory.getStorageService(StorageConfig(storageType, storageKey, storageSecret))
            }
//            else if (StringUtils.equalsIgnoreCase(storageType, "cephs3")) {
//                val storageKey = Platform.config.getString("cephs3_storage_key")
//                val storageSecret = Platform.config.getString("cephs3_storage_secret")
//                val endpoint = Platform.config.getString("cephs3_storage_endpoint")
//                storageService = StorageServiceFactory.getStorageService(new StorageConfig(storageType, storageKey, storageSecret, Option(endpoint)))
//            }
            else throw new ServerException("ERR_INVALID_CLOUD_STORAGE", "Error while initialising cloud storage")
        }
        storageService
    }

    def getContainerName: String = {
      storageType match {
        case "azure" => Platform.config.getString("azure_storage_container")
        case "aws" => Platform.config.getString("aws_storage_container")
        case "gcloud" => Platform.config.getString("gcloud_storage_bucket")
        case _ => throw new ServerException("ERR_INVALID_CLOUD_STORAGE", "Container name not configured.")
      }
    }

    def uploadFile(folderName: String, file: File, slug: Option[Boolean] = Option(true)): Array[String] = {
        val slugFile = if (slug.getOrElse(true)) Slug.createSlugFile(file) else file
        val objectKey = folderName + "/" + slugFile.getName
        val url = getService.upload(getContainerName, slugFile.getAbsolutePath, objectKey, Option.apply(false), Option.apply(1), Option.apply(5), Option.empty)
        Array[String](objectKey, url)
    }

    def uploadDirectory(folderName: String, directory: File, slug: Option[Boolean] = Option(true)): Array[String] = {
        val slugFile = if (slug.getOrElse(true)) Slug.createSlugFile(directory) else directory
        val objectKey = folderName + File.separator
        val url = getService.upload(getContainerName, slugFile.getAbsolutePath, objectKey, Option.apply(true), Option.apply(1), Option.apply(5), Option.empty)
        Array[String](objectKey, url)
    }

    def uploadDirectoryAsync(folderName: String, directory: File, slug: Option[Boolean] = Option(true))(implicit ec: ExecutionContext): Future[List[String]] = {
        val slugFile = if (slug.getOrElse(true)) Slug.createSlugFile(directory) else directory
        val objectKey = folderName + File.separator
        getService.uploadFolder(getContainerName, slugFile.getAbsolutePath, objectKey, Option.apply(false), None, None, 1)
    }

    def getObjectSize(key: String): Double = {
        val blob = getService.getObject(getContainerName, key, Option.apply(false))
        blob.contentLength
    }

    def copyObjectsByPrefix(source: String, destination: String): Unit = {
        getService.copyObjects(getContainerName, source, getContainerName, destination, Option.apply(true))
    }

    def deleteFile(key: String, isDirectory: Option[Boolean] = Option(false)): Unit = {
        getService.deleteObject(getContainerName, key, isDirectory)
    }

    def getSignedURL(key: String, ttl: Option[Int], permission: Option[String], contentType: Option[String] = None): String = {
      storageType match {
        case "gcloud" => getService.getPutSignedURL(getContainerName, key, ttl, permission, Option.apply(getMimeType(key)))
        case _ => getService.getSignedURL (getContainerName, key, ttl, permission)
      }
    }

    def getUri(key: String): String = {
        try {
           getService.getUri(getContainerName, key, Option.apply(false))
        } catch {
            case e:Exception =>
              println("StorageService --> getUri --> Exception: " + e.getMessage)
              ""
        }
    }

  def getMimeType(fileName: String): String = { // 1. first use java's built-in utils
    val mimeTypes = URLConnection.getFileNameMap
    var contentType = mimeTypes.getContentTypeFor(fileName)
    // 2. nothing found -> lookup our in extension map to find types like ".doc" or ".docx"
    if (contentType == null) {
      val extension = fileName.substring(fileName.lastIndexOf('.') + 1, fileName.length)
      contentType = fileExtensionMap.getOrDefault(extension, "application/octet-stream")
    }
    contentType
  }

  private val fileExtensionMap: util.Map[String, String] = new java.util.HashMap().asInstanceOf[java.util.Map[String, String]]

  // MS Office
  fileExtensionMap.put("doc", "application/msword")
  fileExtensionMap.put("dot", "application/msword")
  fileExtensionMap.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  fileExtensionMap.put("dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template")
  fileExtensionMap.put("docm", "application/vnd.ms-word.document.macroEnabled.12")
  fileExtensionMap.put("dotm", "application/vnd.ms-word.template.macroEnabled.12")
  fileExtensionMap.put("xls", "application/vnd.ms-excel")
  fileExtensionMap.put("xlt", "application/vnd.ms-excel")
  fileExtensionMap.put("xla", "application/vnd.ms-excel")
  fileExtensionMap.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  fileExtensionMap.put("xltx", "application/vnd.openxmlformats-officedocument.spreadsheetml.template")
  fileExtensionMap.put("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12")
  fileExtensionMap.put("xltm", "application/vnd.ms-excel.template.macroEnabled.12")
  fileExtensionMap.put("xlam", "application/vnd.ms-excel.addin.macroEnabled.12")
  fileExtensionMap.put("xlsb", "application/vnd.ms-excel.sheet.binary.macroEnabled.12")
  fileExtensionMap.put("ppt", "application/vnd.ms-powerpoint")
  fileExtensionMap.put("pot", "application/vnd.ms-powerpoint")
  fileExtensionMap.put("pps", "application/vnd.ms-powerpoint")
  fileExtensionMap.put("ppa", "application/vnd.ms-powerpoint")
  fileExtensionMap.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
  fileExtensionMap.put("potx", "application/vnd.openxmlformats-officedocument.presentationml.template")
  fileExtensionMap.put("ppsx", "application/vnd.openxmlformats-officedocument.presentationml.slideshow")
  fileExtensionMap.put("ppam", "application/vnd.ms-powerpoint.addin.macroEnabled.12")
  fileExtensionMap.put("pptm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12")
  fileExtensionMap.put("potm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12")
  fileExtensionMap.put("ppsm", "application/vnd.ms-powerpoint.slideshow.macroEnabled.12")
  // Open Office
  fileExtensionMap.put("odt", "application/vnd.oasis.opendocument.text")
  fileExtensionMap.put("ott", "application/vnd.oasis.opendocument.text-template")
  fileExtensionMap.put("oth", "application/vnd.oasis.opendocument.text-web")
  fileExtensionMap.put("odm", "application/vnd.oasis.opendocument.text-master")
  fileExtensionMap.put("odg", "application/vnd.oasis.opendocument.graphics")
  fileExtensionMap.put("otg", "application/vnd.oasis.opendocument.graphics-template")
  fileExtensionMap.put("odp", "application/vnd.oasis.opendocument.presentation")
  fileExtensionMap.put("otp", "application/vnd.oasis.opendocument.presentation-template")
  fileExtensionMap.put("ods", "application/vnd.oasis.opendocument.spreadsheet")
  fileExtensionMap.put("ots", "application/vnd.oasis.opendocument.spreadsheet-template")
  fileExtensionMap.put("odc", "application/vnd.oasis.opendocument.chart")
  fileExtensionMap.put("odf", "application/vnd.oasis.opendocument.formula")
  fileExtensionMap.put("odb", "application/vnd.oasis.opendocument.database")
  fileExtensionMap.put("odi", "application/vnd.oasis.opendocument.image")
  fileExtensionMap.put("oxt", "application/vnd.openofficeorg.extension")
  // Other
  fileExtensionMap.put("rtf", "application/rtf")
  fileExtensionMap.put("pdf", "application/pdf")
  fileExtensionMap.put("h5p", "application/octet-stream")
  fileExtensionMap.put("csv", "text/csv")
  fileExtensionMap.put("epub", "application/epub+zip")
}
