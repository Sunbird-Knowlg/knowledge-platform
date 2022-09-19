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
}
