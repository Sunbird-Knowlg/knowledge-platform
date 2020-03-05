package org.sunbird.mimetype.factory

import org.apache.commons.lang3.StringUtils
import org.sunbird.cloudstore.StorageService
import org.sunbird.mimetype.mgr.MimeTypeManager
import org.sunbird.mimetype.mgr.impl.{ApkMimeTypeMgrImpl, AssetMimeTypeMgrImpl, CollectionMimeTypeMgrImpl, DefaultMimeTypeMgrImpl, DocumentMimeTypeMgrImpl, EcmlMimeTypeMgrImpl, H5PMimeTypeMgrImpl, HtmlMimeTypeMgrImpl, PluginMimeTypeMgrImpl, YouTubeMimeTypeMgrImpl}

object MimeTypeManagerFactory {

	implicit val ss: StorageService = new StorageService
	
	val defaultMimeTypeMgrImpl = new DefaultMimeTypeMgrImpl
	val mimeTypeMgr = Map[String, MimeTypeManager](
		"video/youtube" -> new YouTubeMimeTypeMgrImpl,"video/x-youtube" -> new YouTubeMimeTypeMgrImpl,
		"text/x-url" -> new YouTubeMimeTypeMgrImpl,
		"application/pdf" -> new DocumentMimeTypeMgrImpl, "application/epub" -> new DocumentMimeTypeMgrImpl,
		"application/msword" -> new DocumentMimeTypeMgrImpl,
	    "assets" -> new AssetMimeTypeMgrImpl,
		"application/vnd.ekstep.ecml-archive" -> new EcmlMimeTypeMgrImpl,
		"application/vnd.ekstep.html-archive" -> new HtmlMimeTypeMgrImpl,
		"application/vnd.ekstep.content-collection" -> new CollectionMimeTypeMgrImpl,
		"application/vnd.ekstep.plugin-archive" -> new PluginMimeTypeMgrImpl,
		"application/vnd.ekstep.h5p-archive" -> new H5PMimeTypeMgrImpl,
		"application/vnd.android.package-archive" -> new ApkMimeTypeMgrImpl
	)

	def getManager(contentType: String, mimeType: String): MimeTypeManager = {
		if (StringUtils.equalsIgnoreCase("Asset", contentType)) {
			mimeTypeMgr.get("assets").get
		} else {
			if(null != mimeType)
				mimeTypeMgr.getOrElse(mimeType.toLowerCase(), defaultMimeTypeMgrImpl)
			else defaultMimeTypeMgrImpl
		}
	}
}
