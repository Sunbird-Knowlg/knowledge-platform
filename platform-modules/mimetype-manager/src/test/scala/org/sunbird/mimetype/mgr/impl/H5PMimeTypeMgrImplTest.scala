package org.sunbird.mimetype.mgr.impl

import java.io.File
import java.util

import com.google.common.io.Resources
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node
import org.sunbird.mimetype.mgr.BaseMimeTypeManager

class H5PMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers with AsyncMockFactory /*with MockitoSugar */{

    "upload invalid H5P file" should "return client exception" in {
        val exception = intercept[ClientException] {
            H5PMimeTypeMgrImpl.upload("do_123", new Node(), new File(Resources.getResource("validEcmlContent.zip").toURI))
        }
        exception.getMessage shouldEqual "Please Provide Valid File!"
    }

    "create H5P Zip File" should "return zip file name" in {
        var zipFile = ""
        try {
            val extractionBasePath = Resources.getResource("valid_h5p_content.h5p").getPath.replace("valid_h5p_content.h5p", "")
            zipFile = H5PMimeTypeMgrImpl.createH5PZipFile(extractionBasePath, new File(Resources.getResource("valid_h5p_content.h5p").getPath), "do_1234")
            assert(StringUtils.isNotBlank(zipFile))
        } finally {
//            FileUtils.deleteQuietly(new File(zipFile))
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
