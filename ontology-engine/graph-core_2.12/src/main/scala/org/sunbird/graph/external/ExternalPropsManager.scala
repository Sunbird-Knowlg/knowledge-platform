
package org.sunbird.graph.external

import java.util
import org.sunbird.common.dto.{Request, Response}
import org.sunbird.graph.external.store.ExternalStoreFactory
import org.sunbird.schema.SchemaValidatorFactory

import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object ExternalPropsManager {
    def saveProps(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        val objectType: String = request.getObjectType
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
        store.insert(request.getRequest, getPropsDataType(schemaName, version))
    }

    def savePropsWithTtl(request: Request, ttl: Int)(implicit ec: ExecutionContext): Future[Response] = {
        val objectType: String = request.getObjectType
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
        store.insertWithTtl(request.getRequest, getPropsDataType(schemaName, version), ttl)
    }

    def fetchProps(request: Request, fields: List[String])(implicit ec: ExecutionContext): Future[Response] = {
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
        if (request.get("identifiers") != null) store.read(request.get("identifiers").asInstanceOf[List[String]], fields, getPropsDataType(schemaName, version))
        else {
            val identifier: Any = request.get("identifier")
            identifier match {
                case str: String =>
                    store.read(str, fields, getPropsDataType(schemaName, version))

                case uuid: UUID =>
                    store.read(uuid, fields, getPropsDataType(schemaName, version))
            }
    }
    }

    def deleteProps(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
        store.delete(request.get("identifiers").asInstanceOf[List[String]])
    }

    def update(request: Request)(implicit ec: ExecutionContext): Future[Response] = {
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
        store.update(request.get("identifier").asInstanceOf[String], request.get("fields").asInstanceOf[List[String]],
            request.get("values").asInstanceOf[List[java.util.Map[String, AnyRef]]], getPropsDataType(schemaName, version))
    }

    def updateWithTtl(request: Request, ttl: Int)(implicit ec: ExecutionContext): Future[Response] = {
        val schemaName: String = request.getContext.get("schemaName").asInstanceOf[String]
        val version: String = request.getContext.get("version").asInstanceOf[String]
        val primaryKey: util.List[String] = SchemaValidatorFactory.getExternalPrimaryKey(schemaName, version)
        val store = ExternalStoreFactory.getExternalStore(SchemaValidatorFactory.getExternalStoreName(schemaName, version), primaryKey)
        store.updateWithTtl(request.get("identifier").asInstanceOf[String], request.get("fields").asInstanceOf[List[String]],
            request.get("values").asInstanceOf[List[java.util.Map[String, AnyRef]]], getPropsDataType(schemaName, version), ttl)
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