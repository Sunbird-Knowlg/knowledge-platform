package org.sunbird.cloudstore

import org.scalatest.{AsyncFlatSpec, Matchers}

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
}
