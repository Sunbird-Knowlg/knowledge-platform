package handlers

import org.apache.commons.lang3.StringUtils
import org.apache.poi.xssf.usermodel.{XSSFRow, XSSFWorkbook}

import java.io.{File, FileInputStream}
import java.util
import scala.collection.JavaConverters._
import org.slf4j.{Logger, LoggerFactory}
import org.sunbird.cache.impl.RedisCache

object QuestionExcelParser {

  private val logger: Logger = LoggerFactory.getLogger(RedisCache.getClass.getCanonicalName)
  def getQuestions(file: File) = {

    try {
      val workbook = new XSSFWorkbook(new FileInputStream(file))
      val sheet = workbook.getSheetAt(0)
      logger.info("Inside the getQuestions")
      (1 until sheet.getPhysicalNumberOfRows)
        .filter(rowNum => {
          val oRow = Option(sheet.getRow(rowNum))
          oRow match {
            case Some(x) => {
              val questionText = sheet.getRow(rowNum).getCell(4)
              val questionType = sheet.getRow(rowNum).getCell(7)
              boolean2Boolean("MCQ".equals(questionType.toString))
            }
            case None => false
          }
        })
        .map(rowNum => parseQuestion(sheet.getRow(rowNum))).toList
    } catch {
      case e : Exception => throw new Exception("Invalid File")
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

  def isOptionAnswer(optSeq: String, answerText: String): Boolean = {

    val correctOpt = answerText.split(",").map(_.trim)

    correctOpt.contains(optSeq)
  }

  def parseQuestion(xssFRow: XSSFRow) = {
    val question = buildDefaultQuestion()


    val rowContent = (0 until xssFRow.getPhysicalNumberOfCells)
      .map(colId => Option(xssFRow.getCell(colId)).getOrElse("").toString).toList

    val questionText = rowContent.apply(4)
    val answer = rowContent.apply(6).trim


    var i = -1
    val options = new util.ArrayList[util.Map[String, AnyRef]](rowContent.apply(5).split("\n").filter(StringUtils.isNotBlank).map(o => {
      val option = o.split("[//)]").toList
      val optSeq = option.apply(0).trim
      val optText = option.apply(1).trim
      i += 1
      buildOptionMap(optText, i, isOptionAnswer(optSeq, answer))
    }).toList.asJava)

    val editorState = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]
    editorState.put("options", options)
    editorState.put("question", questionText)
    logger.info("Inside the parseQuestion")
    question.put("body", questionText)
    question.put("editorState", editorState)
    question
  }

}
