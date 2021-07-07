package org.sunbird.collectioncsv.validator

import org.apache.commons.csv.{CSVFormat, CSVRecord}
import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.input.BOMInputStream
import org.apache.commons.lang3.StringUtils
import org.sunbird.collectioncsv.util.CollectionTOCUtil.{getFrameworkTopics, searchLinkedContents, validateDialCodes}
import org.sunbird.collectioncsv.util.CollectionTOCConstants
import org.sunbird.common.Platform
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.telemetry.logger.TelemetryManager

import java.io.{File, FileInputStream, IOException, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.util
import scala.collection.JavaConversions._
import scala.collection.JavaConverters.{asScalaBufferConverter, mapAsJavaMapConverter, mapAsScalaMapConverter}
import scala.collection.immutable.{HashMap, ListMap, Map}
import scala.concurrent.ExecutionContext

object CollectionCSVValidator {

  val allowedContentTypes: List[String] = Platform.getStringList(CollectionTOCConstants.COLLECTION_TOC_ALLOWED_CONTENT_TYPES, java.util.Arrays.asList("TextBook","Collection","LessonPlan","Resource")).toList
  val allowedNumberOfRecord: Integer = Platform.getInteger(CollectionTOCConstants.COLLECTION_TOC_MAX_CSV_ROWS,6500)
  val createCSVHeaders: Map[String, Integer] = Platform.getAnyRef(CollectionTOCConstants.COLLECTION_CREATION_CSV_TOC_HEADERS, Map[String, Integer]("Level 1 Folder"->0,"Level 2 Folder"->1,"Level 3 Folder"->2,"Level 4 Folder"->3,"Description"->4).asJava).asInstanceOf[util.Map[String, Integer]].asScala.toMap
  val updateCSVHeaders: Map[String, Integer] = Platform.getAnyRef(CollectionTOCConstants.COLLECTION_UPDATE_CSV_TOC_HEADERS, Map[String, Integer]("Collection Name"->0,"Folder Identifier"->1,"Level 1 Folder"->2,"Level 2 Folder"->3,"Level 3 Folder"->4,"Level 4 Folder"->5,"Description"->6,"Mapped Topics"->7,"Keywords"->8,"QR Code Required?"->9,"QR Code"->10,"Linked Content 1"->11,"Linked Content 2"->12,"Linked Content 3"->13,"Linked Content 4"->14,"Linked Content 5"->15,"Linked Content 6"->16,"Linked Content 7"->17,"Linked Content 8"->18,"Linked Content 9"->19,"Linked Content 10"->20,"Linked Content 11"->21,"Linked Content 12"->22,"Linked Content 13"->23,"Linked Content 14"->24,"Linked Content 15"->25,"Linked Content 16"->26,"Linked Content 17"->27,"Linked Content 18"->28,"Linked Content 19"->29,"Linked Content 20"->30,"Linked Content 21"->31,"Linked Content 22"->32,"Linked Content 23"->33,"Linked Content 24"->34,"Linked Content 25"->35,"Linked Content 26"->36,"Linked Content 27"->37,"Linked Content 28"->38,"Linked Content 29"->39,"Linked Content 30"->40).asJava).asInstanceOf[util.Map[String, Integer]].asScala.toMap
  val createCSVMandatoryHeaderCols: List[String] =  Platform.getStringList(CollectionTOCConstants.COLLECTION_TOC_CREATE_CSV_MANDATORY_FIELDS, java.util.Arrays.asList("Level 1 Folder")).toList
  val updateCSVMandatoryHeaderCols: List[String] = Platform.getStringList(CollectionTOCConstants.COLLECTION_TOC_UPDATE_CSV_MANDATORY_FIELDS, java.util.Arrays.asList("Collection Name","Folder Identifier")).toList
  val qrCodeHdrColsList: List[String] = Platform.getStringList(CollectionTOCConstants.COLLECTION_CSV_QR_COLUMNS, java.util.Arrays.asList("QR Code Required?","QR Code")).toList
  val folderHierarchyHdrColumnsList: List[String] = Platform.getStringList(CollectionTOCConstants.FOLDER_HIERARCHY_COLUMNS, java.util.Arrays.asList("Level 1 Folder","Level 2 Folder","Level 3 Folder","Level 4 Folder")).toList
  val linkedContentHdrColumnsList: List[String] = Platform.getStringList(CollectionTOCConstants.COLLECTION_CSV_LINKED_CONTENT_FIELDS, java.util.Arrays.asList("Linked Content 1","Linked Content 2","Linked Content 3","Linked Content 4","Linked Content 5","Linked Content 6","Linked Content 7","Linked Content 8","Linked Content 9","Linked Content 10","Linked Content 11","Linked Content 12","Linked Content 13","Linked Content 14","Linked Content 15","Linked Content 16","Linked Content 17","Linked Content 18","Linked Content 19","Linked Content 20","Linked Content 21","Linked Content 22","Linked Content 23","Linked Content 24","Linked Content 25","Linked Content 26","Linked Content 27","Linked Content 28","Linked Content 29","Linked Content 30")).toList
  val linkedContentColumnHeadersSeq: Map[String, Integer] = Platform.getAnyRef(CollectionTOCConstants.COLLECTION_CSV_LINKED_CONTENT_SEQ, Map[String, Integer]("Linked Content 1"->0,"Linked Content 2"->1,"Linked Content 3"->2,"Linked Content 4"->3,"Linked Content 5"->4,"Linked Content 6"->5,"Linked Content 7"->6,"Linked Content 8"->7,"Linked Content 9"->8,"Linked Content 10"->9,"Linked Content 11"->10,"Linked Content 12"->11,"Linked Content 13"->12,"Linked Content 14"->13,"Linked Content 15"->14,"Linked Content 16"->15,"Linked Content 17"->16,"Linked Content 18"->17,"Linked Content 19"->18,"Linked Content 20"->19,"Linked Content 21"->20,"Linked Content 22"->21,"Linked Content 23"->22,"Linked Content 24"->23,"Linked Content 25"->24,"Linked Content 26"->25,"Linked Content 27"->26,"Linked Content 28"->27,"Linked Content 29"->28,"Linked Content 30"->29).asJava).asInstanceOf[util.Map[String, Integer]].asScala.toMap
  val collectionNameHeader: List[String] = Platform.getStringList(CollectionTOCConstants.CSV_COLLECTION_NAME_HEADER, java.util.Arrays.asList("Collection Name")).toList
  val mappedTopicsHeader: List[String] = Platform.getStringList(CollectionTOCConstants.MAPPED_TOPICS_HEADER, java.util.Arrays.asList("Mapped Topics")).toList
  val collectionNodeIdentifierHeader: List[String] = Platform.getStringList(CollectionTOCConstants.COLLECTION_CSV_IDENTIFIER_HEADER, java.util.Arrays.asList("Folder Identifier")).toList
  val contentTypeToUnitTypeMapping: Map[String, String] = Platform.getAnyRef(CollectionTOCConstants.COLLECTION_TYPE_TO_UNIT_TYPE, Map[String, String]("TextBook"-> "TextBookUnit", "Course"-> "CourseUnit", "Collection"->"CollectionUnit").asJava).asInstanceOf[util.Map[String, String]].asScala.toMap
  val collectionOutputTocHeaders: List[String] = Platform.getStringList(CollectionTOCConstants.COLLECTION_OUTPUT_TOC_HEADERS, java.util.Arrays.asList("Collection Name","Folder Identifier","Level 1 Folder","Level 2 Folder","Level 3 Folder","Level 4 Folder","Description","Mapped Topics","Keywords","QR Code Required?","QR Code","Linked Content 1","Linked Content 2","Linked Content 3","Linked Content 4","Linked Content 5","Linked Content 6","Linked Content 7","Linked Content 8","Linked Content 9","Linked Content 10","Linked Content 11","Linked Content 12","Linked Content 13","Linked Content 14","Linked Content 15","Linked Content 16","Linked Content 17","Linked Content 18","Linked Content 19","Linked Content 20","Linked Content 21","Linked Content 22","Linked Content 23","Linked Content 24","Linked Content 25","Linked Content 26","Linked Content 27","Linked Content 28","Linked Content 29","Linked Content 30")).toList
  val maxFolderLevels: Int = folderHierarchyHdrColumnsList.size

  def readInputCSV(request: Request): (String, util.List[CSVRecord], String) = {
    val file = request.getRequest.get("file").asInstanceOf[File]
    val extension = "."+file.getAbsolutePath.split("\\.").last.toLowerCase
    TelemetryManager.log("CollectionCSVManager --> readInputCSV --> file: " + file.getAbsolutePath)

    // Reading input CSV File - START
    val bomInputStream = new BOMInputStream(new FileInputStream(file), ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE)
    val character =  if (bomInputStream.hasBOM)  bomInputStream.getBOMCharsetName else StandardCharsets.UTF_8.name
    try {
      val csvFileParser = CSVFormat.DEFAULT.withHeader().parse(new InputStreamReader(bomInputStream, character))

      try {
        val csvHeaders: Map[String, Integer] =  if (!csvFileParser.getHeaderMap.isEmpty) csvFileParser.getHeaderMap.asScala.toMap else HashMap.empty
        // Reading input CSV File - END
        val csvRecords = csvFileParser.getRecords

        //Check if CSV Headers are empty
        if (null == csvHeaders || csvHeaders.isEmpty) throw new ClientException("BLANK_CSV_DATA", "Did not find any Table of Contents data. Please check and upload again.")

        //Check if the input CSV is 'CREATE' TOC file format or 'UPDATE' TOC file format
        val mode = if (csvHeaders.containsKey(collectionNodeIdentifierHeader.head)) CollectionTOCConstants.UPDATE else CollectionTOCConstants.CREATE
        TelemetryManager.log("CollectionCSVActor --> uploadTOC --> mode identified: " + mode)

        //Validate the headers format of the input CSV
        validateCSVHeadersFormat(csvHeaders, mode)
        TelemetryManager.log("CollectionCSVActor --> uploadTOC --> after validating CSV Headers format: ")

        (extension, csvRecords, mode)
      } catch {
        case ce: ClientException => throw ce
        case ex: Exception => throw new ClientException("CLIENT_ERROR", ex.getMessage)
      } finally {
        try if (null != csvFileParser) csvFileParser.close()
        catch {
          case e: IOException => TelemetryManager.log("CollectionCSVActor:readAndValidateCSV : Exception occurred while closing stream" + e)
        }
      }
    }
    catch {
      case ce: ClientException => throw ce
      case ex: Exception =>  throw new ClientException("INVALID_CSV_FILE", "Please provide valid csv file. Please check for data columns without headers.")
    }
  }

  def validateCSVHeadersFormat(csvHeader: Map[String, Integer], mode:String) {
    val configHeaders: Map[String, Integer]  =  if(mode.equals(CollectionTOCConstants.CREATE)) createCSVHeaders else updateCSVHeaders

    if(!csvHeader.equals(configHeaders))
    {
      //Check if Column Order is different
      validateHeaderOrder(csvHeader, configHeaders)
      //Check if Some columns are missing and any additional columns found
      validateDifferentialHeaders(csvHeader, configHeaders)
    }
  }

  def validateCSVRecordsDataFormat(csvRecords: util.List[CSVRecord], mode: String) {
    //Check if CSV Records are empty
    if (null == csvRecords || csvRecords.isEmpty)
      throw new ClientException("BLANK_CSV_DATA", "Did not find any Table of Contents data. Please check and upload again.")

    // check if records are more than allowed csv rows
    if (csvRecords.nonEmpty && csvRecords.size > allowedNumberOfRecord)
      throw new ClientException("CSV_ROWS_EXCEEDS", "Number of rows in csv file is more than " + allowedNumberOfRecord)

    validateMandatoryHeaderCols(csvRecords, mode)
    validateDuplicateRows(csvRecords)

    if(mode.equals(CollectionTOCConstants.UPDATE)) {
      // QRCode data format validations
        validateQRCodeColumns(csvRecords)

      // validate linked contents missing entries
        validateLinkedContentsColumns(csvRecords)
    }
  }

  def validateCSVRecordsDataAuthenticity(csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): List[Map[String, AnyRef]] = {
    validateCollectionName(csvRecords, collectionHierarchy)

    validateUnitIdentifiers(csvRecords, collectionHierarchy)

    validateQRCodes(csvRecords, collectionHierarchy)

    validateMappedTopics(csvRecords, collectionHierarchy)

    validateLinkedContents(csvRecords, collectionHierarchy)
  }

  private def validateHeaderOrder(csvHeader: Map[String, Integer], configHeaders: Map[String, Integer]): Unit = {
    if((csvHeader.keySet -- configHeaders.keySet).isEmpty)
    {
      val colSeqString = ListMap(configHeaders.toSeq.sortBy(_._2):_*).keySet mkString ","
      val errorMessage = MessageFormat.format("Found invalid sequence of columns. Please follow the order: "+colSeqString)
      throw new ClientException("INVALID_HEADER_SEQUENCE", errorMessage)
    }
  }

  private def validateDifferentialHeaders(csvHeader: Map[String, Integer], configHeaders: Map[String, Integer]): Unit = {
    if((configHeaders.toSet diff csvHeader.toSet).toMap.keySet.nonEmpty && (configHeaders.toSet diff csvHeader.toSet).toMap.keySet.toList.head.nonEmpty &&
      (((csvHeader.toSet diff configHeaders.toSet).toMap.keySet.nonEmpty && (csvHeader.toSet diff configHeaders.toSet).toMap.keySet.toList.head.nonEmpty) ||
        (csvHeader.keySet -- configHeaders.keySet).toList.head.nonEmpty))
    {
      val additionalCols = (csvHeader.toSet diff configHeaders.toSet).toMap.keySet mkString ","
      val missingCols = (configHeaders.toSet diff csvHeader.toSet).toMap.keySet mkString ","
      throw new ClientException("INVALID_HEADERS_FOUND", MessageFormat.format("Following Columns are not found in the file: "+missingCols+" AND "
        + "Following additional Columns are found in the file: "+additionalCols))
    }
    //Check if Some columns are missing
    else if((configHeaders.toSet diff csvHeader.toSet).toMap.keySet.nonEmpty && (configHeaders.toSet diff csvHeader.toSet).toMap.keySet.toList.head.nonEmpty)
    {
      val missingCols = (configHeaders.toSet diff csvHeader.toSet).toMap.keySet mkString ","
      throw new ClientException("REQUIRED_HEADER_MISSING", MessageFormat.format("Following Columns are not found in the file: "+missingCols))
    }
    //Check if any additional columns found
    else if((csvHeader.toSet diff configHeaders.toSet).toMap.keySet.nonEmpty && (csvHeader.toSet diff configHeaders.toSet).toMap.keySet.toList.head.nonEmpty)
    {
      val additionalCols = (csvHeader.toSet diff configHeaders.toSet).toMap.keySet mkString ","
      throw new ClientException("ADDITIONAL_HEADER_FOUND", MessageFormat.format("Following additional Columns are found in the file: "+additionalCols))
    }
  }

  private def validateMandatoryHeaderCols(csvRecords: util.List[CSVRecord], mode: String): Unit = {
    // Check if data exists in mandatory columns - START
    val mandatoryDataHdrCols =  if(mode.equals(CollectionTOCConstants.CREATE)) createCSVMandatoryHeaderCols else updateCSVMandatoryHeaderCols

    val mandatoryMissingDataList = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(mandatoryDataHdrCols.contains(colData._1) && colData._2.trim.isEmpty)
          MessageFormat.format("Row {0} - column: {1}", (csvRecord.getRecordNumber+1).toString,colData._1)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(",")
    // Check if data exists in mandatory columns - END

    // Check if data exists in hierarchy folder columns - START
    val hierarchyHeaders: Map[String, Integer]  = if(mode.equals(CollectionTOCConstants.CREATE)) createCSVHeaders else updateCSVHeaders

    val missingDataList = csvRecords.flatMap(csvRecord => {
      val csvRecordFolderHierarchyData = csvRecord.toMap.asScala.toMap.filter(colData => {
        folderHierarchyHdrColumnsList.contains(colData._1) && colData._2.trim.nonEmpty
      })
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(folderHierarchyHdrColumnsList.contains(colData._1) && colData._2.trim.isEmpty &&
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
  }

  private def validateDuplicateRows(csvRecords: util.List[CSVRecord]): Unit = {
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
  }

  private def validateQRCodeColumns(csvRecords: util.List[CSVRecord]): Unit = {
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
  }

  private def validateLinkedContentsColumns(csvRecords: util.List[CSVRecord]): Unit = {
    // Check if data exists in Linked content columns - START
    val missingLinkedContentDataList = csvRecords.flatMap(csvRecord => {
      val csvRecordLinkedContentsData = csvRecord.toMap.asScala.toMap.filter(colData => {
        linkedContentHdrColumnsList.contains(colData._1) && colData._2.nonEmpty
      })

      csvRecord.toMap.asScala.toMap.map(colData => {
        if(linkedContentHdrColumnsList.contains(colData._1) && colData._2.trim.isEmpty &&
          (csvRecordLinkedContentsData.nonEmpty && linkedContentColumnHeadersSeq(colData._1) < linkedContentColumnHeadersSeq(csvRecordLinkedContentsData.max._1)))
          MessageFormat.format("Row {0} - column: {1}", (csvRecord.getRecordNumber+1).toString,colData._1)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if(missingLinkedContentDataList.trim.nonEmpty)
      throw new ClientException("LINKED_CONTENTS_DATA_MISSING", "Following rows have linked contents data missing: " + missingLinkedContentDataList)
    // Check if data exists in Linked content columns - END
  }

  private def validateCollectionName(csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef]): Unit = {
    // validate collection name column in CSV - START
    val invalidCollectionNameErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNameHeader.contains(colData._1) && (colData._2.trim.isEmpty || !colData._2.trim.equalsIgnoreCase(collectionHierarchy(CollectionTOCConstants.NAME).toString)))
          MessageFormat.format("Row {0}", (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if (invalidCollectionNameErrorMessage.trim.nonEmpty)
      throw new ClientException("CSV_INVALID_COLLECTION_NAME", "Following rows have invalid Collection Name: " + invalidCollectionNameErrorMessage)
    // validate collection name column in CSV - END
    TelemetryManager.log("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating collection name column in CSV")
  }

  private def validateUnitIdentifiers(csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef]): Unit = {
    // validate Folder Identifier column in CSV - START
    val collectionChildNodes = collectionHierarchy(CollectionTOCConstants.CHILD_NODES).asInstanceOf[List[String]]

    val invalidCollectionNodeIDErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNodeIdentifierHeader.contains(colData._1) && (colData._2.isEmpty || !collectionChildNodes.contains(colData._2.trim)))
          MessageFormat.format("Row {0}", (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if (invalidCollectionNodeIDErrorMessage.trim.nonEmpty)
      throw new ClientException("CSV_INVALID_COLLECTION_NODE_ID", "Following rows have invalid folder identifier: " + invalidCollectionNodeIDErrorMessage)
    // validate Folder Identifier column in CSV - END
    TelemetryManager.log("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating Folder Identifier column in CSV")
  }

  private def validateQRCodes(csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Unit = {
    // Validate QR Codes with reserved DIAL codes - START
    val csvQRCodesList: List[String] = csvRecords.map(csvRecord => {
      csvRecord.toMap.asScala.toMap.get(qrCodeHdrColsList(1)).get.trim
    }).filter(msg => msg.nonEmpty).toList

    if(csvQRCodesList.nonEmpty) {
      val returnDIALCodes = validateDialCodes(collectionHierarchy(CollectionTOCConstants.CHANNEL).toString, csvQRCodesList)

      val invalidQRCodeErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (qrCodeHdrColsList.contains(colData._1) && (csvQRCodesList diff returnDIALCodes).contains(colData._2.trim))
            MessageFormat.format("Row {0}", (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
          else ""
        })
      }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if (invalidQRCodeErrorMessage.trim.nonEmpty)
        throw new ClientException("CSV_INVALID_DIAL_CODES", "Following rows have invalid DIAL codes: " + invalidQRCodeErrorMessage)
    }
    // Validate QR Codes with reserved DIAL codes - END
    TelemetryManager.log("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating QR Codes with reserved DIAL codes")
  }

  private def validateMappedTopics(csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): Unit = {
    // Validate Mapped Topics with Collection Framework data - START
    val mappedTopicsList = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (mappedTopicsHeader.contains(colData._1) && colData._2.nonEmpty) colData._2.trim.split(",").mkString(",") else ""
      })
    }).filter(msg => msg.nonEmpty).toList

    if(collectionHierarchy(CollectionTOCConstants.FRAMEWORK) == null || StringUtils.isBlank(collectionHierarchy.getOrElse(CollectionTOCConstants.FRAMEWORK,"").asInstanceOf[String]))
      throw new ClientException("FRAMEWORK_MISSING", "Please set a framework for the content.")

    if(mappedTopicsList.nonEmpty) {
      val frameworkId = collectionHierarchy(CollectionTOCConstants.FRAMEWORK).toString
      val frameworkGetResponse = getFrameworkTopics(frameworkId)
      val frameworkGetResult = frameworkGetResponse.getResult.getOrDefault(CollectionTOCConstants.FRAMEWORK, new util.HashMap[String, AnyRef]()).asInstanceOf[util.HashMap[String, AnyRef]].asScala.toMap[String, AnyRef]
      val frameworkCategories = frameworkGetResult.getOrDefault(CollectionTOCConstants.CATEGORIES, new util.ArrayList[util.Map[String,AnyRef]]()).asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]]

      val frameworkTopicList = frameworkCategories.flatMap(categoryData => {
        categoryData.map(colData => {
          if (categoryData(CollectionTOCConstants.CODE).equals(CollectionTOCConstants.TOPIC) && colData._1.equalsIgnoreCase(CollectionTOCConstants.TERMS))
            colData._2.asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]].asScala.toList.map(rec => rec.asScala.toMap[String,AnyRef]).map(_.getOrElse(CollectionTOCConstants.NAME, "")).asInstanceOf[List[String]]
          else  List.empty
        })
      }).filter(topic => topic.nonEmpty).flatten

      val invalidTopicsErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (mappedTopicsHeader.contains(colData._1) && colData._2.trim.nonEmpty) {
            val topicsDataList: List[String] = colData._2.trim.split(",").toList
            topicsDataList.map(topic => {
              if(!frameworkTopicList.contains(topic.trim)) MessageFormat.format("Row {0}", (csvRecord.getRecordNumber + 1).toString + " - " + topic) else ""
            }).filter(errmsg => errmsg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)
          } else ""
        })
      }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if (invalidTopicsErrorMessage.trim.nonEmpty)
        throw new ClientException("CSV_INVALID_MAPPED_TOPICS", "Following rows have invalid Mapped Topics: " + invalidTopicsErrorMessage)
    }
    // Validate Mapped Topics with Collection Framework data - END
    TelemetryManager.log("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating Mapped Topics with Collection Framework data")
  }

  private def validateLinkedContents(csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef])(implicit oec: OntologyEngineContext, ec: ExecutionContext): List[Map[String, AnyRef]] = {
    // Validate Linked Contents authenticity - START
    val csvLinkedContentsList: List[String] = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (linkedContentHdrColumnsList.contains(colData._1) && colData._2.trim.nonEmpty) colData._2.trim  else ""
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
        }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

        if(invalidContentTypeLinkedContentsList.trim.nonEmpty)
          throw new ClientException("CSV_INVALID_LINKED_CONTENTS_CONTENT_TYPE", "Following contents are not allowed due to invalid content types: "
            + invalidContentTypeLinkedContentsList)
      }
      TelemetryManager.log("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating Linked Contents")
      returnedLinkedContentsResult
    }
    else List.empty[Map[String, AnyRef]]
    // Validate Linked Contents authenticity - END
  }
}
