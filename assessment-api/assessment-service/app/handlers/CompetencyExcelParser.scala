package handlers

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.{XSSFRow, XSSFSheet, XSSFWorkbook}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, FileInputStream}
import java.util
import scala.collection.JavaConverters.{asScalaIteratorConverter, iterableAsScalaIterableConverter, mapAsScalaMapConverter}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class Activity(code: String, label: String)

object CompetencyExcelParser {

  private val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  private var getData: List[util.Map[String, AnyRef]] = List.empty

  def parseCompetencyData(xssFRow: XSSFRow) = {
    val data = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]
    val listData = new util.ArrayList[AnyRef]()

    val rowContent = (0 until xssFRow.getPhysicalNumberOfCells)
      .map(colId => Option(xssFRow.getCell(colId)).getOrElse("").toString).toList

    val function = rowContent.apply(0).trim
    val year = rowContent.apply(1).trim
    val roleId = rowContent.apply(2).trim
    val roleLabel = rowContent.apply(3).trim
    val competencyMapping = rowContent.apply(4).trim
    val activityId = rowContent.apply(5).trim
    val activityLabel = rowContent.apply(6).trim
    val competencyId= rowContent.apply(7).trim
    val competency = rowContent.apply(8).trim
    val competencyLevelId = rowContent.apply(9).trim
    listData.add(function)
    listData.add(year)
    listData.add(roleId)
    listData.add(roleLabel)
    listData.add(activityId)
    listData.add(activityLabel)
    listData.add(competencyId)
    listData.add(competency)
    listData.add(competencyLevelId)
    //val competencyLevel = rowContent.apply(2).trim
    data.put(competencyMapping, listData)
    data
  }


  /*def getCompetenciesData1(sheet: XSSFSheet): List[util.Map[String, AnyRef]] = {
    val column = sheet.asScala.drop(1).map(row =>
      if (sheet.getWorkbook.getSheetIndex(sheet) == 1 || sheet.getWorkbook.getSheetIndex(sheet) == 8)
        row.getCell(4)
      else
        row.getCell(5)
    )

    val formatter = new DataFormatter()
    val uniqueCompetencies = column.map(cell => formatter.formatCellValue(cell)).toList

    val rows = sheet.asScala.drop(1)
    getData = rows.flatMap(row => {
      val cell = if (sheet.getWorkbook.getSheetIndex(sheet) == 1 || sheet.getWorkbook.getSheetIndex(sheet) == 8)
        row.getCell(4)
      else
        row.getCell(5)
      if (cell != null && uniqueCompetencies.contains(cell.getStringCellValue)) {
        //if(row.getCell(1)!=null||row.getCell(2)!=null||row.getCell(2)!=null||row.getCell(4)!=null||row.getCell(5)!=null)
        Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)
      } else {
        None
      }
    }).toList
    getData
  }*/
   def getCompetency(file: File): List[java.util.Map[String, AnyRef]] = {
      logger.info("enter into the getCompetency method")
      val finalData: mutable.Map[String, List[Map[String, AnyRef]]] = mutable.Map.empty
      try {
        val workbook = new XSSFWorkbook(new FileInputStream(file))
        (1 until workbook.getNumberOfSheets)
          .foreach(index => {
           // if (index ==1) {
            getData = getCompetenciesData(workbook.getSheetAt(index))
           /*}else {
            getData=getCompetenciesDataFromSheet(workbook.getSheetAt(index))
          }*/
            val convertedData = getData.map(_.asScala.toMap)
            finalData += (workbook.getSheetName(index) -> convertedData)
            getData = finalData.toList.flatMap { case (_, maps) => maps.map(convertMap) }
          })
        getData

      } catch {
        case e: Exception => throw new Exception("Invalid File")
      }
    }

  /*def getCompetenciesData2(sheet: XSSFSheet): List[util.Map[String, AnyRef]] = {
    val columnIdx = if (sheet.getWorkbook.getSheetIndex(sheet) == 1 || sheet.getWorkbook.getSheetIndex(sheet) == 8) 4 else 5
    val rows = sheet.asScala.drop(1)
    val formatter = new DataFormatter()

    def getCell(row: Row, colIdx: Int): Option[Cell] = {
      val cell = row.getCell(colIdx)
      Option(cell).filter(c => Option(formatter.formatCellValue(c)).exists(_.nonEmpty))
    }

    val uniqueCompetencies = rows.flatMap(row => getCell(row, columnIdx)).map(cell => formatter.formatCellValue(cell)).toList

    rows.flatMap(row => {
      getCell(row, columnIdx).filter(cell => uniqueCompetencies.contains(formatter.formatCellValue(cell))).map(row => Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)).flatten
    }).toList
  }*/

  def convertMap(map: Map[String, AnyRef]): util.Map[String, AnyRef] = {
    val javaMap = new util.HashMap[String, AnyRef]()
    map.foreach { case (k, v) => javaMap.put(k, v) }
    javaMap
  }

  def getCompetenciesData(sheet: XSSFSheet): List[util.Map[String, AnyRef]] = {
    /* val column = sheet.asScala.drop(1).map(row =>
       if (sheet.getWorkbook.getSheetIndex(sheet) == 1 || sheet.getWorkbook.getSheetIndex(sheet) == 8)
         row.getCell(4)
       else
         row.getCell(5)
     ).toList*/

    /*val formatter = new DataFormatter()
    val uniqueCompetencies = column.map(cell => formatter.formatCellValue(cell)).toList*/

    val rows = sheet.asScala.drop(1)
    getData = rows.flatMap(row => {
      val rowValue = {
        if (sheet.getWorkbook.getSheetIndex(sheet) == 1 || sheet.getWorkbook.getSheetIndex(sheet) == 8) {
          if (!row.getCell(4).getStringCellValue.isEmpty)
            row.getCell(4)
        } else if (!row.getCell(5).getStringCellValue.isEmpty)
          row.getCell(5)
      }
      if (sheet.getWorkbook.getSheetIndex(sheet) == 1 && rowValue != null)
        Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)
      else if (sheet.getWorkbook.getSheetIndex(sheet) == 2 && rowValue != null)
        Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)
      else if (sheet.getWorkbook.getSheetIndex(sheet) == 3 && rowValue != null)
        Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)
      else if (sheet.getWorkbook.getSheetIndex(sheet) == 4 && rowValue != null)
        Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)
      else if (sheet.getWorkbook.getSheetIndex(sheet) == 5 && rowValue != null)
        Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)
      else if (sheet.getWorkbook.getSheetIndex(sheet) == 6 && rowValue != null)
        Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)
      else if (sheet.getWorkbook.getSheetIndex(sheet) == 7 && rowValue != null)
        Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)
      else if (sheet.getWorkbook.getSheetIndex(sheet) == 8 && rowValue != null)
        Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)
      else if (sheet.getWorkbook.getSheetIndex(sheet) == 9 && rowValue != null)
        Option(sheet.getRow(row.getRowNum)).map(parseCompetencyData)
      else {
        None
      }
    }).toList
    getData
  }

}
