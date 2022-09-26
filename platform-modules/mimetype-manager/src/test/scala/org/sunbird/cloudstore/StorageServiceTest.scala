package org.sunbird.cloudstore

import org.scalatest.{AsyncFlatSpec, Matchers}

import java.io.File

class StorageServiceTest extends AsyncFlatSpec with Matchers {
    val ss = new StorageService

    "getService" should "return a Storage Service" in {
        val service = ss.getService
        assert(service != null)
    }

    "getContainerName" should "return the container name" in {
        val container = ss.getContainerName
        assert(container == "sunbird-content-dev")
    }

    "getSignedURL" should "return the signed url" in {
        val objectKey = "content" + File.separator + "asset" + File.separator + "do_53245" + File.separator + "abc.png"
        val preSignedURL = ss.getSignedURL(objectKey, Option.apply(600), Option.apply("w"))
        assert(preSignedURL.contains(objectKey))
    }

    "getUri" should "return the signed url" in {
        val uri = ss.getUri("content/abc.json")
        assert(uri != null)
    }

    "getMimeType" should "return the mimetype application/epub+zip for epub" in {
        val result = ss.getMimeType("test.alert.epub")
        assert(result == "application/epub+zip")
    }

    "getMimeType" should "return the mimetype application/octet-stream for h5p" in {
        val result = ss.getMimeType("test.alert.h5p")
        assert(result == "application/octet-stream")
    }

    "getMimeType" should "return the mimetype text/csv for csv" in {
        val result = ss.getMimeType("test.alert.csv")
        assert(result == "text/csv")
    }

    "getMimeType" should "return the mimetype application/pdf for pdf" in {
        val result = ss.getMimeType("test.alert.pdf")
        assert(result == "application/pdf")
    }

    "getMimeType" should "return the mimetype application/zip for zip" in {
        val result = ss.getMimeType("test.alert.zip")
        assert(result == "application/zip")
    }
}
