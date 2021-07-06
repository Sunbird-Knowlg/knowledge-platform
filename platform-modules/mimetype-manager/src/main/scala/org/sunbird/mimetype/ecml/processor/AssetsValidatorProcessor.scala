package org.sunbird.mimetype.ecml.processor

import java.io.File

import org.apache.tika.Tika
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.Platform
import org.sunbird.common.exception.ClientException

trait AssetsValidatorProcessor extends IProcessor {

    val MAX_FILE_SIZE:Double = {if(Platform.config.hasPath("MAX_ASSET_FILE_SIZE_LIMIT")) Platform.config.getDouble("MAX_ASSET_FILE_SIZE_LIMIT") else 20971520}

    abstract override def process(ecrf: Plugin)(implicit ss: StorageService): Plugin = {
        validateAssets(ecrf)
        super.process(ecrf)
    }

    def validateAssets(plugin: Plugin) = {
        if(null != plugin.manifest){
            val medias:List[Media] = plugin.manifest.medias
            if(null != medias && !medias.isEmpty)
            medias.map(media=> {
                validateMedia(media)
            })
        }
    }

    def getAssetPath(media: Media): String = {
        if(widgetTypeAssets.contains(media.`type`)) getBasePath() + File.separator + "widgets" + File.separator + media.src
        else getBasePath() + File.separator + "assets" + File.separator + media.src
    }

    def validateAssetMimeType(file: File) = {
        val tika:Tika = new Tika()
        val mimeType = tika.detect(file)
        if(!(whiteListedMimeTypes.contains(mimeType) && !blackListedMimeTypes.contains(mimeType)))
            throw new ClientException("INVALID_MIME_TYPE", "Error! Invalid Asset Mime-Type. | Asset " + file.getName + " has Invalid Mime-Type. :" + mimeType)
    }

    def validateAssetSize(file: File) = {
        if(MAX_FILE_SIZE < file.length())
            throw new ClientException("FILE_SIZE_EXCEEDS_LIMIT", "Error! File Size Exceeded the Limit. | Asset " + file.getName + " is Bigger in Size.: " + file.length())
    }

    def validateMedia(media: Media) = {
        if(null != media) {
            val file = new File(getAssetPath(media))
            if(file.exists()) {
                validateAssetMimeType(file)
                validateAssetSize(file)
            }
        }
    }

}
