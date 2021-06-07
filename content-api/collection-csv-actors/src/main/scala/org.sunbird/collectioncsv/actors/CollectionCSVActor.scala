package org.sunbird.collectioncsv.actors

import org.sunbird.actor.core.BaseActor
import org.sunbird.cloudstore.StorageService
import org.sunbird.collectioncsv.manager.CollectionCSVManager.{getCloudPath, readInputFile, updateCollection, validateCollection, validateRecordsDataAuthenticity, validateRecordsDataFormat}
import org.sunbird.collectioncsv.util.CollectionTOCConstants
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response, ResponseParams}
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.managers.HierarchyManager
import org.sunbird.telemetry.logger.TelemetryManager

import javax.inject.Inject
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.immutable.{HashMap, Map}
import scala.concurrent.{ExecutionContext, Future}

class CollectionCSVActor @Inject() (implicit oec: OntologyEngineContext, ss: StorageService) extends BaseActor {

  implicit val ec: ExecutionContext = getContext().dispatcher
  
  override def onReceive(request: Request): Future[Response] = {
    request.getOperation match {
      case CollectionTOCConstants.COLLECTION_IMPORT => importCollection(request)
      case CollectionTOCConstants.COLLECTION_EXPORT => exportCollection(request)
      case _ => ERROR(request.getOperation)
    }
  }

  private def importCollection(request:Request): Future[Response] = {
    try {
      val csvRecordsAndMode = readInputFile(request)

      val inputFileExtension = csvRecordsAndMode._1
      val csvRecords = csvRecordsAndMode._2
      val mode = csvRecordsAndMode._3

      val collectionId = request.get(CollectionTOCConstants.IDENTIFIER).asInstanceOf[String]
      TelemetryManager.log("CollectionCSVActor --> uploadTOC --> collectionId: " + collectionId)

      request.put("rootId",collectionId)
      request.put("mode","edit")

      HierarchyManager.getHierarchy(request).flatMap(getHierarchyResponse => {
        val collectionHierarchyDeSer = ScalaJsonUtils.deserialize[Map[String, AnyRef]](JsonUtils.serialize(getHierarchyResponse))
        val collectionHierarchy = collectionHierarchyDeSer(CollectionTOCConstants.RESULT).asInstanceOf[Map[String, AnyRef]](CollectionTOCConstants.CONTENT).asInstanceOf[Map[String, AnyRef]]
        TelemetryManager.log("CollectionCSVActor --> uploadTOC --> after fetching collection Hierarchy: " + collectionHierarchy)

        // Validate if the mode is CREATE and children already exist in collection
        val children = collectionHierarchy(CollectionTOCConstants.CHILDREN).asInstanceOf[List[AnyRef]]
        if (mode.equals(CollectionTOCConstants.CREATE) && children.nonEmpty)
          throw new ClientException("COLLECTION_CHILDREN_EXISTS", "Collection is already having children.")
        TelemetryManager.log("CollectionCSVActor --> uploadTOC --> after Validating if the mode is CREATE and children already exist in collection")

        //Validate the data format of the input CSV records
        validateRecordsDataFormat(inputFileExtension, csvRecords, mode)
        TelemetryManager.log("CollectionCSVActor --> uploadTOC --> after validating CSV Records data format: ")

        val linkedContentsDetails: List[Map[String, AnyRef]] = {
          if (mode.equals(CollectionTOCConstants.UPDATE)) {
            // validate the data authenticity of the input CSV records' - Mapped Topics, QR Codes, Linked Contents
            validateRecordsDataAuthenticity(inputFileExtension, csvRecords, collectionHierarchy)
          }
          else List.empty[Map[String, AnyRef]]
        }
        TelemetryManager.log("CollectionCSVActor --> uploadTOC --> after validating the data authenticity of the input CSV records' - Mapped Topics, QR Codes, Linked Contents: ")

        // update the collection hierarchy
        updateCollection(collectionHierarchy, csvRecords, mode, linkedContentsDetails)
      })
    } catch {
      case e: IllegalArgumentException =>
        TelemetryManager.log("CollectionCSVActor --> IllegalArgumentException: " + e.getMessage)
        throw new ClientException("CLIENT_ERROR", e.getMessage)
      case e: ClientException =>
        TelemetryManager.log("CollectionCSVActor --> ClientException: " + e.getErrCode + " || " + e.getMessage)
        throw e
      case e: Exception =>
        TelemetryManager.log("CollectionCSVActor --> Exception: " + e.getMessage)
        throw new ServerException("SERVER_ERROR", "Something went wrong while processing the file")
    }
  }

   private def exportCollection(request: Request): Future[Response] = {
     val fileExtension = "."+request.get("fileType").asInstanceOf[String].toLowerCase
     val collectionId = request.get("identifier").asInstanceOf[String]
     if (collectionId.isBlank) {
       TelemetryManager.log("CollectionCSVActor:getTOCUrl -> Invalid Collection Id Provided")
       throw new ClientException("INVALID_COLLECTION", "Invalid Collection. Please Provide Valid Collection Identifier.")
     }

     request.put("rootId",collectionId)
     request.put("mode","edit")

     HierarchyManager.getHierarchy(request).map(getHierarchyResponse => {
       val collectionHierarchyDeSer = ScalaJsonUtils.deserialize[Map[String, AnyRef]] (JsonUtils.serialize (getHierarchyResponse))
       val collectionHierarchy = collectionHierarchyDeSer (CollectionTOCConstants.RESULT).asInstanceOf[Map[String, AnyRef]] (CollectionTOCConstants.CONTENT).asInstanceOf[Map[String, AnyRef]]
       TelemetryManager.log ("CollectionCSVActor:getTOCUrl -> collectionHierarchy: " + collectionHierarchy)
       validateCollection (collectionHierarchy)

       val cloudPath = getCloudPath(fileExtension, collectionHierarchy)
       TelemetryManager.log ("CollectionCSVActor:getTOCUrl -> cloudPath: " + cloudPath)
       TelemetryManager.log ("CollectionCSVActor:getTOCUrl -> Sending Response for Toc Download API for Collection | Id: " + collectionId)
       val collectionCSV = HashMap[String, AnyRef] (CollectionTOCConstants.TOC_URL -> cloudPath, CollectionTOCConstants.TTL -> Platform.getString("cloud_storage.upload.url.ttl", "86400") )

       val response = new Response
       val resParams = new ResponseParams
       resParams.setStatus ("successful")
       response.setParams (resParams)
       response.put(CollectionTOCConstants.COLLECTION, collectionCSV.asJava)
     })

  }

}