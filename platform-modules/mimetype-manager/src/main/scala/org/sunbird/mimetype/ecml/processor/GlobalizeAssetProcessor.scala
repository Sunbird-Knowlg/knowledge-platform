package org.sunbird.mimetype.ecml.processor

import java.io.File
import com.mashape.unirest.http.Unirest
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.{Platform, Slug}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait GlobalizeAssetProcessor extends IProcessor {

    val ASSET_DIR:String = "cloud_storage.asset.folder"
    val OBJECT_DIR:String = "cloud_storage.content.folder"
    val BASE_URL: String = Platform.getString("content.media.base_url", "https://dev.sunbirded.org")
    val timeout: Long = if(Platform.config.hasPath("asset.max_upload_time")) Platform.config.getLong("asset.max_upload_time") else 60

    abstract override def process(ecrf: Plugin)(implicit ss: StorageService): Plugin = {
        val manifest = ecrf.manifest
        val updatedMedias:List[Media] = uploadAssets(manifest.medias)
        val updatedManifest:Manifest = Manifest(manifest.id, manifest.data, manifest.innerText, manifest.cData, updatedMedias)
        super.process(Plugin(ecrf.id, ecrf.data, ecrf.innerText, ecrf.cData, ecrf.childrenPlugin, updatedManifest, ecrf.controllers, ecrf.events))
    }

    def uploadAssets(medias: List[Media])(implicit ss: StorageService, ec: ExecutionContext =  concurrent.ExecutionContext.Implicits.global): List[Media] = {
        if(null != medias) {
            val future:Future[List[Media]] = Future.sequence(medias.filter(media=> StringUtils.isNotBlank(media.id) && StringUtils.isNotBlank(media.src) && StringUtils.isNotBlank(media.`type`))
                    .map(media => {
                        Future{
                            val file: File = {
                                if (widgetTypeAssets.contains(media.`type`)) new File(getBasePath() + File.separator + "widgets" + File.separator + media.src)
                                else new File(getBasePath() + File.separator + "assets" + File.separator + media.src)
                            }
                            val mediaSrc = media.data.getOrElse("src", "").asInstanceOf[String]
                            val cloudDirName = if (!(mediaSrc.startsWith("http"))) FilenameUtils.getFullPathNoEndSeparator(mediaSrc).replace("assets/public/", "").replace("content-plugins/", "").trim else mediaSrc
                            val blobUrl = if (!(mediaSrc.startsWith("http"))) {
                                if (mediaSrc.startsWith("/")) BASE_URL + mediaSrc else BASE_URL + File.separator + mediaSrc
                            } else mediaSrc

                          TelemetryManager.log("GlobalizeAssetProcessor::uploadAssets:: uploading file:: "+file.getAbsolutePath)
                            val uploadFileUrl: Array[String] = if (StringUtils.isNoneBlank(cloudDirName) && getBlobLength(blobUrl) == 0) ss.uploadFile(cloudDirName, file)
                            else new Array[String](1)
                          TelemetryManager.log("GlobalizeAssetProcessor::uploadAssets:: uploadFileUrl:: "+uploadFileUrl)

                            if (null != uploadFileUrl && uploadFileUrl.size > 1) {
                                if (!(mediaSrc.startsWith("http") || mediaSrc.startsWith("/"))) {
                                    val temp = media.data ++ Map("src" -> ("/" + mediaSrc))
                                    Media(media.id, temp, media.innerText, media.cData, uploadFileUrl(1), media.`type`, media.childrenPlugin)
                                } else
                                    Media(media.id, media.data, media.innerText, media.cData, uploadFileUrl(1), media.`type`, media.childrenPlugin)
                            }
                            else media
                        }
                    }))
            val mediaList:List[Media] = Await.result(future, Duration.apply(timeout, "second"))
            if(null != mediaList && !mediaList.isEmpty)
                mediaList
            else medias
        } else medias
    }

   private  def getBlobLength(url:String):Long = {
        val response = Unirest.head(url).header("Content-Type", "application/json").asString
        if (response.getStatus == 200 && null!=response.getHeaders) {
            val size : Long = if(response.getHeaders.containsKey("Content-Length")) response.getHeaders.get("Content-Length").get(0).toLong
            else if(response.getHeaders.containsKey("content-length")) response.getHeaders.get("content-length").get(0).toLong else 0.toLong
            size
        } else 0.toLong
    }
}
