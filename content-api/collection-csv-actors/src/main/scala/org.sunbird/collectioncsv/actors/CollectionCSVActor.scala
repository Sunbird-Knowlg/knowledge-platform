package org.sunbird.collectioncsv.actors

import org.sunbird.actor.core.BaseActor
import org.sunbird.cloudstore.StorageService
import org.sunbird.collectioncsv.manager.CollectionCSVManager.{getCloudPath, readInputFile, updateCollection, validateCollection, validateInputData}
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

      updateRequestWithMode(request)

      HierarchyManager.getHierarchy(request).flatMap(getHierarchyResponse => {
        val collectionHierarchy = getCollectionHierarchy(getHierarchyResponse)
        TelemetryManager.log("CollectionCSVActor --> uploadTOC --> after fetching collection Hierarchy: " + collectionHierarchy)
        validateCollection(collectionHierarchy,"import")
        val linkedContentsDetails = validateInputData(csvRecordsAndMode._1, csvRecordsAndMode._2, csvRecordsAndMode._3, collectionHierarchy)
        // update the collection hierarchy
        updateCollection(collectionHierarchy, csvRecordsAndMode._2, csvRecordsAndMode._3, linkedContentsDetails)
      })
    } catch {
      case e: ClientException => throw e
      case e: Exception => throw new ServerException("SERVER_ERROR", "Something went wrong while processing the file")
    }
  }

   private def exportCollection(request: Request): Future[Response] = {
     val fileExtension = "."+request.get("fileType").asInstanceOf[String].toLowerCase

     updateRequestWithMode(request)

     HierarchyManager.getHierarchy(request).map(getHierarchyResponse => {
       val collectionHierarchy = getCollectionHierarchy(getHierarchyResponse)
       TelemetryManager.log ("CollectionCSVActor:getTOCUrl -> collectionHierarchy: " + collectionHierarchy)
       validateCollection(collectionHierarchy,"export")

       val cloudPath = getCloudPath(fileExtension, collectionHierarchy)
       TelemetryManager.log ("CollectionCSVActor:getTOCUrl -> Sending Response for Toc Download API for Collection | Id: " + request.get(CollectionTOCConstants.IDENTIFIER).asInstanceOf[String])
       exportResponse(cloudPath)
     })

  }

  private def exportResponse(cloudPath: String): Response = {
    val collectionCSV = HashMap[String, AnyRef] (CollectionTOCConstants.TOC_URL -> cloudPath, CollectionTOCConstants.TTL -> Platform.getString("cloud_storage.upload.url.ttl", "86400") )

    val response = new Response
    val resParams = new ResponseParams
    resParams.setStatus ("successful")
    response.setParams (resParams)
    response.put(CollectionTOCConstants.COLLECTION, collectionCSV.asJava)
  }

  private def getCollectionHierarchy(getHierarchyResponse: Response): Map[String, AnyRef] = {
    val collectionHierarchyDeSer = ScalaJsonUtils.deserialize[Map[String, AnyRef]](JsonUtils.serialize(getHierarchyResponse))
    collectionHierarchyDeSer(CollectionTOCConstants.RESULT).asInstanceOf[Map[String, AnyRef]](CollectionTOCConstants.CONTENT).asInstanceOf[Map[String, AnyRef]]
  }

  private def updateRequestWithMode(request: Request) {
    request.put("rootId",request.get(CollectionTOCConstants.IDENTIFIER).asInstanceOf[String])
    request.put("mode","edit")
  }

}