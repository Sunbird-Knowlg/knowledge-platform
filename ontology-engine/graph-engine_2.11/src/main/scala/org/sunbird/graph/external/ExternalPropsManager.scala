
package org.sunbird.graph.external

import java.util

import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.external.store.ExternalStoreFactory
import org.sunbird.schema.SchemaValidatorFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

object ExternalPropsManager {
    def saveProps(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        val objectType: String = request.getObjectType
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
        store.insert(request.getRequest, getPropsDataType(schemaName, version))
    }

    def fetchProps(request: Request, fields: List[String])(implicit ec: ExecutionContext): Future[Response] = {
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
        store.read(request.get("identifier").asInstanceOf[String], fields, getPropsDataType(schemaName, version))
    }

    def deleteProps(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
        store.delete(request.get("identifiers").asInstanceOf[List[String]])
    }

    def updateMapRecord(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
        store.updateMapRecord(request.get("identifier").asInstanceOf[String], request.get("fields").asInstanceOf[List[String]],
            request.get("values").asInstanceOf[List[java.util.Map[String, AnyRef]]])
    }

    def getPropsDataType(schemaName: String, version: String) = {
        val propTypes: Map[String, String] = SchemaValidatorFactory.getInstance(schemaName, version).getConfig.getAnyRef("external.properties")
                .asInstanceOf[java.util.HashMap[String, AnyRef]].asScala
                .map{ ele =>
                    ele._1 -> ele._2.asInstanceOf[java.util.HashMap[String, AnyRef]].asScala.getOrElse("type", "").asInstanceOf[String]
                }.toMap
        propTypes
    }

}