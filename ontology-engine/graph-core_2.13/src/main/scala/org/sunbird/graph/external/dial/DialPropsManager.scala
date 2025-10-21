package org.sunbird.graph.external.dial

import org.sunbird.common.dto.{Request, Response}
import org.sunbird.schema.SchemaValidatorFactory

import java.util
import scala.concurrent.{ExecutionContext, Future}
// $COVERAGE-OFF$ Disabling scoverage
object DialPropsManager {
  def saveProps(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
    val objectType: String = request.getObjectType
    val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
    val version: String = request.getContext.get("version").asInstanceOf[String]
    val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
    val store = DialStoreFactory.getDialStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
    store.insert(request.getRequest, getPropsDataType(schemaName, version))
  }
  def getPropsDataType(schemaName: String, version: String) = {
    val propTypes: Map[String, String] = SchemaValidatorFactory.getInstance(schemaName, version).getConfig.getAnyRef("external.properties")
      .asInstanceOf[java.util.HashMap[String, AnyRef]].asScala
      .map { ele =>
        ele._1 -> ele._2.asInstanceOf[java.util.HashMap[String, AnyRef]].asScala.getOrElse("type", "").asInstanceOf[String]
      }.toMap
    propTypes
  }

}

// $COVERAGE-ON$ Enabling scoverage
