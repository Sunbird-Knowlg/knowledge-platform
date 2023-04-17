package handlers

import org.apache.commons.lang3.StringUtils
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.{XSSFRow, XSSFWorkbook}
import org.slf4j.{Logger, LoggerFactory}
import org.sunbird.cache.impl.RedisCache
import utils.Constants

import java.io.{File, FileInputStream}
import java.util
import scala.collection.JavaConverters._

object QuestionExcelParser {

  private val logger: Logger = LoggerFactory.getLogger(RedisCache.getClass.getCanonicalName)

  def getQuestions(fileName: String, file: File) = {
    try {
      var board = "";
      if (fileName.startsWith("GNM")) { // checks for the filename that starts with GNM or ANM
        board = "GNM"
      } else if (fileName.startsWith("ANM")) {
        board = "ANM"
      }
      if (board.isEmpty) {
        throw new RuntimeException("Invalid file name")
      }
      val workbook = new XSSFWorkbook(new FileInputStream(file))
      val sheets = (2 until workbook.getNumberOfSheets).map(index => workbook.getSheetAt(index))  // iterates over the excelsheet
      sheets.flatMap(sheet => {
        logger.info("Inside the getQuestions")
        (1 until sheet.getPhysicalNumberOfRows)  // iterates over each row in the sheet
          .filter(rowNum => {
            val oRow = Option(sheet.getRow(rowNum))
            // matching the row value to determine the value of objects
            oRow match {
              case Some(x) => {
                val questionType = sheet.getRow(rowNum).getCell(11)
                val isMCQ = questionType.toString.startsWith(Constants.MCQ) || questionType.toString.endsWith(Constants.MCQ) // checks questionType is MCQ
                val isMTF = Constants.MTF.equals(questionType.toString) || Constants.MATCH_THE_FOLLOWING.equals(questionType.toString)
                val isFITB = Constants.FITB.equals(questionType.toString)
                val answerCell = sheet.getRow(rowNum).getCell(9)
                val isAnswerNotBlank = answerCell.getCellType() != CellType.BLANK
                isMCQ || isMTF || isFITB && isAnswerNotBlank
              }
              case None => false
            }
          })
          .map(rowNum => parseQuestion(sheet.getRow(rowNum), sheet.getSheetName, board)).toList
      })
    }

    catch {
      case e: Exception => throw new Exception("Invalid File")
    }
  }

  def buildDefaultQuestion() = {
    val defaultQuestion = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]

    defaultQuestion.put("code", "question")
    defaultQuestion.put("mimeType", "application/vnd.sunbird.question")
    defaultQuestion.put("objectType", "Question")
    defaultQuestion.put("primaryCategory", "Multiple Choice Question")
    defaultQuestion.put("qType", "MCQ")
    defaultQuestion.put("name", "Question")
    defaultQuestion
  }

  def buildOptionMap(option: String, level: Integer, answer: Boolean) = {
    val mapOptionValue = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]
    mapOptionValue.put("body", option)
    mapOptionValue.put("value", level)
    val mapOption = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]
    mapOption.put("answer", answer.asInstanceOf[AnyRef])
    mapOption.put("value", mapOptionValue)

    mapOption
  }

  // determines whether the Opton is correct
  def isOptionAnswer(optSeq: String, answerText: String): Boolean = {

    val correctOpt = answerText.split(",").map(_.trim)

    correctOpt.contains(optSeq)

  }

  def parseQuestion(xssFRow: XSSFRow, sheetName: String, board: String) = {
    val question = buildDefaultQuestion()

    val rowContent = (0 until xssFRow.getPhysicalNumberOfCells)
      .map(colId => Option(xssFRow.getCell(colId)).getOrElse("").toString).toList

    //fetches data from sheet
    val medium = rowContent.apply(7)
    val subject = rowContent.apply(0)
    val difficultyLevel = rowContent.apply(5)
    val questionText = rowContent.apply(8)
    val answer = rowContent.apply(10).trim


    var i = -1
    val options = new util.ArrayList[util.Map[String, AnyRef]](rowContent.apply(9).split("\n").filter(StringUtils.isNotBlank).map(o => {
      //      val option = o.split("[//)]").toList
      val option = o.split("[.).]").toList
      val optSeq = option.apply(0).trim

      val optText = option.apply(1).trim
      i += 1
      buildOptionMap(optText, i, isOptionAnswer(optSeq, answer))
    }).toList.asJava)

    val editorState = new util.HashMap().asInstanceOf[util.Map[String, AnyRef]]
    question.put("board", board)
    setArrayValue(question, medium, "medium")
    setArrayValue(question, subject, "subject")
    setArrayValue(question, sheetName, "gradeLevel")
    setArrayValue(question, difficultyLevel, "difficultyLevel")
    editorState.put("options", options)
    editorState.put("question", questionText)
    logger.info("Inside the parseQuestion")
    question.put("body", questionText)
    question.put("editorState", editorState)

    question
  }


  private def setArrayValue(question: util.Map[String, AnyRef], medium: String, questionKey: String) = {
    val valueList = new util.ArrayList[String]();
    valueList.add(medium);
    question.put(questionKey, valueList)
  }
}
