package org.sunbird.mimetype.factory

import org.apache.commons.lang3.StringUtils
import org.sunbird.mimetype.mgr.MimeTypeManager
import org.sunbird.mimetype.mgr.impl.{AssetMimeTypeMgrImpl, CollectionMimeTypeMgrImpl, DefaultMimeTypeMgrImpl, DocumentMimeTypeMgrImpl, EcmlMimeTypeMgrImpl, H5PMimeTypeMgrImpl, HtmlMimeTypeMgrImpl, PluginMimeTypeMgrImpl, YouTubeMimeTypeMgrImpl}

object MimeTypeManagerFactory {

	def getManager(contentType: String, mimeType: String): MimeTypeManager = {
		if (StringUtils.equalsIgnoreCase("Asset", contentType)) {
			AssetMimeTypeMgrImpl
		} else StringUtils.lowerCase(mimeType) match {
			case "video/youtube" | "video/x-youtube" | "text/x-url" => YouTubeMimeTypeMgrImpl
			case "application/pdf" | "application/epub" | "application/msword" => DocumentMimeTypeMgrImpl
			case "assets" => AssetMimeTypeMgrImpl
			case "application/vnd.ekstep.ecml-archive" => EcmlMimeTypeMgrImpl
			case "application/vnd.ekstep.html-archive" => HtmlMimeTypeMgrImpl
			case "application/vnd.ekstep.content-collection" => CollectionMimeTypeMgrImpl
			case "application/vnd.ekstep.plugin-archive" => PluginMimeTypeMgrImpl
			case "application/vnd.ekstep.h5p-archive" => H5PMimeTypeMgrImpl
//			case "application/vnd.android.package-archive" => ApkMimeTypeMgrImpl
			case _ => DefaultMimeTypeMgrImpl
		}
	}
}
