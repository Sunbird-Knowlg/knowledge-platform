package org.sunbird.cloudstore

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.{BlobId, BlobInfo, HttpMethod, Storage, StorageOptions}
import org.apache.commons.lang3.StringUtils
import org.apache.tika.Tika
import org.apache.tika.metadata.HttpHeaders
import org.apache.tika.mime.MimeTypes
import org.sunbird.cloud.storage.BaseStorageService
import org.sunbird.cloud.storage.factory.{StorageConfig, StorageServiceFactory}
import org.sunbird.common.exception.ServerException
import org.sunbird.common.{Platform, Slug}

import java.io.File
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future};


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

    def getSignedURL(key: String, ttl: Option[Int], permission: Option[String]): String = {
      storageType match {
        case "gcloud" => getGCPSignedURL(Platform.config.getString("gcloud_private_bucket_projecclientid"),
          Platform.config.getString("gcloud_client_key"),
          Platform.config.getString("gcloud_private_secret"),
          Platform.config.getString("gcloud_private_bucket_projeckeyid"), Platform.config.getString("gcloud_private_bucket_projectId"), key, ttl.get)
        case _ => getService.getSignedURL (getContainerName, key, ttl, permission)
      }
    }

  def getGCPSignedURL(clientId: String, clientEmail: String, privateKeyPkcs8: String, privateKeyIds: String, projectId: String, objectName: String, ttl: Long):  String = {
    val credentials = ServiceAccountCredentials.fromPkcs8(clientId, clientEmail, privateKeyPkcs8, privateKeyIds, new java.util.ArrayList[String]())
    println("credentials : ", credentials)
    val storage = StorageOptions.newBuilder.setProjectId(projectId).setCredentials(credentials).build.getService
    println("container name : ", getContainerName)
    println("object name : ", objectName)
    println("Storage : ", storage)
    val extensionHeaders = new java.util.HashMap().asInstanceOf[java.util.Map[String, String]]
    extensionHeaders.putAll(Map(HttpHeaders.CONTENT_TYPE -> MimeTypes.OCTET_STREAM).asJava)
    val blobInfo = BlobInfo.newBuilder(BlobId.of(getContainerName, objectName)).build
    println("blob : ", blobInfo)
    val expiryTime = if(ttl > 7) 7 else ttl
    val url = storage.signUrl(blobInfo, expiryTime, TimeUnit.DAYS, Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
      Storage.SignUrlOption.withExtHeaders(extensionHeaders),
      Storage.SignUrlOption.withV4Signature);
    println("url:", url)
    url.toString;
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
