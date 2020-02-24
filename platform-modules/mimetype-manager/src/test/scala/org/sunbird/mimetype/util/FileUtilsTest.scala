package org.sunbird.mimetype.util

import java.io.File
import java.nio.file.{Files, Paths}

import com.google.common.io.Resources
import org.scalatest.FlatSpec

import scala.io.Source

class FileUtilsTest extends FlatSpec {

    "extractPackage" should "extract package in specified basePath" in {

        val file: File = new File(Resources.getResource("test_ecml.zip").toURI)
        FileUtils.extractPackage(file, "./test_ecml")
        try{
            assert(FileUtils.isValidPackageStructure(file))
        }finally{
            Files.deleteIfExists(Paths.get("./test_ecml"))
        }
    }

}
