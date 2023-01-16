package org.sunbird.cloudstore

import org.apache.commons.lang3.StringUtils
import org.apache.tika.Tika
import org.sunbird.cloud.storage.BaseStorageService
import org.sunbird.cloud.storage.factory.{StorageConfig, StorageServiceFactory}
import org.sunbird.common.{Platform, Slug}
import org.sunbird.common.exception.ServerException

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

class StorageService {

    val storageType: String = if (Platform.config.hasPath("cloud_storage_type")) Platform.config.getString("cloud_storage_type") else ""
    var storageService: BaseStorageService = null

    @throws[Exception]
    def getService: BaseStorageService = {
        if (null == storageService) {
              val storageKey = Platform.config.getString("cloud_storage_key")
              val storageSecret = Platform.config.getString("cloud_storage_secret")
              // TODO: endPoint defined to support "cephs3". Make code changes after cloud-store-sdk 2.11 support it.
              val endPoint = if (Platform.config.hasPath("cloud_storage_endpoint")) Option(Platform.config.getString("cloud_storage_endpoint")) else None
              storageService = StorageServiceFactory.getStorageService(new StorageConfig(storageType, storageKey, storageSecret,endPoint))
        }
        storageService
    }

    def getContainerName: String = {
      if(Platform.config.hasPath("cloud_storage_container"))
        Platform.config.getString("cloud_storage_container")
      else
        throw new ServerException("ERR_INVALID_CLOUD_STORAGE", "Cloud Storage Container name not configured.")
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

    def getSignedURL(key: String, ttl: Option[Int], permission: Option[String]): String = {
      getService.getPutSignedURL(getContainerName, key, ttl, permission, Option.apply(getMimeType(key)))
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

  def getMimeType(fileName: String): String = {
    val tika: Tika = new Tika()
    tika.detect(fileName)
  }

}
