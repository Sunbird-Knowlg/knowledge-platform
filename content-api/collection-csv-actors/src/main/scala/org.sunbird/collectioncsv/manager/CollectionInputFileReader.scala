package org.sunbird.collectioncsv.manager

import org.apache.commons.csv.CSVRecord
import org.sunbird.cloudstore.StorageService
import org.sunbird.collectioncsv.manager.CollectionCSVManager.createCSVFileAndStore
import org.sunbird.collectioncsv.util.CollectionTOCConstants
import org.sunbird.collectioncsv.util.CollectionTOCConstants.COLLECTION_CSV_FILE_EXTENSION
import org.sunbird.collectioncsv.validator.CollectionCSVValidator.{readInputCSV, validateCSVRecordsDataAuthenticity, validateCSVRecordsDataFormat}
import org.sunbird.common.Platform
import org.sunbird.common.Slug.makeSlug
import org.sunbird.common.dto.Request
import org.sunbird.content.util.CopyManager.copyURLToFile
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.telemetry.logger.TelemetryManager

import java.io.File
import java.util
import scala.collection.immutable.Map
import scala.concurrent.ExecutionContext

trait CollectionInputFileReader {

  private val CONTENT_FOLDER = "cloud_storage.content.folder"

  def readInputFile(request: Request): (String, util.List[CSVRecord], String) = {
    val file = if(request.getRequest.getOrDefault("fileUrl","").asInstanceOf[String].nonEmpty){
      val copiedFile = copyURLToFile(request.get(CollectionTOCConstants.IDENTIFIER).asInstanceOf[String], request.getRequest.getOrDefault("fileUrl", "").asInstanceOf[String])
      request.getRequest.put("file",copiedFile)
      copiedFile
    } else request.getRequest.get("file").asInstanceOf[File]

    val extension = "."+file.getAbsolutePath.split("\\.").last.toLowerCase

    extension match {
      case COLLECTION_CSV_FILE_EXTENSION => readInputCSV(request)
    }
  }

  def validateRecordsDataFormat(fileExtension: String, csvRecords: util.List[CSVRecord], mode: String) {
    fileExtension match {
      case COLLECTION_CSV_FILE_EXTENSION => validateCSVRecordsDataFormat(csvRecords, mode)
    }
  }


  def validateRecordsDataAuthenticity(fileExtension: String, csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): List[Map[String, AnyRef]] = {
    fileExtension match {
      case COLLECTION_CSV_FILE_EXTENSION => validateCSVRecordsDataAuthenticity(csvRecords, collectionHierarchy)
    }
  }

  def getCloudPath(fileExtension: String, collectionHierarchy: Map[String, AnyRef])(implicit ss: StorageService): String = {
    val collectionId = collectionHierarchy(CollectionTOCConstants.IDENTIFIER).asInstanceOf[String]
    val contentVersionKey = collectionHierarchy(CollectionTOCConstants.VERSION_KEY).asInstanceOf[String]
    val collectionNameSlug = makeSlug(collectionHierarchy(CollectionTOCConstants.NAME).asInstanceOf[String], true)
    val collectionTocFileName = collectionId + "_" + collectionNameSlug + "_" + contentVersionKey
    val prefix = Platform.getString(CONTENT_FOLDER, "content") + "/" + collectionHierarchy(CollectionTOCConstants.CONTENT_TYPE).toString.toLowerCase + "/toc/" + collectionTocFileName + fileExtension
    TelemetryManager.log("CollectionCSVManager --> getCloudPath --> invoke getUri using prefix: " + prefix)
    val path = ss.getUri(prefix)
    TelemetryManager.log("CollectionCSVManager --> getCloudPath --> path: " + path)
    if (path == null || path.isEmpty || path.isBlank) {
      fileExtension match {
        case COLLECTION_CSV_FILE_EXTENSION => createCSVFileAndStore(collectionHierarchy, collectionTocFileName+fileExtension)
      }
    } else path
  }


}
