package handlers

import org.apache.poi.xssf.usermodel.{XSSFRow, XSSFSheet, XSSFWorkbook}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, FileInputStream}
import scala.collection.mutable.ListBuffer

case class Activity(code: String, label: String)

object CompetencyExcelParser {

  private val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  private var getData = new java.util.HashMap().asInstanceOf[ListBuffer[List[java.util.Map[String, Activity]]]]

  private var yearData = new java.util.HashMap().asInstanceOf[List[java.util.Map[String, Activity]]]


  def parseCompetencyData(xssFRow: XSSFRow) = {
    val competency = new java.util.HashMap().asInstanceOf[java.util.Map[String, Activity]]

    val rowContent = (0 until xssFRow.getPhysicalNumberOfCells)
      .map(colId => Option(xssFRow.getCell(colId)).getOrElse("").toString).toList

    val activityCode = rowContent.apply(0).trim
    val activityLabel = rowContent.apply(1).trim
    val questionCode = rowContent.apply(2).trim
    val activityDetails = Activity(activityCode, activityLabel)
    competency.put(questionCode, activityDetails)
    competency
  }

  def getCompetenciesData(sheet: XSSFSheet, yearWiseEntries: ListBuffer[List[java.util.Map[String, Activity]]]) = {
    logger.info("Inside the getCompetenciesData")
    val sheetName = sheet.getSheetName.toLowerCase()
    if (sheetName.startsWith("competencies")) {
      logger.info("Inside the competencies sheet")
      getData = {
        (1 until sheet.getPhysicalNumberOfRows)
          .filter(rowNum => {
            val oRow = Option(sheet.getRow(rowNum))
            oRow match {
              case Some(x) => {
                val competencyBoolean = sheet.getRow(rowNum).getCell(4)
                boolean2Boolean("CDM".equals(competencyBoolean.toString))
              }
              case None => false
            }
          }).map(rowNum => parseCompetencyData(sheet.getRow(rowNum))).toList
      }
    }
    getData
  }

  def getRowNum(sheet: XSSFSheet) = {
    (1 until sheet.getPhysicalNumberOfRows)
      .filter(rowNum => {
        val oRow = Option(sheet.getRow(rowNum))
        oRow match {
          case Some(x) => {
            val competencyBoolean = sheet.getRow(rowNum).getCell(4)
            boolean2Boolean("CDM".equals(competencyBoolean.toString))
          }
          case None => false
        }
      }).map(rowNum => parseCompetency(sheet.getRow(rowNum))).toList
  }

  def getYearWiseCompetencyMappingData(sheet: XSSFSheet)= {
    val sheetName = sheet.getSheetName.toLowerCase()
    if (sheetName.startsWith("first")) {
      logger.info("Inside the first sheet")
      yearData =  getRowNum(sheet)
    }else if (sheetName.startsWith("second")) {
      logger.info("Inside the second sheet")
      yearData = getRowNum(sheet)
    }else if (sheetName.startsWith("third")) {
      logger.info("Inside the third sheet")
      yearData = getRowNum(sheet)
    }
    yearData
  }

  def getCompetency(file: File): List[java.util.Map[String, Activity]] = {
    try {
      val workbook = new XSSFWorkbook(new FileInputStream(file))
      (workbook.getNumberOfSheets - 1 until -1)
        .foreach(index => {
          if (index == 0) {
            getData = getCompetenciesData(workbook.getSheetAt(index), getData)
          } else {
            getData = ListBuffer.apply(getYearWiseCompetencyMappingData(workbook.getSheetAt(index)))
          }
        })
      yearData
    } catch {
      case e: Exception => throw new Exception("Invalid File")
    }
  }

  def parseCompetency(xssFRow: XSSFRow) = {
    val competency = new java.util.HashMap().asInstanceOf[java.util.Map[String, Activity]]

    val rowContent = (0 until xssFRow.getPhysicalNumberOfCells)
      .map(colId => Option(xssFRow.getCell(colId)).getOrElse("").toString).toList

    val activityCode = rowContent.apply(0).trim
    val activityLabel = rowContent.apply(1).trim
    val questionCode = rowContent.apply(2).trim
    val activityDetails = Activity(activityCode, activityLabel)
    competency.put(questionCode, activityDetails)
    competency
  }
}
