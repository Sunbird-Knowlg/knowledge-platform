package org.sunbird.mimetype.util

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipFile}

import org.apache.tika.Tika
import org.sunbird.common.Platform
import org.sunbird.common.exception.ClientException

import scala.collection.JavaConversions._

object FileUtils {
    private val tika: Tika = new Tika()
    private val allowedExtensions: Set[String] = Set("doc", "docx", "ppt", "pptx", "key", "odp", "pps", "odt", "wpd", "wps", "wks")
    private val DEFAULT_PACKAGE_MIME_TYPE = "application/zip"
    private val maxPackageSize = if(Platform.config.hasPath("MAX_CONTENT_PACKAGE_FILE_SIZE_LIMIT")) Platform.config.getDouble("MAX_CONTENT_PACKAGE_FILE_SIZE_LIMIT") else 52428800

    def isValidPackageStructure(file: File):Boolean = {
        val validFileNameList:List[String] = List("index.json", "index.ecml")
        val zipFile:ZipFile = new ZipFile(file)
        try{
            val entries = validFileNameList.filter(fileName => null != zipFile.getEntry(fileName)).toList
            (null != entries && !entries.isEmpty)
        }
        catch {
            case e: Exception => throw new ClientException("ERR_INVALID_FILE", "Please Provide Valid File!")
        } finally {
            zipFile.close()
        }
    }

    def validateFilePackage(file: File) = {
        if(null != file && file.exists()){
            val mimeType = tika.detect(file)
            if(!DEFAULT_PACKAGE_MIME_TYPE.contentEquals(mimeType)) throw new ClientException("VALIDATOR_ERROR", "INVALID_CONTENT_PACKAGE_FILE_MIME_TYPE_ERROR | [The uploaded package is invalid]")
            if(!isValidPackageStructure(file)) throw new ClientException("VALIDATOR_ERROR", "INVALID_CONTENT_PACKAGE_STRUCTURE_ERROR | ['index' file and other folders (assets, data & widgets) should be at root location]")
            if(file.length() > maxPackageSize) throw new ClientException("VALIDATOR_ERROR", "INVALID_CONTENT_PACKAGE_SIZE_ERROR | [Content Package file size is too large]")
        }
        else{
            throw new ClientException("ERR_INVALID_FILE", "File does not exists")
        }
    }

    def extractPackage(file: File, basePath: String) = {
        val zipFile = new ZipFile(file)
        for (entry <- zipFile.entries) {
            val path = Paths.get(basePath + File.separator + entry.getName)
            if (entry.isDirectory) {
                Files.createDirectories(path)
            } else {
                Files.createDirectories(path.getParent)
                Files.copy(zipFile.getInputStream(entry), path)
            }
        }
    }
}
