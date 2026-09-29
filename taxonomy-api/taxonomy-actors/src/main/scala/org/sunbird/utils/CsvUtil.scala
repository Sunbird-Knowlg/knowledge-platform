package org.sunbird.utils

import org.apache.commons.csv.{CSVFormat, CSVPrinter}

import java.io.{File, FileOutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.util.Using

object CsvUtil {

  def writeCsv(file: File, headers: List[String], rows: List[List[String]]): File = {
    Using.resource(new FileOutputStream(file)) { fos =>
      Using.resource(new OutputStreamWriter(fos, StandardCharsets.UTF_8)) { out =>
        Using.resource(new CSVPrinter(out, CSVFormat.DEFAULT)) { csvPrinter =>
          csvPrinter.printRecord(headers.asJava)
          rows.foreach(row => csvPrinter.printRecord(row.asJava))
        }
      }
    }
    file
  }
}