package org.sunbird.mimetype.factory

import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.mimetype.mgr.impl.{AssetMimeTypeMgrImpl, CollectionMimeTypeMgrImpl, DefaultMimeTypeMgrImpl, DocumentMimeTypeMgrImpl, EcmlMimeTypeMgrImpl, H5PMimeTypeMgrImpl, HtmlMimeTypeMgrImpl, PluginMimeTypeMgrImpl, YouTubeMimeTypeMgrImpl}

class MimeTypeManagerFactoryTest extends FlatSpec with Matchers {

	"getManager with mimeType text/x-url" should "give instance of YouTubeMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("TextBook", "text/x-url")
		assert(null != mgr)
		assert(mgr.isInstanceOf[YouTubeMimeTypeMgrImpl])
	}

	"getManager with mimeType video/x-youtube" should "give instance of YouTubeMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("TextBook", "video/x-youtube")
		assert(null != mgr)
		assert(mgr.isInstanceOf[YouTubeMimeTypeMgrImpl])
	}

	"getManager with mimeType video/youtube" should "give instance of YouTubeMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("TextBook", "video/youtube")
		assert(null != mgr)
		assert(mgr.isInstanceOf[YouTubeMimeTypeMgrImpl])
	}

	"getManager with mimeType application/pdf" should "give instance of DocumentMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("TextBook", "application/pdf")
		assert(null != mgr)
		assert(mgr.isInstanceOf[DocumentMimeTypeMgrImpl])
	}

	"getManager with mimeType application/epub" should "give instance of DocumentMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("TextBook", "application/epub")
		assert(null != mgr)
		assert(mgr.isInstanceOf[DocumentMimeTypeMgrImpl])
	}

	"getManager with mimeType application/msword" should "give instance of DocumentMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("TextBook", "application/msword")
		assert(null != mgr)
		assert(mgr.isInstanceOf[DocumentMimeTypeMgrImpl])
	}

	"getManager with mimeType assets" should "give instance of AssetMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("Resource", "assets")
		assert(null != mgr)
		assert(mgr.isInstanceOf[AssetMimeTypeMgrImpl])
	}

	"getManager with contentType Assets" should "give instance of AssetMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("Asset", "image/jpeg")
		assert(null != mgr)
		assert(mgr.isInstanceOf[AssetMimeTypeMgrImpl])
	}

	"getManager with mimeType application/vnd.ekstep.ecml-archive" should "give instance of EcmlMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("Resource", "application/vnd.ekstep.ecml-archive")
		assert(null != mgr)
		assert(mgr.isInstanceOf[EcmlMimeTypeMgrImpl])
	}

	"getManager with mimeType application/vnd.ekstep.html-archive" should "give instance of HtmlMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("Resource", "application/vnd.ekstep.html-archive")
		assert(null != mgr)
		assert(mgr.isInstanceOf[HtmlMimeTypeMgrImpl])
	}

	"getManager with mimeType application/vnd.ekstep.content-collection" should "give instance of CollectionMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("Resource", "application/vnd.ekstep.content-collection")
		assert(null != mgr)
		assert(mgr.isInstanceOf[CollectionMimeTypeMgrImpl])
	}

	"getManager with mimeType application/vnd.ekstep.plugin-archive" should "give instance of PluginMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("Resource", "application/vnd.ekstep.plugin-archive")
		assert(null != mgr)
		assert(mgr.isInstanceOf[PluginMimeTypeMgrImpl])
	}

	"getManager with mimeType application/vnd.ekstep.h5p-archive" should "give instance of H5PMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("Resource", "application/vnd.ekstep.h5p-archive")
		assert(null != mgr)
		assert(mgr.isInstanceOf[H5PMimeTypeMgrImpl])
	}

	"getManager with blank mimeType" should "give instance of DefaultMimeTypeMgrImpl" in {
		val mgr = MimeTypeManagerFactory.getManager("Resource", null)
		assert(null != mgr)
		assert(mgr.isInstanceOf[DefaultMimeTypeMgrImpl])
	}

}
