package org.sunbird.cloudstore

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.{BlobId, BlobInfo, Storage, StorageOptions}
import org.apache.commons.lang3.StringUtils
import org.apache.tika.Tika
import org.sunbird.cloud.storage.BaseStorageService
import org.sunbird.cloud.storage.factory.{StorageConfig, StorageServiceFactory}
import org.sunbird.common.exception.ServerException
import org.sunbird.common.{Platform, Slug}

import java.io.File
import java.util.concurrent.TimeUnit
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
        case "gcloud" => getGCPSignedURL("113740098487205958998",
          Platform.config.getString("gcloud_client_key"),
          Platform.config.getString("gcloud_private_secret"),
          "6aef3a75efe29225e6347244de3e8f1ddd8437df", "upsmf-368011", key, ttl.get)
        case _ => getService.getSignedURL (getContainerName, key, ttl, permission)
      }
    }

  def getGCPSignedURL(clientId: String, clientEmail: String, privateKeyPkcs8: String, privateKeyIds: String, projectId: String, objectName: String, ttl: Int):  String = {
    //val credentials = ServiceAccountCredentials.fromPkcs8(clientId, clientEmail, privateKeyPkcs8, privateKeyIds, new java.util.ArrayList[String]())
    val credentials: ServiceAccountCredentials = ServiceAccountCredentials.newBuilder.setProjectId(projectId).setPrivateKeyId("6aef3a75efe29225e6347244de3e8f1ddd8437df").setPrivateKeyString("-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCqtqMByEGjddwE\n0oIkQRT4KukhPn65ozDQUfgop55VblUWeJEqmeGfTXdOTTVHpwwuYR9esrMgR5WN\n8IUGSLRmap9iyb4QBUV/gCjJIpsVu6HFMadBQCFceqzTqPMK6g6dwObNtDMxH6yP\nV47L/McwiPNoug2W+zBiRQ6YZ1GvQVY5s0KTX6EgkN/u3DW6kUu6NqcgqGuCWqUo\nEjss4HaX4D7DSbmKgOts/rKjwtDv9fgKLgbMlufwpxwWe/jygVUNvZumBARNIuVe\n+RbO6OvHb26H18KgkdDzB1VkzKX+750iIIa/KGrZHJStiw0zfri0/H0KdzbClvoO\nT6cBN/zVAgMBAAECggEAPN9dJsCKr0fSiaGqqFTQrmFWufT36SgIuu/PpmeGrP3n\nt1iMov02F8DevvwG+KLXVB5rui/MccE00eUm+gOo2DBC304CyOGoU3uWj4oYdNpw\nJ8m50ymT+87+m4bOC2FEjvn/EvCjpGuf84kMMS7UtDjRWtGlEOZG7XOkbbHBzdTQ\nGldzEgsqi2T8O5I31xZ1b2LJzAVODrv7TiVElhGcUB/1MkProjhkcyJx3B3cpClw\nY8Lq2R2urTf4NxMnmh/PmUfBzLQLauSDI/MH9NN57J1M/5uWYAIY/eaf8BtqEsbr\nXLmBP1WfNchXbfXLeadaiAX45ukt0y103qd0TmJa7QKBgQDdvgTcjKMddzfU6PeB\nXO3upl2FVLA6B89J/QiEKoeg4bkM2C3pNkGvgVA3HfHfauMhhwFee8mP14HLZQvb\n+0k9tL64CiznkuEfOBApkXJDsW0iAN3TwMj5zVRAVHWBRcexMt74CdySuKDOkV9G\n5feOXfdhOZM6z8LSfGs+2lYbQwKBgQDFFmj8Mtv4Pv5zsF1/UeFnuijkHO84dNYn\nflTB5Pmwb4Z5rhnJzG446cxr9f7E/+3yjd+xtBQf5ttPwvCBbZR20RO2jA5o/qij\nXaYHCjlE7yOpAfgU+p5K3JH9bTMLuPsSVaxBof7cFoqjFalVGmpR1qAj4UGHc9mT\nnV6CGCbqBwKBgQCTI+RV9XzHsLR7s5uJXAEGu56TOv81grkqf52HFjGpsB77Rvgw\nKLCtpUF1UYmOl37gYJWn/Lxjlr2qGgMkljqjl6x2s0nY4L5B2RHgg4MvKC0iwzBv\nsx2ppXaiuWi/v24jR35tWR3kvl72s8Bla3Q6JGBjQ7FO9U5yHd2Md5VrwQKBgAzy\nQOk4KgzvjmVpE2s2pLjcmK0LXYd23U5w1P57nQ9C9DFwB0+jNyZT7VK2MQsdyLKj\nMSuKKbxCvOtLYeMOoK8BYusd3iB1gfxhPXO+7y4hC1WhxHsUT2uZe5mLH8xIVW3J\n5OvWyVgJvwehd6MYfh1sHM7ekCBmsscokjm3fm7nAoGBAL5PXhD6rCaHGOo0KXEA\n0S6rzMI6qBzQvMyOVj7b0lwey6q+G2xl7Cc9IUmxVzhBe7daD6QSQ4dU91ZKaIys\nopfZWibHFcQm6I6FJI3ha73EOB2zyyl3xlBxK9fMQVN8gELdXhA8DBuMD+Qxj6Nr\nbqteFJLCyz7ATtETSb3+hP+G\n-----END PRIVATE KEY-----\n").setClientEmail("jenkins@upsmf-368011.iam.gserviceaccount.com").setClientId("113740098487205958998").build()
    println("credentials : ", credentials)
    val storage = StorageOptions.newBuilder.setProjectId(projectId).setCredentials(credentials).build.getService
    println("container name : ", getContainerName)
    println("object name : ", objectName)
    println("Storage : ", storage)
    val blobInfo = BlobInfo.newBuilder(BlobId.of(getContainerName, objectName)).build
    println("blob : ", blobInfo)
    val url = storage.signUrl(blobInfo, 7, TimeUnit.DAYS, Storage.SignUrlOption.withV4Signature)
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
