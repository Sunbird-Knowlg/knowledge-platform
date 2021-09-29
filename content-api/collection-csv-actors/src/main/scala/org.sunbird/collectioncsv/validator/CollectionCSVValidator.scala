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
  val maxUnitFieldLength: Int = Platform.getInteger(CollectionTOCConstants.COLLECTION_UNIT_FIELD_MAX_LENGTH, 50)
  val maxDescFieldLength: Int = Platform.getInteger(CollectionTOCConstants.COLLECTION_DESC_FIELD_MAX_LENGTH, 250)

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
        if (null == csvHeaders || csvHeaders.isEmpty) throw new ClientException("BLANK_CSV_DATA", "Not data found in the file. Please correct and upload again.")

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
      case ex: Exception =>  throw new ClientException("INVALID_CSV_FILE", "One or more columns without a header (first) row found. Please check the csv and upload again.")
    }
  }

  def validateCSVHeadersFormat(csvHeader: Map[String, Integer], mode:String) {
    val configHeaders: Map[String, Integer]  =  if(mode.equals(CollectionTOCConstants.CREATE)) createCSVHeaders else updateCSVHeaders
    //Check if Some columns are missing, any additional columns found, difference in header sequence
    if(!csvHeader.equals(configHeaders))
      validateDifferentialHeaders(csvHeader, configHeaders)
  }

  def validateCSVRecordsDataFormat(csvRecords: util.List[CSVRecord], mode: String) {
    //Check if CSV Records are empty
    if (null == csvRecords || csvRecords.isEmpty) throw new ClientException("BLANK_CSV_DATA", "Not data found in the file. Please correct and upload again.")
    // check if records are more than allowed csv rows
    if (csvRecords.nonEmpty && csvRecords.size > allowedNumberOfRecord) throw new ClientException("CSV_ROWS_EXCEEDS", s"Number of rows in the file exceeds the limit $allowedNumberOfRecord. Please reduce the number of folders and upload again.")

    validateMandatoryHeaderCols(csvRecords, mode)
    validateDuplicateRows(csvRecords)
    validateFieldsLength(csvRecords)

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

  private def validateDifferentialHeaders(csvHeader: Map[String, Integer], configHeaders: Map[String, Integer]): Unit = {
    // validate Header sequence
    if((configHeaders.keySet diff csvHeader.keySet).isEmpty && (csvHeader.keySet diff configHeaders.keySet).isEmpty && csvHeader.toSeq != configHeaders.toSeq) {
      val errorMessage = MessageFormat.format("The columns in csv are not in correct order as required. Please check the sample file and follow the same order. ")
      throw new ClientException("INVALID_HEADER_SEQUENCE", errorMessage)
    }
    //Check if Some columns are missing
    if((configHeaders.keySet diff csvHeader.keySet).nonEmpty && (configHeaders.keySet diff csvHeader.keySet).toList.head.nonEmpty) {
      val missingCols = (configHeaders.keySet diff csvHeader.keySet) mkString ",\n"
      throw new ClientException("REQUIRED_HEADER_MISSING", MessageFormat.format("Following columns are missing in the file. Please correct the same and upload again. \n "+missingCols))
    }
    //Check if any additional columns found
    if((csvHeader.keySet diff configHeaders.keySet).nonEmpty && (csvHeader.keySet diff configHeaders.keySet).toList.head.nonEmpty) {
      val additionalCols = (csvHeader.toSet diff configHeaders.toSet).toMap.keySet mkString ",\n"
      throw new ClientException("ADDITIONAL_HEADER_FOUND", MessageFormat.format("Following invalid columns found in the file. Please check the sample file and provide correct columns. \n"+additionalCols))
    }
  }

  private def validateMandatoryHeaderCols(csvRecords: util.List[CSVRecord], mode: String): Unit = {
    // Check if data exists in mandatory columns - START
    val mandatoryDataHdrCols =  if(mode.equals(CollectionTOCConstants.CREATE)) createCSVMandatoryHeaderCols else updateCSVMandatoryHeaderCols

    val mandatoryMissingDataList = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(mandatoryDataHdrCols.contains(colData._1) && colData._2.trim.isEmpty)
          MessageFormat.format("\nRow {0} - column: {1}", (csvRecord.getRecordNumber+1).toString,colData._1)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(",")

    if(mandatoryMissingDataList.trim.nonEmpty)
      throw new ClientException("REQUIRED_FIELD_MISSING", "Following rows have missing folder values. Please correct and upload again: "
        + mandatoryMissingDataList.split(",").distinct.mkString(CollectionTOCConstants.COMMA_SEPARATOR))
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
          MessageFormat.format("\nRow {0} - column: {1}", (csvRecord.getRecordNumber+1).toString,colData._1)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(",")

    if(missingDataList.trim.nonEmpty)
      throw new ClientException("REQUIRED_FIELD_MISSING", "Following rows have missing folder values. Please correct and upload again: "
        + missingDataList.split(",").distinct.mkString(CollectionTOCConstants.COMMA_SEPARATOR))
    // Check if data exists in hierarchy folder columns - END
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
      MessageFormat.format("\nRow {0}", (dupRecord.getRecordNumber+1).toString)
    }).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if(dupRecordsList.trim.nonEmpty)
      throw new ClientException("DUPLICATE_ROWS", "Following rows are duplicate, they have exactly same folder level values. Please correct and upload again: " + dupRecordsList)
    // Verify if there are any duplicate hierarchy folder structure - END
  }

  private def validateQRCodeColumns(csvRecords: util.List[CSVRecord]): Unit = {
    // Verify if there are any QR Codes data entry issues - START
    val qrDataErrorMessage = csvRecords.map(csvRecord => {
      val csvRecordMap = csvRecord.toMap.asScala.toMap
      if((csvRecordMap(qrCodeHdrColsList.head).equalsIgnoreCase(CollectionTOCConstants.NO) || csvRecordMap(qrCodeHdrColsList.head).isEmpty) &&
        csvRecordMap(qrCodeHdrColsList(1)).nonEmpty)
        MessageFormat.format("\nRow {0}", (csvRecord.getRecordNumber+1).toString)
      else
        ""
    }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if(qrDataErrorMessage.trim.nonEmpty)
      throw new ClientException("ERROR_QR_CODE_ENTRY", "Following rows have incorrect QR Code entries. “QR Code Required?” should be “Yes” if there is a value in QR Code column. Please correct and upload again: " + qrDataErrorMessage)
    // Verify if there are any QR Codes data entry issues - END
    // Verify if there are any duplicate QR Codes - START
    val dupQRListMsg = csvRecords.filter(csvRecord => {
      csvRecords.exists(record => {
        record.get(CollectionTOCConstants.QR_CODE).nonEmpty && csvRecord.get(CollectionTOCConstants.QR_CODE).nonEmpty && record.get(CollectionTOCConstants.QR_CODE).equals(csvRecord.get(CollectionTOCConstants.QR_CODE)) &&
          !csvRecord.getRecordNumber.equals(record.getRecordNumber)
      })
    }).map(dupQRRecord => {
      MessageFormat.format("\nRow {0}", (dupQRRecord.getRecordNumber+1).toString)
    }).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if(dupQRListMsg.trim.nonEmpty)
      throw new ClientException("DUPLICATE_QR_CODE_ENTRY", "Following rows have duplicate QR Code values. Please correct and upload again: " + dupQRListMsg)
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
          MessageFormat.format("\nRow {0} - column: {1}", (csvRecord.getRecordNumber+1).toString,colData._1)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if(missingLinkedContentDataList.trim.nonEmpty)
      throw new ClientException("LINKED_CONTENTS_DATA_MISSING", "Following rows have missing “Linked Content” values. Please correct and upload again: " + missingLinkedContentDataList)
    // Check if data exists in Linked content columns - END
  }

  private def validateCollectionName(csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef]): Unit = {
    // validate collection name column in CSV - START
    val invalidCollectionNameErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNameHeader.contains(colData._1) && (colData._2.trim.isEmpty || !colData._2.trim.equalsIgnoreCase(collectionHierarchy(CollectionTOCConstants.NAME).toString)))
          MessageFormat.format("\nRow {0}", (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if (invalidCollectionNameErrorMessage.trim.nonEmpty)
      throw new ClientException("CSV_INVALID_COLLECTION_NAME", "Following rows have invalid “Collection Name”. Please correct and upload again: " + invalidCollectionNameErrorMessage)
    // validate collection name column in CSV - END
    TelemetryManager.log("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating collection name column in CSV")
  }

  private def validateFieldsLength(csvRecords: util.List[CSVRecord]): Unit = {
    validateUnitFieldLength(csvRecords)
    validateDescFieldLength(csvRecords)
  }

  private def validateUnitFieldLength(csvRecords: util.List[CSVRecord]): Unit = {
    // validate Units' name length
    val invalidUnitLengthErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (folderHierarchyHdrColumnsList.contains(colData._1) &&
          (colData._2.trim.nonEmpty && colData._2.trim.length>maxUnitFieldLength)) MessageFormat.format("\nRow {0}", (csvRecord.getRecordNumber + 1).toString + " - " + colData._1) else ""
      })}).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if(invalidUnitLengthErrorMessage.trim.nonEmpty)
      throw new ClientException("CSV_INVALID_FIELDS_LENGTH", s"Following rows have invalid “Folder Name”. The maximum length of characters should be $maxUnitFieldLength. Please correct and upload again: "+invalidUnitLengthErrorMessage)
  }

  private def validateDescFieldLength(csvRecords: util.List[CSVRecord]): Unit = {
    // validate Description column data length in CSV
    val invalidDescLengthErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (colData._1.trim.equalsIgnoreCase("Description") &&
          (colData._2.trim.nonEmpty && colData._2.trim.length>maxDescFieldLength)) MessageFormat.format("\nRow {0}", (csvRecord.getRecordNumber + 1).toString) else ""
      })}).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if(invalidDescLengthErrorMessage.trim.nonEmpty)
      throw new ClientException("CSV_INVALID_FIELDS_LENGTH", s"Following rows have invalid “Description”. The maximum length of characters should be $maxDescFieldLength. Please correct and upload again "+invalidDescLengthErrorMessage)
  }

  private def validateUnitIdentifiers(csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef]): Unit = {
    // validate Folder Identifier column in CSV - START
    val collectionChildNodes = collectionHierarchy(CollectionTOCConstants.CHILD_NODES).asInstanceOf[List[String]]

    val invalidCollectionNodeIDErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNodeIdentifierHeader.contains(colData._1) && (colData._2.isEmpty || !collectionChildNodes.contains(colData._2.trim)))
          MessageFormat.format("\nRow {0}", (csvRecord.getRecordNumber + 1).toString)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if (invalidCollectionNodeIDErrorMessage.trim.nonEmpty)
      throw new ClientException("CSV_INVALID_COLLECTION_NODE_ID", "Following rows have invalid “Folder Identifier”. Please correct and upload again: " + invalidCollectionNodeIDErrorMessage)

    val folderIdentifierList  = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNodeIdentifierHeader.contains(colData._1) && collectionChildNodes.contains(colData._2.trim)) colData._2.trim else ""
      })
    }).filter(msg => msg.nonEmpty)

    // Verify if there are any duplicate folder identifier - START
    val dupFolderIdentifierList = csvRecords.filter(csvRecord => {
      csvRecords.exists(record => {
        val csvRecordFolderIdentifier = csvRecord.toMap.asScala.toMap.map(colData => {
          if(collectionNodeIdentifierHeader.contains(colData._1))
            colData
        })
        val recordFolderIdentifier = record.toMap.asScala.toMap.map(colData => {
          if(collectionNodeIdentifierHeader.contains(colData._1))
            colData
        })
        recordFolderIdentifier.equals(csvRecordFolderIdentifier) && !csvRecord.getRecordNumber.equals(record.getRecordNumber)
      })
    }).map(dupRecord => {
      MessageFormat.format("\nRow {0}", (dupRecord.getRecordNumber+1).toString)
    }).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

    if(dupFolderIdentifierList.trim.nonEmpty)
      throw new ClientException("DUPLICATE_ROWS", "Following rows have duplicate “Folder Identifier”. Please correct and upload again: " + dupFolderIdentifierList)
    // Verify if there are any duplicate folder identifier - END

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
            MessageFormat.format("\nRow {0}", (csvRecord.getRecordNumber + 1).toString)
          else ""
        })
      }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if (invalidQRCodeErrorMessage.trim.nonEmpty)
        throw new ClientException("CSV_INVALID_DIAL_CODES", "Following rows have invalid “QR Code” values. Please correct and upload again: " + invalidQRCodeErrorMessage)
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

    if(mappedTopicsList.nonEmpty) {
      if(!collectionHierarchy.contains(CollectionTOCConstants.FRAMEWORK) || StringUtils.isBlank(collectionHierarchy.getOrElse(CollectionTOCConstants.FRAMEWORK,"").asInstanceOf[String]))
        throw new ClientException("FRAMEWORK_MISSING", "Please set a framework for the content.")

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
              if(!frameworkTopicList.contains(topic.trim)) MessageFormat.format("\nRow {0}", (csvRecord.getRecordNumber + 1).toString + " - " + topic) else ""
            }).filter(errmsg => errmsg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)
          } else ""
        })
      }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if (invalidTopicsErrorMessage.trim.nonEmpty)
        throw new ClientException("CSV_INVALID_MAPPED_TOPICS", "Following rows have invalid “Mapped Topics”. Please correct and upload again: " + invalidTopicsErrorMessage)
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

    if(csvLinkedContentsList.contains(collectionHierarchy(CollectionTOCConstants.IDENTIFIER).toString))
      throw new ClientException("CURRENT_COLLECTION_AS_LINKED_CONTENT", "You cannot add the current collection you are editing as part of linked contents.")

    if (csvLinkedContentsList.nonEmpty) {
      val returnedLinkedContentsResult: List[Map[String, AnyRef]] = searchLinkedContents(csvLinkedContentsList)
      TelemetryManager.info("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> linked content search response:: returnedLinkedContentsResult:: " +  returnedLinkedContentsResult.toString())
      val returnedLinkedContentsIdentifierList = returnedLinkedContentsResult.map(_.getOrElse(CollectionTOCConstants.IDENTIFIER, "")).asInstanceOf[List[String]]
      TelemetryManager.info("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> linked content search response:: csvLinkedContentsList:: " +  csvLinkedContentsList.toString() + " || returnedLinkedContentsIdentifierList:: " + returnedLinkedContentsIdentifierList.toString())
      val invalidLinkedContentsErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (linkedContentHdrColumnsList.contains(colData._1) && (csvLinkedContentsList.toSet.toList diff returnedLinkedContentsIdentifierList).contains(colData._2))
            MessageFormat.format("\nRow {0}", (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
          else
            ""
        })
      }).filter(msg => msg.nonEmpty).mkString(CollectionTOCConstants.COMMA_SEPARATOR)

      if (invalidLinkedContentsErrorMessage.trim.nonEmpty)
        throw new ClientException("CSV_INVALID_LINKED_CONTENTS", "Following rows have invalid linked contents. No live content found for the given do_id. Please correct and upload again: " + invalidLinkedContentsErrorMessage)

      TelemetryManager.log("CollectionCSVActor --> validateCSVRecordsDataAuthenticity --> after validating Linked Contents")
      returnedLinkedContentsResult
    }
    else List.empty[Map[String, AnyRef]]
    // Validate Linked Contents authenticity - END
  }
}
