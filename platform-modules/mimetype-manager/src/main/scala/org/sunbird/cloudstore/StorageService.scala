package org.sunbird.cloudstore

import org.apache.tika.Tika
import org.sunbird.cloud.storage.IStorageService
import org.sunbird.common.{Platform, Slug}
import org.sunbird.common.exception.ServerException
import org.sunbird.telemetry.logger.TelemetryManager

import java.io.File
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters._
import scala.jdk.CollectionConverters._

/**
 * Wrapper around the cloud-storage SDK's [[IStorageService]].
 *
 * The [[IStorageService]] instance is provided by [[modules.StorageModule]] via Guice DI,
 * which reads `cloud_storage_type` and `cloud_storage_auth_type` from the Play config and
 * builds the appropriate [[org.sunbird.cloud.storage.StorageConfig]] at startup.
 *
 * All actors that need cloud storage operations inject this class directly, e.g.:
 * {{{
 *   class ContentActor @Inject()(implicit val ss: StorageService, ...) extends BaseActor { ... }
 * }}}
 */
@Singleton  // javax.inject.Singleton annotation (not a trait)
class StorageService @Inject()(storageService: IStorageService) {

    val storageType: String =
        if (Platform.config.hasPath("cloud_storage_type")) Platform.config.getString("cloud_storage_type") else ""

    /** Returns the Guice-managed [[IStorageService]] singleton. */
    def getService: IStorageService = storageService

    def getContainerName: String = {
        if (Platform.config.hasPath("cloud_storage_container"))
            Platform.config.getString("cloud_storage_container")
        else
            throw new ServerException("ERR_INVALID_CLOUD_STORAGE", "Cloud Storage Container name not configured.")
    }

    def formatUrl(url: String): String = {
        if (storageType == "oci") {
            val newHostname = if (Platform.config.hasPath("cloud_storage_proxy_host"))
                Platform.config.getString("cloud_storage_proxy_host") else ""
            val regex = "(?<=://)([^/]+)".r
            regex.replaceAllIn(url, newHostname)
        } else url
    }

    def uploadFile(folderName: String, file: File, slug: Option[Boolean] = Option(true)): Array[String] = {
        val slugFile = if (slug.getOrElse(true)) Slug.createSlugFile(file) else file
        val objectKey = folderName + "/" + slugFile.getName
        val url = getService.upload(getContainerName, slugFile.getAbsolutePath, objectKey,
            false, 1, 5, null.asInstanceOf[Integer])
        Array[String](objectKey, formatUrl(url))
    }

    def uploadDirectory(folderName: String, directory: File, slug: Option[Boolean] = Option(true)): Array[String] = {
        val slugFile = if (slug.getOrElse(true)) Slug.createSlugFile(directory) else directory
        val objectKey = folderName + File.separator
        val url = getService.upload(getContainerName, slugFile.getAbsolutePath, objectKey,
            true, 1, 5, null.asInstanceOf[Integer])
        Array[String](objectKey, formatUrl(url))
    }

    def uploadDirectoryAsync(folderName: String, directory: File, slug: Option[Boolean] = Option(true))
                            (implicit ec: ExecutionContext): Future[List[String]] = {
        val slugFile = if (slug.getOrElse(true)) Slug.createSlugFile(directory) else directory
        val objectKey = folderName + File.separator
        getService.uploadFolder(getContainerName, slugFile.getAbsolutePath, objectKey,
            false, null.asInstanceOf[Integer], null.asInstanceOf[Integer], 1)
            .asScala
            .map(_.asScala.toList)
    }

    def getObjectSize(key: String): Double = {
        val blob = getService.getObject(getContainerName, key, false)
        blob.getContentLength.toDouble
    }

    def copyObjectsByPrefix(source: String, destination: String): Unit =
        getService.copyObjects(getContainerName, source, getContainerName, destination, true)

    def deleteFile(key: String, isDirectory: Option[Boolean] = Option(false)): Unit =
        getService.deleteObject(getContainerName, key, isDirectory.getOrElse(false))

    def getSignedURL(key: String, ttl: Option[Int], permission: Option[String]): String =
        // v2.0.0: method renamed from getPutSignedURL to getSignedURL.
        // CSP credential resolution is handled at service startup via StorageModule/AuthType.
        getService.getSignedURL(getContainerName, key, ttl.map(i => i: Integer).orNull, permission.orNull)

    def getUri(key: String): String =
        try {
            getService.getUri(getContainerName, key, false)
        } catch {
            case e:Exception =>
              TelemetryManager.error("StorageService --> getUri --> Exception: " + e.getMessage, e)
              ""
        }

    def getMimeType(fileName: String): String = new Tika().detect(fileName)
}
