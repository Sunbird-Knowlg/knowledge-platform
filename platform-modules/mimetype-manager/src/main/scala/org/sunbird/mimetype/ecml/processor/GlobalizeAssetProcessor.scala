package org.sunbird.mimetype.ecml.processor

import java.io.File

import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.{Platform, Slug}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait GlobalizeAssetProcessor extends IProcessor {

    val ASSET_DIR:String = "cloud_storage.asset.folder"
    val OBJECT_DIR:String = "cloud_storage.content.folder"
    val timeout: Long = if(Platform.config.hasPath("asset.max_upload_time")) Platform.config.getLong("asset.max_upload_time") else 60

    abstract override def process(ecrf: Plugin)(implicit ss: StorageService): Plugin = {
        val manifest = ecrf.manifest
        val updatedMedias:List[Media] = uploadAssets(manifest.medias)
        val updatedManifest:Manifest = Manifest(manifest.id, manifest.data, manifest.innerText, manifest.cData, updatedMedias)
        super.process(Plugin(ecrf.id, ecrf.data, ecrf.innerText, ecrf.cData, ecrf.childrenPlugin, updatedManifest, ecrf.controllers, ecrf.events))
    }

    def uploadAssets(medias: List[Media])(implicit ss: StorageService, ec: ExecutionContext =  concurrent.ExecutionContext.Implicits.global): List[Media] = {
        val future:Future[List[Media]] = Future.sequence(medias.filter(media=> StringUtils.isNotBlank(media.id) && StringUtils.isNotBlank(media.src) && StringUtils.isNotBlank(media.`type`))
                .map(media => {
                    Future{
                        val file:File  = {
                            if(widgetTypeAssets.contains(media.`type`)) new File(getBasePath() + File.separator + "widgets" + File.separator + media.src)
                            else new File(getBasePath() + File.separator + "assets" + File.separator + media.src)
                        }
                        val cloudDirName = {
                            val assetDir = if(Platform.config.hasPath(ASSET_DIR)) Platform.config.getString(ASSET_DIR) else System.currentTimeMillis()
                            Platform.config.getString(OBJECT_DIR) + File.separator + Slug.makeSlug(getIdentifier(), true) + assetDir
                        }
                        val uploadFileUrl: Array[String] = ss.uploadFile(cloudDirName, file)
                        if(null != uploadFileUrl && uploadFileUrl.size > 1)
                            Media(media.id, media.data, media.innerText, media.cData, uploadFileUrl(1), media.`type`, media.childrenPlugin)
                        else media
                    }
                }))
        val mediaList:List[Media] = Await.result(future, Duration.apply(timeout, "second"))
        if(null != mediaList && !mediaList.isEmpty)
            mediaList
        else medias
    }
}
