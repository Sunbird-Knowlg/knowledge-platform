package handlers

import org.apache.poi.xssf.usermodel.{XSSFRow, XSSFSheet, XSSFWorkbook}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, FileInputStream}
import scala.collection.mutable.ListBuffer

case class Activity(code: String, label: String)

object CompetencyExcelParser {

  private val logger: Logger = LoggerFactory.getLogger(classOf[CompetencyExcelParser.type])

  def getCompetenciesData(sheet: XSSFSheet) = {
    logger.info("Inside the getQuestions")
    (2 until sheet.getPhysicalNumberOfRows)
      .filter(rowNum => {
        val oRow = Option(sheet.getRow(rowNum))
        oRow match {
          case Some(x) => {
            val activityCode = sheet.getRow(rowNum).getCell(0)
            val activityLabel = sheet.getRow(rowNum).getCell(1)
            val questionCode = sheet.getRow(rowNum).getCell(2)
            boolean2Boolean(true)
          }
          case None => false
        }
      }).map(rowNum => parseCompetency(sheet.getRow(rowNum))).toList
  }

  def getYearWiseCompetencyMappingData(sheet: XSSFSheet): = {
    val sheetName = sheet.getSheetName.toLowerCase()
    if (sheetName.startsWith("first")) {
      logger.info("Inside the getQuestions")
      (2 until sheet.getPhysicalNumberOfRows)
        .filter(rowNum => {
          val oRow = Option(sheet.getRow(rowNum))
          oRow match {
            case Some(x) => {
              val activityCode = sheet.getRow(rowNum).getCell(0)
              val activityLabel = sheet.getRow(rowNum).getCell(1)
              val questionCode = sheet.getRow(rowNum).getCell(2)
              boolean2Boolean(true)
            }
            case None => false
          }
        }).map(rowNum => parseCompetency(sheet.getRow(rowNum))).toList
    }else if(true){

    }
  }

  def getCompetency(file: File) = {

    try {
      val workbook = new XSSFWorkbook(new FileInputStream(file))
      val sheetIndex = workbook.getNumberOfSheets
      (0 until sheetIndex)
      .foreach(index => {
        if(index == 0){
          getCompetenciesData(workbook.getSheetAt(index))
        }else{
          getYearWiseCompetencyMappingData(workbook.getSheetAt(index))
        }
      })
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
    val activityDetails = new Activity(activityCode, activityLabel)
    competency.put(questionCode, activityDetails)
    competency
  }

}
