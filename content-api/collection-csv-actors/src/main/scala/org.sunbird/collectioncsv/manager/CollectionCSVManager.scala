package org.sunbird.collectioncsv.manager

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.csv.{CSVFormat, CSVPrinter, CSVRecord, QuoteMode}
import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.FileUtils.{deleteQuietly, touch}
import org.sunbird.cloudstore.StorageService
import org.sunbird.collectioncsv.util.CollectionTOCConstants
import org.sunbird.collectioncsv.util.CollectionTOCConstants.COLLECTION_TOC_ALLOWED_MIMETYPE
import org.sunbird.collectioncsv.util.CollectionTOCUtil.linkDIALCode
import org.sunbird.collectioncsv.validator.CollectionCSVValidator.{collectionNodeIdentifierHeader, collectionOutputTocHeaders, contentTypeToUnitTypeMapping, createCSVMandatoryHeaderCols, folderHierarchyHdrColumnsList, linkedContentHdrColumnsList, mappedTopicsHeader, maxFolderLevels}
import org.sunbird.common.{JsonUtils, Platform}
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.common.exception.{ClientException, ServerException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.managers.UpdateHierarchyManager
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.utils.HierarchyConstants
import org.sunbird.utils.HierarchyConstants.MIME_TYPE

import java.io.{File, FileOutputStream, IOException, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.util
import scala.collection.immutable.{ListMap, Map}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters.{asJavaIterableConverter, mapAsScalaMapConverter}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object CollectionCSVManager extends CollectionInputFileReader  {

  private val CONTENT_FOLDER = "cloud_storage.content.folder"

  def getCode(code: String): String = {DigestUtils.md5Hex(code)}

  def validateInputData(inputFileExtension: String, csvRecords: util.List[CSVRecord], mode: String, collectionHierarchy: Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): List[Map[String, AnyRef]] = {
    // Validate if the mode is CREATE and children already exist in collection
    val children = collectionHierarchy(CollectionTOCConstants.CHILDREN).asInstanceOf[List[AnyRef]]
    if (mode.equals(CollectionTOCConstants.CREATE) && children.nonEmpty)
      throw new ClientException("COLLECTION_CHILDREN_EXISTS", "The “Folder Identifier” column is missing. Please correct and upload again.")

    //Validate the data format of the input CSV records
    validateRecordsDataFormat(inputFileExtension, csvRecords, mode)

    // if mode=UPDATE, validate the data authenticity of the input CSV records' - Mapped Topics, QR Codes, Linked Contents
    if (mode.equals(CollectionTOCConstants.UPDATE)) validateRecordsDataAuthenticity(inputFileExtension, csvRecords, collectionHierarchy) else List.empty[Map[String, AnyRef]]
  }

  def validateCollection(collection: Map[String, AnyRef], mode: String) {
    if (!COLLECTION_TOC_ALLOWED_MIMETYPE.equalsIgnoreCase(collection(MIME_TYPE).toString))
      throw new ClientException("INVALID_COLLECTION", "Invalid Collection. Please Provide Valid Collection Identifier.")
    if(mode.equalsIgnoreCase("export")) {
      val children = collection(CollectionTOCConstants.CHILDREN).asInstanceOf[List[AnyRef]]
      if (children.isEmpty) throw new ClientException("COLLECTION_CHILDREN_NOT_EXISTS", "No Children Exists for given Collection.")
    }
  }

  def updateCollection(collectionHierarchy: Map[String, AnyRef], csvRecords: util.List[CSVRecord], mode: String, linkedContentsDetails: List[Map[String, AnyRef]])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {
    val folderInfoMap = scala.collection.mutable.LinkedHashMap.empty[String, AnyRef]

    //prepare Map(folderInfoMap) of each folder with its details from the csvRecords
    populateFolderInfoMap(folderInfoMap, csvRecords, mode)

    // Prepare nodesMetadata and hierarchyMetadata using the folderInfoMap
    val nodesMetadata = getNodesMetadata(folderInfoMap, mode, collectionHierarchy.getOrElse(CollectionTOCConstants.FRAMEWORK,"").asInstanceOf[String], collectionHierarchy(CollectionTOCConstants.CONTENT_TYPE).toString)
    val hierarchyMetadata = getHierarchyMetadata(folderInfoMap, mode, linkedContentsDetails, collectionHierarchy(CollectionTOCConstants.IDENTIFIER).toString, collectionHierarchy(CollectionTOCConstants.NAME).toString, collectionHierarchy(CollectionTOCConstants.CONTENT_TYPE).toString)

    // Invoke UpdateHierarchyManager to update the collection hierarchy
    val updateHierarchyResponse = UpdateHierarchyManager.updateHierarchy(getUpdateHierarchyRequest(nodesMetadata, hierarchyMetadata))
    TelemetryManager.log("CollectionCSVManager:updateCollection --> after invoking updateHierarchyManager: " + updateHierarchyResponse)

    // Invoke DIAL code linking if mode=UPDATE
    if(mode.equals(CollectionTOCConstants.UPDATE)) {
      linkDIALCodes(folderInfoMap, collectionHierarchy(CollectionTOCConstants.CHANNEL).toString, collectionHierarchy(CollectionTOCConstants.IDENTIFIER).toString)
    }

    updateHierarchyResponse
  }

  def createCSVFileAndStore(collectionHierarchy: Map[String, AnyRef], collectionTocFileName: String)(implicit ss: StorageService): String = {
    val collectionName = collectionHierarchy(CollectionTOCConstants.NAME).toString
    val collectionType = collectionHierarchy(CollectionTOCConstants.CONTENT_TYPE).toString
    val collectionUnitType = contentTypeToUnitTypeMapping(collectionType)
    val childrenHierarchy = collectionHierarchy(CollectionTOCConstants.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]]
    val nodesInfoList = prepareNodeInfo(collectionUnitType, childrenHierarchy, Map.empty[String, AnyRef], "")
    val nodesMap = ListMap(nodesInfoList.flatten.toMap[String, AnyRef].toSeq.sortBy(_._1):_*)
    val maxAllowedContentSize = Platform.getInteger(CollectionTOCConstants.SUNBIRD_TOC_MAX_FIRST_LEVEL_UNITS,30)

    val csvFile: File = new File(collectionTocFileName)
    var out: OutputStreamWriter = null
    var csvPrinter: CSVPrinter = null
    try{
      deleteQuietly(csvFile)
      TelemetryManager.log("CollectionCSVManager:createFileAndStore -> Creating file for CSV at Location: " + csvFile.getAbsolutePath)
      touch(csvFile)

      out = new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8)
      out.write(ByteOrderMark.UTF_BOM)

      val csvFormat = CSVFormat.DEFAULT.withFirstRecordAsHeader().withRecordSeparator(System.lineSeparator()).withQuoteMode(QuoteMode.NON_NUMERIC)
      TelemetryManager.log("CollectionCSVManager:createFileAndStore -> Writing Headers to Output Stream for Collection | Id " + collectionHierarchy(CollectionTOCConstants.IDENTIFIER).toString)
      csvPrinter = new CSVPrinter(out, csvFormat)
      csvPrinter.printRecord(collectionOutputTocHeaders.asJava)
      nodesMap.foreach(record => {
        val nodeDepthIndex = record._1
        val nodeInfo = record._2.asInstanceOf[Map[String, AnyRef]]
        if(nodeInfo(CollectionTOCConstants.CONTENT_TYPE).toString.equalsIgnoreCase(collectionUnitType)) {
          val nodeID = nodeInfo(CollectionTOCConstants.IDENTIFIER).toString
          val recordToWrite = ListBuffer.empty[String]
          recordToWrite.append(collectionName)
          recordToWrite.append(nodeID)

          val foldersLevel = nodeDepthIndex.split(":")
          val foldersLevelId = StringBuilder.newBuilder
          for (iCounter <- 0 until maxFolderLevels) {
            if (iCounter < foldersLevel.size) {
              if (iCounter == 0)
                foldersLevelId ++= foldersLevel(iCounter)
              else {
                foldersLevelId ++= ":"
                foldersLevelId ++= foldersLevel(iCounter)
              }
              val parentNode = nodesMap(foldersLevelId.toString).asInstanceOf[Map[String, AnyRef]]
              recordToWrite.append(parentNode(CollectionTOCConstants.NAME).toString)
            }
            else {
              recordToWrite.append(null)
            }
          }

          val mappedTopics = if (nodeInfo(CollectionTOCConstants.TOPIC).asInstanceOf[List[String]].nonEmpty) nodeInfo(CollectionTOCConstants.TOPIC).asInstanceOf[List[String]].mkString(",") else null
          val keywords = if (nodeInfo(CollectionTOCConstants.KEYWORDS).asInstanceOf[List[String]].nonEmpty) nodeInfo(CollectionTOCConstants.KEYWORDS).asInstanceOf[List[String]].mkString(",") else null
          val linkedContentsList = nodeInfo(CollectionTOCConstants.LINKED_CONTENT).asInstanceOf[Seq[String]]

          recordToWrite.append(if (nodeInfo(CollectionTOCConstants.DESCRIPTION).toString.nonEmpty) nodeInfo(CollectionTOCConstants.DESCRIPTION).toString else null)
          recordToWrite.append(mappedTopics)
          recordToWrite.append(keywords)
          recordToWrite.append(nodeInfo(CollectionTOCConstants.QR_CODE_REQUIRED).toString)
          recordToWrite.append(if (nodeInfo(CollectionTOCConstants.QR_CODE).toString.nonEmpty) nodeInfo(CollectionTOCConstants.QR_CODE).toString else null)

          for (idx <- 0 until maxAllowedContentSize) {
            if (idx < linkedContentsList.size) {
              recordToWrite.append(linkedContentsList(idx))
            }
            else {
              recordToWrite.append(null)
            }
          }

          csvPrinter.printRecord(recordToWrite.toList.asJava)
        }
      })

      csvPrinter.flush()

      val folder = Platform.getString(CONTENT_FOLDER, "content") + "/" + collectionHierarchy(CollectionTOCConstants.CONTENT_TYPE).toString.toLowerCase + "/toc"
      TelemetryManager.log("CollectionCSVManager:createFileAndStore -> Writing CSV to Cloud Folder: " + folder)
      val csvURL = ss.uploadFile(folder, csvFile)
      TelemetryManager.log("CollectionCSVManager:createFileAndStore -> csvURL: " + csvURL.mkString("Array(", ", ", ")"))

      csvURL(1)
    }
    catch {
      case ce: ClientException => throw ce
      case e: Exception =>
        TelemetryManager.log("Error writing data to file | Collection Id:" + collectionHierarchy(CollectionTOCConstants.IDENTIFIER).toString + " - Version Key: "
          + collectionHierarchy(CollectionTOCConstants.VERSION_KEY).toString + e)
        throw new ServerException("ERROR_PROCESSING_REQUEST", "Something went wrong while Processing Request")
    } finally {
      try {
        if (csvPrinter != null) csvPrinter.close()
        if (out != null) out.close()
        if (null != csvFile && csvFile.exists) deleteQuietly(csvFile.getCanonicalFile)
      } catch {
        case e: IOException =>
          TelemetryManager.log("Error writing data to file | Collection Id:" + collectionHierarchy(CollectionTOCConstants.IDENTIFIER) + " - Version Key: "
            + collectionHierarchy(CollectionTOCConstants.VERSION_KEY) + e)
      }
    }
  }

  def prepareNodeInfo(collectionUnitType: String, childrenHierarchy: List[Map[String, AnyRef]], nodesInfoMap: Map[String, AnyRef], parentDepthIndex: String): List[Map[String, AnyRef]] = {
    val nodesInfoListMet: List[Map[String, AnyRef]] = {
      childrenHierarchy.flatMap(record => {
        val linkedContents = {
          if (record.contains(CollectionTOCConstants.CHILDREN)) {
            record(CollectionTOCConstants.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]].map(childNode => {
              if (!childNode(CollectionTOCConstants.CONTENT_TYPE).toString.equalsIgnoreCase(collectionUnitType)) childNode(CollectionTOCConstants.IDENTIFIER).toString
              else ""
            }).filter(nodeId => nodeId.nonEmpty).asInstanceOf[Seq[String]]
          }
          else Seq.empty[String]
        }

        val nodeDepth = if (record.contains(CollectionTOCConstants.DEPTH)) record(CollectionTOCConstants.DEPTH).toString.toInt else 0
        val nodeIndex = if (record.contains(CollectionTOCConstants.INDEX)) record(CollectionTOCConstants.INDEX).toString.toInt else 0
        val nodeInfo = getNodeInfo(record, linkedContents, nodeDepth, nodeIndex)

        val appendedMap = {
          if(nodeDepth == 1) nodesInfoMap ++ Map(nodeDepth + "."+ (if(nodeIndex<10) "0"+nodeIndex else nodeIndex) -> nodeInfo)
          else nodesInfoMap ++ Map(parentDepthIndex + ":" + nodeDepth + "."+ (if(nodeIndex<10) "0"+nodeIndex else nodeIndex) -> nodeInfo)
        }

        val fetchedList = {
          if (record.contains(CollectionTOCConstants.CHILDREN))
            if(nodeDepth == 1)
              prepareNodeInfo(collectionUnitType, record(CollectionTOCConstants.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]], appendedMap, nodeDepth + "."+ (if(nodeIndex<10) "0"+nodeIndex else nodeIndex))
            else
              prepareNodeInfo(collectionUnitType, record(CollectionTOCConstants.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]], appendedMap, parentDepthIndex + ":"
                + nodeDepth + "."+ (if(nodeIndex<10) "0"+nodeIndex else nodeIndex))
          else List(appendedMap)
        }
        fetchedList
      })
    }
    nodesInfoListMet
  }

  private def getNodeInfo(record: Map[String, AnyRef], linkedContents: Seq[String], nodeDepth: Integer, nodeIndex: Integer): Map[String, AnyRef] = {
    val nodeId = record(CollectionTOCConstants.IDENTIFIER).toString
    val nodeName = record(CollectionTOCConstants.NAME).toString
    val nodeDescription = if (record.contains(CollectionTOCConstants.DESCRIPTION)) record(CollectionTOCConstants.DESCRIPTION).toString else ""
    val nodeKeywords = if (record.contains(CollectionTOCConstants.KEYWORDS)) record(CollectionTOCConstants.KEYWORDS).asInstanceOf[List[String]] else List.empty[String]
    val nodeTopics = if (record.contains(CollectionTOCConstants.TOPIC)) record(CollectionTOCConstants.TOPIC).asInstanceOf[List[String]] else List.empty[String]
    val nodeDialCodeRequired = if (record.contains(CollectionTOCConstants.DIAL_CODE_REQUIRED)) record(CollectionTOCConstants.DIAL_CODE_REQUIRED).toString else "No"
    val nodeDIALCode = if (record.contains(CollectionTOCConstants.DIAL_CODES)) record(CollectionTOCConstants.DIAL_CODES).asInstanceOf[List[String]].head else ""

    Map(CollectionTOCConstants.IDENTIFIER -> nodeId, CollectionTOCConstants.NAME -> nodeName, CollectionTOCConstants.DESCRIPTION -> nodeDescription,
      CollectionTOCConstants.KEYWORDS -> nodeKeywords, CollectionTOCConstants.TOPIC -> nodeTopics, CollectionTOCConstants.QR_CODE_REQUIRED -> nodeDialCodeRequired, CollectionTOCConstants.CONTENT_TYPE -> record(CollectionTOCConstants.CONTENT_TYPE).toString,
      CollectionTOCConstants.QR_CODE -> nodeDIALCode, CollectionTOCConstants.DEPTH -> nodeDepth, CollectionTOCConstants.INDEX -> nodeIndex, CollectionTOCConstants.LINKED_CONTENT -> linkedContents)
  }

  private def populateFolderInfoMap(folderInfoMap: mutable.Map[String, AnyRef], csvRecords: util.List[CSVRecord], mode: String): Unit = {
    csvRecords.map(csvRecord => {
      val csvRecordFolderHierarchyMap: Map[String, String] = csvRecord.toMap.asScala.toMap.filter(colData => {
        folderHierarchyHdrColumnsList.contains(colData._1) && colData._2.nonEmpty
      })

      val sortedFolderHierarchyMap = Map(csvRecordFolderHierarchyMap.toSeq.sortWith(_._1 < _._1):_*)
      val sortedFoldersDataKey = sortedFolderHierarchyMap.keys.toList
      val sortedFoldersDataList = sortedFolderHierarchyMap.values.scan("")(_+_).filter(x => x.nonEmpty).toList
      val finalSortedMap = (sortedFoldersDataKey zip sortedFoldersDataList).toMap
      val csvRecordMap = csvRecord.toMap.asScala.toMap

      sortedFolderHierarchyMap.map(folderData => {
        val folderDataHashCode = getCode(finalSortedMap(folderData._1))

        if(folderInfoMap.contains(folderDataHashCode) && ((sortedFoldersDataKey.indexOf(folderData._1)+1) != sortedFoldersDataList.size)) {
          val nodeInfoMap = folderInfoMap(folderDataHashCode).asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
          if(nodeInfoMap.contains(CollectionTOCConstants.CHILDREN))
          {
            var childrenSet = nodeInfoMap(CollectionTOCConstants.CHILDREN).asInstanceOf[Seq[String]]
            childrenSet ++= Seq(getCode(sortedFoldersDataList.get(sortedFoldersDataKey.indexOf(folderData._1)+1)))
            nodeInfoMap(CollectionTOCConstants.CHILDREN) = childrenSet
          }
          else {
            val childrenList = Seq(getCode(sortedFoldersDataList.get(sortedFoldersDataKey.indexOf(folderData._1)+1)))
            nodeInfoMap += (CollectionTOCConstants.CHILDREN -> childrenList)
          }
          folderInfoMap(folderDataHashCode) = nodeInfoMap
        }
        else {
          val nodeInfo = {
            if(folderData._1.equalsIgnoreCase(sortedFolderHierarchyMap.max._1)) {
              if(mode.equals(CollectionTOCConstants.UPDATE)) {
                val keywordsList = csvRecord.toMap.asScala.toMap.map(colData => {
                  if(CollectionTOCConstants.KEYWORDS.equalsIgnoreCase(colData._1) && colData._2.nonEmpty)
                    colData._2.trim.split(",").toList.filter(x => x.trim.nonEmpty)
                  else List.empty
                }).filter(msg => msg.nonEmpty).flatten.toList

                val mappedTopicsList = csvRecord.toMap.asScala.toMap.map(colData => {
                  if(mappedTopicsHeader.contains(colData._1) && colData._2.nonEmpty)
                    colData._2.trim.split(",").toList.map(x => x.trim)
                  else List.empty
                }).filter(msg => msg.nonEmpty).flatten.toList

                val dialCodeRequired = if(csvRecordMap(CollectionTOCConstants.QR_CODE_REQUIRED).nonEmpty && csvRecordMap(CollectionTOCConstants.QR_CODE_REQUIRED)
                  .equalsIgnoreCase(CollectionTOCConstants.YES)) CollectionTOCConstants.YES else CollectionTOCConstants.NO

                val dialCode = if(csvRecordMap(CollectionTOCConstants.QR_CODE).nonEmpty) csvRecordMap(CollectionTOCConstants.QR_CODE).trim else ""

                val csvLinkedContentsList: Seq[String] = csvRecord.toMap.asScala.toMap.map(colData => {
                  if(linkedContentHdrColumnsList.contains(colData._1) && colData._2.nonEmpty) colData._2.trim.toLowerCase() else ""
                }).filter(msg => msg.nonEmpty).toSeq

                scala.collection.mutable.Map(CollectionTOCConstants.IDENTIFIER -> csvRecordMap(collectionNodeIdentifierHeader.head), CollectionTOCConstants.NAME -> folderData._2,
                  CollectionTOCConstants.DESCRIPTION -> csvRecordMap("Description"), CollectionTOCConstants.KEYWORDS -> keywordsList, CollectionTOCConstants.TOPIC -> mappedTopicsList,
                  CollectionTOCConstants.DIAL_CODE_REQUIRED -> dialCodeRequired, CollectionTOCConstants.DIAL_CODES -> dialCode, CollectionTOCConstants.LINKED_CONTENT -> csvLinkedContentsList,
                  CollectionTOCConstants.LEVEL -> folderData._1)
              }
              else{
                scala.collection.mutable.Map(CollectionTOCConstants.NAME -> folderData._2, CollectionTOCConstants.DESCRIPTION -> csvRecordMap("Description"), CollectionTOCConstants.LEVEL -> folderData._1)
              }
            }
            else {
              val childrenList = {
                if((sortedFoldersDataKey.indexOf(folderData._1)+1) != sortedFoldersDataList.size)
                  Seq(getCode(sortedFoldersDataList.get(sortedFoldersDataKey.indexOf(folderData._1)+1)))
                else Seq.empty[String]
              }
              scala.collection.mutable.Map(CollectionTOCConstants.NAME -> folderData._2, CollectionTOCConstants.CHILDREN -> childrenList, CollectionTOCConstants.LEVEL -> folderData._1)
            }
          }

          folderInfoMap += (folderDataHashCode -> nodeInfo)
        }
      })
    })
  }

  private def getNodesMetadata(folderInfoMap: mutable.LinkedHashMap[String, AnyRef], mode: String, frameworkID: String, collectionType: String): String = {

    val collectionUnitType = contentTypeToUnitTypeMapping(collectionType)
    folderInfoMap.map(record => {
      val nodeInfo = record._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
      if(mode.equals(CollectionTOCConstants.CREATE))
        s""""${record._1}": {"isNew": true,"root": false, "metadata": {"mimeType": "application/vnd.ekstep.content-collection","contentType": "$collectionUnitType",
           |"name": "${nodeInfo("name").toString}", "description": "${if(nodeInfo.contains(CollectionTOCConstants.DESCRIPTION)) nodeInfo(CollectionTOCConstants.DESCRIPTION).toString else ""}",
           |"dialcodeRequired": "No","code": "nodeID","framework": "$frameworkID" }}""".stripMargin
      else
        try {
          s""""${nodeInfo(CollectionTOCConstants.IDENTIFIER).toString}": {"isNew": false,"root": false, "metadata": {"mimeType": "application/vnd.ekstep.content-collection",
             |"contentType": "$collectionUnitType","name": "${nodeInfo("name").toString}",
             |"description": "${if(nodeInfo.contains(CollectionTOCConstants.DESCRIPTION)) nodeInfo(CollectionTOCConstants.DESCRIPTION).toString else ""}",
             |"dialcodeRequired": "${nodeInfo(CollectionTOCConstants.DIAL_CODE_REQUIRED).toString}","dialcodes": "${nodeInfo(CollectionTOCConstants.DIAL_CODES).toString}",
             |"code": "${nodeInfo(CollectionTOCConstants.IDENTIFIER).toString}","framework": "$frameworkID",
             |"keywords": ${if(nodeInfo.contains(CollectionTOCConstants.KEYWORDS) && nodeInfo(CollectionTOCConstants.KEYWORDS).asInstanceOf[List[String]].nonEmpty)
            nodeInfo(CollectionTOCConstants.KEYWORDS).asInstanceOf[List[String]].mkString("[\"","\",\"","\"]") else "[]"},
             |"topic": ${if(nodeInfo.contains(CollectionTOCConstants.TOPIC) && nodeInfo(CollectionTOCConstants.TOPIC).asInstanceOf[List[String]].nonEmpty)
            nodeInfo(CollectionTOCConstants.TOPIC).asInstanceOf[List[String]].mkString("[\"","\",\"","\"]") else "[]"} }}""".stripMargin
        } catch {
          case _:Exception => throw new ClientException("CORRUPT_FOLDER_HIERARCHY", "Please verify the updated folder levels. Please ensure no new folder levels are added in the csv and upload again.")
        }
    }).mkString(",")


  }

  private def getHierarchyMetadata(folderInfoMap: mutable.LinkedHashMap[String, AnyRef], mode: String, linkedContentsDetails: List[Map[String, AnyRef]], collectionID: String, collectionName: String, collectionType: String): String = {
    val collectionUnitType = contentTypeToUnitTypeMapping(collectionType)

    val linkedContentsInfoMap: Map[String, Map[String, String]] = if(linkedContentsDetails.nonEmpty) {
      linkedContentsDetails.flatMap(linkedContentRecord => {
        Map(linkedContentRecord(CollectionTOCConstants.IDENTIFIER).toString ->
          Map(CollectionTOCConstants.IDENTIFIER -> linkedContentRecord(CollectionTOCConstants.IDENTIFIER).toString,
            CollectionTOCConstants.NAME -> linkedContentRecord(CollectionTOCConstants.NAME).toString,
            CollectionTOCConstants.CONTENT_TYPE -> linkedContentRecord(CollectionTOCConstants.CONTENT_TYPE).toString))
      }).toMap
    } else Map.empty[String, Map[String, String]]

    val collectionL1NodeList = folderInfoMap.map(nodeData => {
        if(nodeData._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]](CollectionTOCConstants.LEVEL)!=null &&
          nodeData._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]](CollectionTOCConstants.LEVEL).toString.equalsIgnoreCase
          (createCSVMandatoryHeaderCols.head)) {
          if (mode.equals(CollectionTOCConstants.UPDATE))
            nodeData._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]](CollectionTOCConstants.IDENTIFIER).toString
          else nodeData._1
        }
        else ""
      }).filter(node => node.nonEmpty).toList.distinct.mkString("[\"","\",\"","\"]")

    val hierarchyRootNode = s""""$collectionID": {"name":"$collectionName","collectionType":"$collectionType","root":true,"children":$collectionL1NodeList}"""

    val hierarchyChildNodesMetadata = folderInfoMap.map(record => {
      val nodeInfo = record._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
      if(mode.equals(CollectionTOCConstants.CREATE)) {
        s""""${record._1}": {"name": "${nodeInfo("name").toString}","root": false,"contentType": "$collectionUnitType", "children": ${if(nodeInfo.contains(CollectionTOCConstants.CHILDREN)) nodeInfo(CollectionTOCConstants.CHILDREN).asInstanceOf[Seq[String]].mkString("[\"","\",\"","\"]") else "[]"}}"""
      }
      else {
        val childrenFolders = {
          if(nodeInfo.contains(CollectionTOCConstants.CHILDREN) &&  nodeInfo(CollectionTOCConstants.CHILDREN).asInstanceOf[Seq[String]].nonEmpty
            && nodeInfo.contains(CollectionTOCConstants.LINKED_CONTENT) && nodeInfo(CollectionTOCConstants.LINKED_CONTENT).asInstanceOf[Seq[String]].nonEmpty) {
            val allChildrenSet = nodeInfo(CollectionTOCConstants.CHILDREN).asInstanceOf[Seq[String]] ++ nodeInfo(CollectionTOCConstants.LINKED_CONTENT).asInstanceOf[Seq[String]]
            allChildrenSet.map(childFolder => {
              if(folderInfoMap.contains(childFolder))
                folderInfoMap(childFolder).asInstanceOf[scala.collection.mutable.Map[String,AnyRef]](CollectionTOCConstants.IDENTIFIER).toString
              else childFolder
            }).mkString("[\"","\",\"","\"]")
          }
          else if(nodeInfo.contains(CollectionTOCConstants.CHILDREN) &&  nodeInfo(CollectionTOCConstants.CHILDREN).asInstanceOf[Seq[String]].nonEmpty)
            nodeInfo(CollectionTOCConstants.CHILDREN).asInstanceOf[Seq[String]].map(childFolder => {
              folderInfoMap(childFolder).asInstanceOf[scala.collection.mutable.Map[String,AnyRef]](CollectionTOCConstants.IDENTIFIER).toString
            }).mkString("[\"","\",\"","\"]")
          else if(nodeInfo.contains(CollectionTOCConstants.LINKED_CONTENT) && nodeInfo(CollectionTOCConstants.LINKED_CONTENT).asInstanceOf[Seq[String]].nonEmpty)
            nodeInfo(CollectionTOCConstants.LINKED_CONTENT).asInstanceOf[Seq[String]].mkString("[\"","\",\"","\"]")
          else "[]"
        }

        val folderNodeHierarchy = s""""${nodeInfo(CollectionTOCConstants.IDENTIFIER).toString}": {"name": "${nodeInfo("name").toString}","root": false,"contentType": "$collectionUnitType", "children": $childrenFolders}"""

        val contentsNode = if(nodeInfo.contains(CollectionTOCConstants.LINKED_CONTENT) && nodeInfo(CollectionTOCConstants.LINKED_CONTENT).asInstanceOf[Seq[String]].nonEmpty && linkedContentsInfoMap.nonEmpty)
          {
            val LinkedContentInfo = nodeInfo(CollectionTOCConstants.LINKED_CONTENT).asInstanceOf[Seq[String]].map(contentId => {
              val linkedContentDetails: Map[String, String] = linkedContentsInfoMap(contentId)
              s""""${linkedContentDetails(CollectionTOCConstants.IDENTIFIER)}": {"name": "${linkedContentDetails(CollectionTOCConstants.NAME)}","root": false,"contentType": "${linkedContentDetails(CollectionTOCConstants.CONTENT_TYPE)}", "children": []}"""
            }).mkString(",")
            LinkedContentInfo
          } else ""

        if(contentsNode.isEmpty) folderNodeHierarchy else folderNodeHierarchy + "," + contentsNode
      }
    }).mkString(",")

    hierarchyRootNode + "," + hierarchyChildNodesMetadata
  }

  private def getUpdateHierarchyRequest(nodesMetadata: String, hierarchyMetadata: String): Request = {
    val updateHierarchyRequest = new Request()

    val requestHashMap = new util.HashMap[String, AnyRef]
    requestHashMap.put(HierarchyConstants.NODES_MODIFIED, JsonUtils.deserialize("{"+nodesMetadata+"}", classOf[java.util.Map[String, AnyRef]]))
    requestHashMap.put(HierarchyConstants.HIERARCHY, JsonUtils.deserialize("{"+hierarchyMetadata+"}", classOf[java.util.Map[String, AnyRef]]))

    val requestContext = new util.HashMap[String, AnyRef]
    requestContext.put("graph_id", "domain")
    requestContext.put("schemaName", "collection")
    requestContext.put("version", "1.0")
    requestContext.put("objectType", "Collection")

    updateHierarchyRequest.setRequest(requestHashMap)
    updateHierarchyRequest.setObjectType("Collection")
    updateHierarchyRequest.setContext(requestContext)

    updateHierarchyRequest
  }

  private def linkDIALCodes(folderInfoMap: mutable.LinkedHashMap[String, AnyRef], channelID: String, collectionID: String)(implicit oec: OntologyEngineContext, ec: ExecutionContext): Unit = {
    //invoke DIAL code Linking
    val linkDIALCodeReqMap = folderInfoMap.map(record => {
      val nodeInfo = record._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
      if(nodeInfo(CollectionTOCConstants.DIAL_CODES) != null && nodeInfo(CollectionTOCConstants.DIAL_CODES).toString.nonEmpty)
        new util.HashMap[String, String]{put(CollectionTOCConstants.IDENTIFIER, nodeInfo(CollectionTOCConstants.IDENTIFIER).toString); put(CollectionTOCConstants.DIALCODE, nodeInfo(CollectionTOCConstants.DIAL_CODES).toString)}
      else  new util.HashMap[String, String]()
    }).filter(record => record.nonEmpty).toList

    if(linkDIALCodeReqMap.nonEmpty) linkDIALCode(channelID, collectionID, linkDIALCodeReqMap)
  }

}
