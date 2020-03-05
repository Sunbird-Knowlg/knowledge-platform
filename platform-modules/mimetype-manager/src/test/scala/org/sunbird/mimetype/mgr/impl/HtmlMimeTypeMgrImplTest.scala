package org.sunbird.mimetype.mgr.impl

import java.io.File

import com.google.common.io.Resources
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.dac.model.Node


class HtmlMimeTypeMgrImplTest extends AsyncFlatSpec with Matchers{
    implicit val ss: StorageService = new StorageService

    "upload invalid html file" should "return client exception" in {
        val exception = intercept[ClientException] {
            new HtmlMimeTypeMgrImpl().upload("do_123", new Node(), new File(Resources.getResource("invalidHtmlContent.zip").toURI))
        }
        exception.getMessage shouldEqual "Please Provide Valid File!"
    }

    "upload invalid html file url" should "return client exception" in {
        val exception = intercept[ClientException] {
            new HtmlMimeTypeMgrImpl().upload("do_123", new Node(), "invalid.html")
        }
        exception.getMessage shouldEqual "Please Provide Valid File Url!"
    }

}
