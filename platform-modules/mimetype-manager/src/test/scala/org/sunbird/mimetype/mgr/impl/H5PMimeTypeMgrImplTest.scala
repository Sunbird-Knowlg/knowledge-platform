package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import com.google.common.io.Resources
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.BaseMimeTypeManager

class H5PMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory {
    implicit val ss: StorageService = new StorageService()

    "upload invalid H5P file" should "return client exception" in {
        implicit val ss: StorageService = new StorageService
        val exception = intercept[ClientException] {
            new H5PMimeTypeMgrImpl().upload("do_123", new Node(), new File(Resources.getResource("validEcmlContent.zip").toURI))
        }
        exception.getMessage shouldEqual "Please Provide Valid File!"
    }

    "create H5P Zip File" should "return zip file name" in {

        val mgr = new BaseMimeTypeManager()
        var zipFile = ""
        try {
            val extractionBasePath = mgr.getBasePath("do_1234")
            zipFile = new H5PMimeTypeMgrImpl().createH5PZipFile(extractionBasePath, new File(Resources.getResource("valid_h5p_content.h5p").getPath), "do_1234")
            assert(StringUtils.isNotBlank(zipFile))
        } finally {
            FileUtils.deleteQuietly(new File(zipFile))
        }

    }

    def getNode(): Node = {
        val node = new Node()
        node.setIdentifier("do_1234")
        node.setMetadata(new util.HashMap[String, AnyRef](){{
            put("identifier", "do_1234")
            put("mimeType", "application/vnd.ekstep.h5p-archive")
            put("status","Draft")
            put("contentType", "Resource")
        }})
        node
    }

}
