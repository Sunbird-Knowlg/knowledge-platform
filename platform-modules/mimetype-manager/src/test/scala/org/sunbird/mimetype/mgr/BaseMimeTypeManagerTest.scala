package org.sunbird.mimetype.mgr

import java.io.File

import com.google.common.io.Resources
import org.apache.commons.io.FileUtils
import org.sunbird.graph.dac.model.Node
import org.scalatest.{AsyncFlatSpec, Matchers}
import org.sunbird.cloudstore.StorageService
import org.sunbird.common.exception.ClientException

class BaseMimeTypeManagerTest extends AsyncFlatSpec with Matchers {
	implicit val ss: StorageService = new StorageService()
	val mgr = new BaseMimeTypeManager

	"validateUploadRequest with empty data" should "throw ClientException" in {
		val exception = intercept[ClientException] {
			mgr.validateUploadRequest("do_123", new Node(), null)
		}
		exception.getMessage shouldEqual "Please Provide Valid File Or File Url!"
	}

	"getBasePath with valid objectId" should "return a valid path" in {
		val result = mgr.getBasePath("do_123")
		assert(result.contains("/tmp/content/"))
		assert(result.contains("_temp/do_123"))
	}

	"getFieNameFromURL" should "return file name from url" in {
		val result = mgr.getFileNameFromURL("http://abc.com/content/sample.pdf")
		assert(result.contains("sample_"))
		assert(result.endsWith(".pdf"))
	}

	"getFileSize with invalid file" should "return 0 size" in {
		assert(0==mgr.getFileSize(new File("/tmp/sample.pdf")))
	}

	"copyURLToFile with valid url" should "return a valid file" in {
		val result = mgr.copyURLToFile("do_123","https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf")
		assert(result.exists())
		assert(true)
	}

	"isValidPackageStructure with valid html zip file" should "return true" in {
		val file: File = new File(Resources.getResource("validHtmlContent.zip").toURI)
		val result = mgr.isValidPackageStructure(file, List("index.html"))
		assert(result)
	}

	"isValidPackageStructure with invalid html zip file" should "return false" in {
		val file: File = new File(Resources.getResource("invalidHtmlContent.zip").toURI)
		val result = mgr.isValidPackageStructure(file, List("index.html"))
		assert(!result)
	}

	"isValidPackageStructure with valid ecml zip file" should "return true" in {
		val file: File = new File(Resources.getResource("validEcmlContent.zip").toURI)
		val result = mgr.isValidPackageStructure(file, List("index.ecml","index.json"))
		assert(result)
	}

	"extractPackage" should "extract package in specified basePath" in {
		FileUtils.deleteDirectory(new File("/tmp/validEcmlContent"))
		val file: File = new File(Resources.getResource("validEcmlContent.zip").toURI)
		val result = mgr.extractPackage(file, "/tmp/validEcmlContent")
		assert(new File("/tmp/validEcmlContent/index.ecml").exists())
	}

	"createZipPackage" should "Zip the files into a single package" in {
		try {
			mgr.createZipPackage(Resources.getResource("filesToZip").getPath, Resources.getResource("filesToZip").getPath.replace("filesToZip", "")
				+ "test_zip.zip")
			assert(new File(Resources.getResource("test_zip.zip").getPath).exists())
		} finally {
			FileUtils.deleteQuietly(new File(Resources.getResource("test_zip.zip").getPath))
		}
	}


	"createZipPackageWithNestedFiles" should "Zip the files into a single package" in {
		try {
			mgr.createZipPackage(Resources.getResource("filesToZipNested").getPath, Resources.getResource("filesToZip").getPath.replace("filesToZip", "")
				+ "test_zip_nested.zip")
			assert(new File(Resources.getResource("test_zip_nested.zip").getPath).exists())
		}
		finally {
			FileUtils.deleteQuietly(new File(Resources.getResource("test_zip_nested.zip").getPath))
		}
	}

}
