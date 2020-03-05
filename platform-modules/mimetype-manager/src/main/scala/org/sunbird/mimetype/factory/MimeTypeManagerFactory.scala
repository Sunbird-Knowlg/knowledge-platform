package org.sunbird.mimetype.factory

import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.mimetype.mgr.MimeTypeManager
import org.sunbird.mimetype.mgr.impl.{ApkMimeTypeMgrImpl, AssetMimeTypeMgrImpl, CollectionMimeTypeMgrImpl, DefaultMimeTypeMgrImpl, DocumentMimeTypeMgrImpl, EcmlMimeTypeMgrImpl, H5PMimeTypeMgrImpl, HtmlMimeTypeMgrImpl, PluginMimeTypeMgrImpl, YouTubeMimeTypeMgrImpl}

object MimeTypeManagerFactory {

	implicit val ss: StorageService = new StorageService

	def getManager(contentType: String, mimeType: String): MimeTypeManager = {
		if (StringUtils.equalsIgnoreCase("Asset", contentType)) {
			new AssetMimeTypeMgrImpl
		} else StringUtils.lowerCase(mimeType) match {
			case "video/youtube" | "video/x-youtube" | "text/x-url" => new YouTubeMimeTypeMgrImpl
			case "application/pdf" | "application/epub" | "application/msword" => new DocumentMimeTypeMgrImpl
			case "assets" => new AssetMimeTypeMgrImpl
			case "application/vnd.ekstep.ecml-archive" => new EcmlMimeTypeMgrImpl
			case "application/vnd.ekstep.html-archive" => new HtmlMimeTypeMgrImpl
			case "application/vnd.ekstep.content-collection" => new CollectionMimeTypeMgrImpl
			case "application/vnd.ekstep.plugin-archive" => new PluginMimeTypeMgrImpl
			case "application/vnd.ekstep.h5p-archive" => new H5PMimeTypeMgrImpl
			case "application/vnd.android.package-archive" => new ApkMimeTypeMgrImpl
			case _ => new DefaultMimeTypeMgrImpl
		}
	}
}
