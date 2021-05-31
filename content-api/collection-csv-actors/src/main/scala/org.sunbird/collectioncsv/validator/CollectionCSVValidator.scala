package org.sunbird.collectioncsv.validator

import org.apache.commons.csv.CSVRecord
import org.sunbird.collectioncsv.util.CollectionTOCUtil.{getRelatedFrameworkById, searchLinkedContents, validateDialCodes}
import org.sunbird.collectioncsv.util.CollectionTOCConstants
import org.sunbird.common.Platform
import org.sunbird.common.exception.ClientException
import org.sunbird.telemetry.logger.TelemetryManager

import java.text.MessageFormat
import java.util
import scala.collection.JavaConversions._
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.immutable.{ListMap, Map}

object CollectionCSVValidator {

  val allowedContentTypes: List[String] = Platform.config.getStringList(CollectionTOCConstants.COLLECTION_TOC_ALLOWED_CONTENT_TYPES).toList
  val allowedNumberOfRecord: Integer = Platform.config.getInt(CollectionTOCConstants.COLLECTION_TOC_MAX_CSV_ROWS)
  val createCSVHeaders: Map[String, Integer] = Platform.config.getAnyRef(CollectionTOCConstants.COLLECTION_CREATION_CSV_TOC_HEADERS).asInstanceOf[util.HashMap[String, Integer]].asScala.toMap
  val updateCSVHeaders: Map[String, Integer] = Platform.config.getAnyRef(CollectionTOCConstants.COLLECTION_UPDATE_CSV_TOC_HEADERS).asInstanceOf[util.HashMap[String, Integer]].asScala.toMap
  val createCSVMandatoryHeaderCols: List[String] =  Platform.config.getStringList(CollectionTOCConstants.COLLECTION_TOC_CREATE_CSV_MANDATORY_FIELDS).toList
  val updateCSVMandatoryHeaderCols: List[String] = Platform.config.getStringList(CollectionTOCConstants.COLLECTION_TOC_UPDATE_CSV_MANDATORY_FIELDS).toList
  val qrCodeHdrColsList: List[String] = Platform.config.getStringList(CollectionTOCConstants.COLLECTION_CSV_QR_COLUMNS).toList
  val folderHierarchyHdrColumnsList: List[String] = Platform.config.getStringList(CollectionTOCConstants.FOLDER_HIERARCHY_COLUMNS).toList
  val linkedContentHdrColumnsList: List[String] = Platform.config.getStringList(CollectionTOCConstants.COLLECTION_CSV_LINKED_CONTENT_FIELDS).toList
  val linkedContentColumnHeadersSeq: Map[String, Integer] = Platform.config.getAnyRef(CollectionTOCConstants.COLLECTION_CSV_LINKED_CONTENT_SEQ).asInstanceOf[util.HashMap[String, Integer]].asScala.toMap
  val collectionNameHeader: List[String] = Platform.config.getStringList(CollectionTOCConstants.CSV_COLLECTION_NAME_HEADER).toList
  val mappedTopicsHeader: List[String] = Platform.config.getStringList(CollectionTOCConstants.MAPPED_TOPICS_HEADER).toList
  val collectionNodeIdentifierHeader: List[String] = Platform.config.getStringList(CollectionTOCConstants.COLLECTION_CSV_IDENTIFIER_HEADER).toList
  val contentTypeToUnitTypeMapping: Map[String, String] = Platform.config.getAnyRef(CollectionTOCConstants.COLLECTION_TYPE_TO_UNIT_TYPE).asInstanceOf[util.HashMap[String, String]].asScala.toMap
  val collectionOutputTocHeaders: List[String] = Platform.config.getStringList(CollectionTOCConstants.COLLECTION_OUTPUT_TOC_HEADERS).toList
  val maxFolderLevels: Int = Platform.config.getStringList(CollectionTOCConstants.FOLDER_HIERARCHY_COLUMNS).toList.size

  def validateCSVHeadersFormat(csvHeader: Map[String, Integer], mode:String) {

    val configHeaders: Map[String, Integer]  =  if(mode.equals(CollectionTOCConstants.CREATE)) createCSVHeaders else updateCSVHeaders

    if(!csvHeader.equals(configHeaders))
    {
      //Check if Column Order is different
      if((csvHeader.keySet -- configHeaders.keySet).isEmpty)
      {
        val colSeqString = ListMap(configHeaders.toSeq.sortBy(_._2):_*).keySet mkString ","
        val errorMessage = MessageFormat.format("Found invalid sequence of columns. Please follow the order: "+colSeqString)
        throw new ClientException("INVALID_HEADER_SEQUENCE", errorMessage)
      }

      //Check if Some columns are missing and any additional columns found
      if((configHeaders.toSet diff csvHeader.toSet).toMap.keySet.nonEmpty && (configHeaders.toSet diff csvHeader.toSet).toMap.keySet.toList.head.nonEmpty &&
        (((csvHeader.toSet diff configHeaders.toSet).toMap.keySet.nonEmpty && (csvHeader.toSet diff configHeaders.toSet).toMap.keySet.toList.head.nonEmpty) ||
          (csvHeader.keySet -- configHeaders.keySet).toList.head.nonEmpty))
      {
        val additionalCols = (csvHeader.toSet diff configHeaders.toSet).toMap.keySet mkString ","
        val missingCols = (configHeaders.toSet diff csvHeader.toSet).toMap.keySet mkString ","
        val errorMessage = MessageFormat.format("Following Columns are not found in the file: "+missingCols+" AND "
          + "Following additional Columns are found in the file: "+additionalCols)
        throw new ClientException("INVALID_HEADERS_FOUND", errorMessage)
      }
      //Check if Some columns are missing
      else if((configHeaders.toSet diff csvHeader.toSet).toMap.keySet.nonEmpty && (configHeaders.toSet diff csvHeader.toSet).toMap.keySet.toList.head.nonEmpty)
      {
        val missingCols = (configHeaders.toSet diff csvHeader.toSet).toMap.keySet mkString ","
        val errorMessage = MessageFormat.format("Following Columns are not found in the file: "+missingCols)
        throw new ClientException("REQUIRED_HEADER_MISSING", errorMessage)
      }
      //Check if any additional columns found
      else if((csvHeader.toSet diff configHeaders.toSet).toMap.keySet.nonEmpty && (csvHeader.toSet diff configHeaders.toSet).toMap.keySet.toList.head.nonEmpty)
      {
        val additionalCols:String = (csvHeader.toSet diff configHeaders.toSet).toMap.keySet mkString ","
        val errorMessage = MessageFormat.format("Following additional Columns are found in the file: "+additionalCols)
        throw new ClientException("ADDITIONAL_HEADER_FOUND", errorMessage)
      }
    }

  }

  def validateCSVRecordsDataFormat(csvRecords: util.List[CSVRecord], mode: String) {
    //Check if CSV Records are empty
    if (null == csvRecords || csvRecords.isEmpty)
      throw new ClientException("BLANK_CSV_DATA", "Did not find any Table of Contents data. Please check and upload again.")

    // check if records are more than allowed csv rows
    if (csvRecords.nonEmpty && csvRecords.size > allowedNumberOfRecord)
      throw new ClientException("CSV_ROWS_EXCEEDS", "Number of rows in csv file is more than " + allowedNumberOfRecord)

    // check if record length is greater than max length - START
    val recordLengthErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(colData._1.isEmpty && colData._2.nonEmpty)
          MessageFormat.format("Row {0}", (csvRecord.getRecordNumber + 1).toString)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if(recordLengthErrorMessage.nonEmpty && recordLengthErrorMessage.trim.nonEmpty)
      throw new ClientException("CSV_RECORD_EXCEEDS_MAX_LENGTH", "Following rows have data exceeding maximum columns allowed: " + recordLengthErrorMessage)
    // check if record length is greater than max length - END

    // Check if data exists in mandatory columns - START
    val mandatoryDataHdrCols =  if(mode.equals(CollectionTOCConstants.CREATE)) createCSVMandatoryHeaderCols else updateCSVMandatoryHeaderCols

    val mandatoryMissingDataList = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(mandatoryDataHdrCols.contains(colData._1) && colData._2.isEmpty)
          MessageFormat.format("Row {0} - column: {1}", (csvRecord.getRecordNumber+1).toString,colData._1)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(",")
    // Check if data exists in mandatory columns - END

    // Check if data exists in hierarchy folder columns - START
    val hierarchyHeaders: Map[String, Integer]  = if(mode.equals(CollectionTOCConstants.CREATE)) createCSVHeaders else updateCSVHeaders

    val missingDataList = csvRecords.flatMap(csvRecord => {
      val csvRecordFolderHierarchyData = csvRecord.toMap.asScala.toMap.filter(colData => {
        folderHierarchyHdrColumnsList.contains(colData._1) && colData._2.nonEmpty
      })
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(folderHierarchyHdrColumnsList.contains(colData._1) && colData._2.isEmpty &&
          (csvRecordFolderHierarchyData.nonEmpty && hierarchyHeaders(colData._1) < hierarchyHeaders(csvRecordFolderHierarchyData.max._1)))
          MessageFormat.format("Row {0} - column: {1}", (csvRecord.getRecordNumber+1).toString,colData._1)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(",")
    // Check if data exists in hierarchy folder columns - END

    // Add column data validation messages from mandatory columns and hierarchy folder - START
    val missingDataErrorMessage = {
      if (mandatoryMissingDataList.trim.nonEmpty && missingDataList.trim.nonEmpty)
        mandatoryMissingDataList.trim + "," + missingDataList.trim
      else if (mandatoryMissingDataList.trim.nonEmpty) mandatoryMissingDataList.trim
      else if (missingDataList.trim.nonEmpty) missingDataList.trim
      else ""
    }

    if(missingDataErrorMessage.trim.nonEmpty)
      throw new ClientException("REQUIRED_FIELD_MISSING", "Following Rows have missing values: "
        + missingDataErrorMessage.split(",").distinct.mkString(CollectionTOCConstants.COMMA_SEPARATOR))
    // Add column data validation messages from mandatory columns and hierarchy folder - END

    // Verify if there are any duplicate hierarchy folder structure - START
    val dupRecordsList = csvRecords.filter(csvRecord => {
      csvRecords.exists(record => {
        val csvRecordFolderHierarchy = csvRecord.toMap.asScala.toMap.map(colData => {
          if(folderHierarchyHdrColumnsList.contains(colData._1))
            colData
        })
        val recordFolderHierarchy = record.toMap.asScala.toMap.map(colData => {
          if(folderHierarchyHdrColumnsList.contains(colData._1))
            colData
        })
        recordFolderHierarchy.equals(csvRecordFolderHierarchy) && !csvRecord.getRecordNumber.equals(record.getRecordNumber)
      })
    }).map(dupRecord => {
      MessageFormat.format("Row {0}", (dupRecord.getRecordNumber+1).toString)
    }).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if(dupRecordsList.trim.nonEmpty)
      throw new ClientException("DUPLICATE_ROWS", "Following Rows are duplicate: " + dupRecordsList)
    // Verify if there are any duplicate hierarchy folder structure - END


    if(mode.equals(CollectionTOCConstants.UPDATE)) {
      // QRCode data format validations - START
      // Verify if there are any QR Codes data entry issues - START
      val qrDataErrorMessage = csvRecords.map(csvRecord => {
        val csvRecordMap = csvRecord.toMap.asScala.toMap
        if(csvRecordMap(qrCodeHdrColsList.head).equalsIgnoreCase(CollectionTOCConstants.YES) && csvRecordMap(qrCodeHdrColsList(1)).isEmpty)
          MessageFormat.format("Row {0} has column 'QR Code Required?' as 'Yes' but 'QR Code' is Blank", (csvRecord.getRecordNumber+1).toString)
        else if((csvRecordMap(qrCodeHdrColsList.head).equalsIgnoreCase(CollectionTOCConstants.NO) || csvRecordMap(qrCodeHdrColsList.head).isEmpty) &&
          csvRecordMap(qrCodeHdrColsList(1)).nonEmpty)
          MessageFormat.format("Row {0} has column 'QR Code Required?' as 'No'/Blank but 'QR Code' is Filled", (csvRecord.getRecordNumber+1).toString)
        else
          ""
      }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if(qrDataErrorMessage.trim.nonEmpty)
        throw new ClientException("ERROR_QR_CODE_ENTRY", "Following rows have issues for QR Code entries: " + qrDataErrorMessage)
      // Verify if there are any QR Codes data entry issues - END

      // Verify if there are any duplicate QR Codes - START
      val dupQRListMsg = csvRecords.filter(csvRecord => {
        csvRecords.exists(record => {
          record.get(CollectionTOCConstants.QR_CODE).nonEmpty && csvRecord.get(CollectionTOCConstants.QR_CODE).nonEmpty && record.get(CollectionTOCConstants.QR_CODE).equals(csvRecord.get(CollectionTOCConstants.QR_CODE)) &&
            !csvRecord.getRecordNumber.equals(record.getRecordNumber)
        })
      }).map(dupQRRecord => {
        MessageFormat.format("Row {0} - {1}", (dupQRRecord.getRecordNumber+1).toString, dupQRRecord.get(CollectionTOCConstants.QR_CODE))
      }).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if(dupQRListMsg.trim.nonEmpty)
        throw new ClientException("DUPLICATE_QR_CODE_ENTRY", "Following rows have duplicate QR Code entries: " + dupQRListMsg)
      // Verify if there are any duplicate QR Codes - END
      // QRCode data format validations - END

      // Check if data exists in Linked content columns - START
      val missingLinkedContentDataList = csvRecords.flatMap(csvRecord => {
        val csvRecordLinkedContentsData = csvRecord.toMap.asScala.toMap.filter(colData => {
          linkedContentHdrColumnsList.contains(colData._1) && colData._2.nonEmpty
        })

        csvRecord.toMap.asScala.toMap.map(colData => {
          if(linkedContentHdrColumnsList.contains(colData._1) && colData._2.isEmpty &&
            (csvRecordLinkedContentsData.nonEmpty && linkedContentColumnHeadersSeq(colData._1) < linkedContentColumnHeadersSeq(csvRecordLinkedContentsData.max._1)))
            MessageFormat.format("Row {0} - column: {1}", (csvRecord.getRecordNumber+1).toString,colData._1)
          else ""
        })
      }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if(missingLinkedContentDataList.trim.nonEmpty)
        throw new ClientException("LINKED_CONTENTS_DATA_MISSING", "Following rows have linked contents data missing: " + missingLinkedContentDataList)
      // Check if data exists in hierarchy folder columns - END
    }

  }

  def validateCSVRecordsDataAuthenticity(csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef]): List[Map[String, AnyRef]] = {
    // validate collection name column in CSV - START
    val invalidCollectionNameErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNameHeader.contains(colData._1) && (colData._2.isEmpty || !colData._2.equalsIgnoreCase(collectionHierarchy(CollectionTOCConstants.NAME).toString)))
          MessageFormat.format("Row {0}", (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if (invalidCollectionNameErrorMessage.trim.nonEmpty)
      throw new ClientException("CSV_INVALID_COLLECTION_NAME", "Following rows have invalid Collection Name: " + invalidCollectionNameErrorMessage)
    // validate collection name column in CSV - END
    println("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating collection name column in CSV")

    // validate Folder Identifier column in CSV - START
    val collectionChildNodes = collectionHierarchy(CollectionTOCConstants.CHILD_NODES).asInstanceOf[List[String]]

    val invalidCollectionNodeIDErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNodeIdentifierHeader.contains(colData._1) && (colData._2.isEmpty || !collectionChildNodes.contains(colData._2)))
          MessageFormat.format("Row {0}", (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if (invalidCollectionNameErrorMessage.trim.nonEmpty)
      throw new ClientException("CSV_INVALID_COLLECTION_NODE_ID", "Following rows have invalid folder identifier: " + invalidCollectionNodeIDErrorMessage)
    // validate Folder Identifier column in CSV - END
    println("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating Folder Identifier column in CSV")

    // Validate QR Codes with reserved DIAL codes - START
    val csvQRCodesList: List[String] = csvRecords.map(csvRecord => {
      csvRecord.toMap.asScala.toMap.get(qrCodeHdrColsList(1)).get.trim
    }).filter(msg => msg.nonEmpty).toList

    if(csvQRCodesList.nonEmpty) {
      val returnDIALCodes = validateDialCodes(collectionHierarchy(CollectionTOCConstants.CHANNEL).toString, csvQRCodesList)

      val invalidQRCodeErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (qrCodeHdrColsList.contains(colData._1) && (csvQRCodesList diff returnDIALCodes).contains(colData._2))
            MessageFormat.format("Row {0}", (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
          else ""
        })
      }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if (invalidQRCodeErrorMessage.trim.nonEmpty)
        throw new ClientException("CSV_INVALID_DIAL_CODES", "Following rows have invalid DIAL codes: " + invalidQRCodeErrorMessage)
    }
    // Validate QR Codes with reserved DIAL codes - END
    println("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating QR Codes with reserved DIAL codes")

    // Validate Mapped Topics with Collection Framework data - START
    val mappedTopicsList = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (mappedTopicsHeader.contains(colData._1) && colData._2.nonEmpty) colData._2.trim.split(",").mkString(",") else ""
      })
    }).filter(msg => msg.nonEmpty).toList

    if(mappedTopicsList.nonEmpty) {
      val frameworkId = collectionHierarchy(CollectionTOCConstants.FRAMEWORK).toString
      val frameworkGetResponse = getRelatedFrameworkById(frameworkId)
      val frameworkGetResult = frameworkGetResponse.getResult.getOrDefault(CollectionTOCConstants.FRAMEWORK, new util.HashMap[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
      val frameworkCategories = frameworkGetResult.getOrDefault(CollectionTOCConstants.CATEGORIES, List.empty).asInstanceOf[List[Map[String, AnyRef]]]

      val frameworkTopicList = frameworkCategories.flatMap(categoryData => {
        categoryData.map(colData => {
          if (categoryData(CollectionTOCConstants.CODE).equals(CollectionTOCConstants.TOPIC) && colData._1.equalsIgnoreCase(CollectionTOCConstants.TERMS))
            colData._2.asInstanceOf[List[Map[String, AnyRef]]].map(_.getOrElse(CollectionTOCConstants.NAME, "")).asInstanceOf[List[String]]
          else  List.empty
        })
      }).filter(topic => topic.nonEmpty).flatten

      val invalidTopicsErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (mappedTopicsHeader.contains(colData._1) && colData._2.nonEmpty) {
            val topicsDataList: List[String] = colData._2.split(",").toList
            topicsDataList.map(topic => {
              if(!frameworkTopicList.contains(topic.trim))
                MessageFormat.format("Row {0}", (csvRecord.getRecordNumber + 1).toString + " - " + topic)
              else ""
            }).filter(errmsg => errmsg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)
          } else ""
        })
      }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if (invalidTopicsErrorMessage.trim.nonEmpty)
        throw new ClientException("CSV_INVALID_MAPPED_TOPICS", "Following rows have invalid Mapped Topics: " + invalidTopicsErrorMessage)
    }
    // Validate Mapped Topics with Collection Framework data - END
    println("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating Mapped Topics with Collection Framework data")

    // Validate Linked Contents authenticity - START

    val csvLinkedContentsList: List[String] = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (linkedContentHdrColumnsList.contains(colData._1) && colData._2.nonEmpty) colData._2.trim  else ""
      })
    }).filter(msg => msg.nonEmpty).toList

    if (csvLinkedContentsList.nonEmpty) {
      val returnedLinkedContentsResult: List[Map[String, AnyRef]] = searchLinkedContents(csvLinkedContentsList)
      val returnedLinkedContentsIdentifierList = returnedLinkedContentsResult.map(_.getOrElse(CollectionTOCConstants.IDENTIFIER, "")).asInstanceOf[List[String]]

      val invalidLinkedContentsErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (linkedContentHdrColumnsList.contains(colData._1) && (csvLinkedContentsList diff returnedLinkedContentsIdentifierList).contains(colData._2))
            MessageFormat.format("Row {0}", (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
          else
            ""
        })
      }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if (invalidLinkedContentsErrorMessage.trim.nonEmpty)
        throw new ClientException("CSV_INVALID_LINKED_CONTENTS", "Following rows have invalid contents linked: " + invalidLinkedContentsErrorMessage)

      val returnedLinkedContentsContentTypeList = returnedLinkedContentsResult.map(_.getOrElse(CollectionTOCConstants.CONTENT_TYPE, "")).asInstanceOf[List[String]]

      if(returnedLinkedContentsContentTypeList.exists(contentType => !allowedContentTypes.contains(contentType)))
      {
        val invalidContentTypeLinkedContentsList = returnedLinkedContentsResult.map(content => {
          if(!allowedContentTypes.contains(content(CollectionTOCConstants.CONTENT_TYPE).toString)) content(CollectionTOCConstants.IDENTIFIER).toString  else ""
        }).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

        if(invalidContentTypeLinkedContentsList.trim.nonEmpty)
          throw new ClientException("CSV_INVALID_LINKED_CONTENTS_CONTENT_TYPE", "Following contents are not allowed due to invalid content types: "
            + invalidContentTypeLinkedContentsList)
      }
      println("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating Linked Contents")
      returnedLinkedContentsResult
    }
    else List.empty[Map[String, AnyRef]]
    // Validate Linked Contents authenticity - END

  }

}
