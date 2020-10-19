package org.sunbird.graph.external.store

import java.sql.Timestamp
import java.util
import java.util.Date

import com.datastax.driver.core.Session
import com.datastax.driver.core.querybuilder.{Clause, Insert, QueryBuilder}
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture, MoreExecutors}
import org.apache.commons.lang3.StringUtils
import org.sunbird.cassandra.{CassandraConnector, CassandraStore}
import org.sunbird.common.dto.ResponseHandler
import org.sunbird.common.dto.Response
import org.sunbird.common.exception.{ErrorCodes, ResponseCode, ServerException}
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.{ExecutionContext, Future, Promise}

class ExternalStore(keySpace: String , table: String , primaryKey: java.util.List[String]) extends CassandraStore(keySpace, table, primaryKey) {

    def insert(request: util.Map[String, AnyRef], propsMapping: Map[String, String])(implicit ec: ExecutionContext): Future[Response] = {
        val insertQuery: Insert = QueryBuilder.insertInto(keySpace, table)
        val identifier = request.get("identifier")
        insertQuery.value(primaryKey.get(0), identifier)
        request.remove("identifier")
        request.remove("last_updated_on")
        if(propsMapping.keySet.contains("last_updated_on"))
            insertQuery.value("last_updated_on", new Timestamp(new Date().getTime))
        import scala.collection.JavaConverters._
        for ((key, value) <- request.asScala) {
            if("blob".equalsIgnoreCase(propsMapping.getOrElse(key, "")))
                insertQuery.value(key, QueryBuilder.fcall("textAsBlob", value))
            else
                insertQuery.value(key, value)
        }
        try {
            val session: Session = CassandraConnector.getSession
            session.executeAsync(insertQuery).asScala.map( resultset => {
                ResponseHandler.OK()
            })
        } catch {
            case e: Exception =>
                e.printStackTrace()
                TelemetryManager.error("Exception Occurred While Saving The Record. | Exception is : " + e.getMessage, e)
                throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name, "Exception Occurred While Saving The Record. Exception is : " + e.getMessage)
        }
    }

    /**
      * Fetching properties which are stored in an external database
      * @param identifier
      * @param extProps
      * @param ec
      * @return
      */
    def read(identifier: String, extProps: List[String], propsMapping: Map[String, String])(implicit ec: ExecutionContext): Future[Response] = {
        val select = QueryBuilder.select()
        if(null != extProps && !extProps.isEmpty){
            extProps.foreach(prop => {
                if("blob".equalsIgnoreCase(propsMapping.getOrElse(prop, "")))
                    select.fcall("blobAsText", QueryBuilder.column(prop)).as(prop)
                else
                    select.column(prop).as(prop)
            })
        }
        val selectQuery = select.from(keySpace, table)
        val clause: Clause = QueryBuilder.eq(primaryKey.get(0), identifier)
        selectQuery.where.and(clause)
        try {
            val session: Session = CassandraConnector.getSession
            session.executeAsync(selectQuery).asScala.map(resultSet => {
                if (resultSet.iterator().hasNext) {
                    val row = resultSet.one()
                    val externalMetadataMap = extProps.map(prop => prop -> row.getObject(prop)).toMap
                    val response = ResponseHandler.OK()
                    import scala.collection.JavaConverters._
                    response.putAll(externalMetadataMap.asJava)
                    response
                } else {
                    TelemetryManager.error("Entry is not found in cassandra for content with identifier: " + identifier)
                    ResponseHandler.ERROR(ResponseCode.RESOURCE_NOT_FOUND, ResponseCode.RESOURCE_NOT_FOUND.code().toString, "Entry is not found in cassandra for content with identifier: " + identifier)
                }
            })
        } catch {
            case e: Exception =>
                e.printStackTrace()
                TelemetryManager.error("Exception Occurred While Reading The Record. | Exception is : " + e.getMessage, e)
                throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name, "Exception Occurred While Reading The Record. Exception is : " + e.getMessage)
        }
    }

    def delete(identifiers: List[String])(implicit ec: ExecutionContext): Future[Response] = {
        val delete = QueryBuilder.delete()
        import scala.collection.JavaConversions._
        val deleteQuery = delete.from(keySpace, table).where(QueryBuilder.in(primaryKey.get(0), seqAsJavaList(identifiers)))
        try {
            val session: Session = CassandraConnector.getSession
            session.executeAsync(deleteQuery).asScala.map(resultSet => {
                if (!resultSet.wasApplied())
                    TelemetryManager.error("Entry is not found in cassandra for content with identifiers: " + identifiers)
                ResponseHandler.OK()
            })
        } catch {
            case e: Exception =>
                TelemetryManager.error("Exception Occurred While Deleting The Record. | Exception is : " + e.getMessage, e)
                throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name, "Exception Occurred While Reading The Record. Exception is : " + e.getMessage)
        }
    }

    def updateMapRecord(identifier: String, columns: List[String], values: List[java.util.Map[String, AnyRef]])(implicit ec: ExecutionContext): Future[Response] = {
        val update = QueryBuilder.update(keySpace, table)
        val clause: Clause = QueryBuilder.eq(primaryKey.get(0), identifier)
        update.where.and(clause)
        for ((column, index) <- columns.view.zipWithIndex)  update.`with`(QueryBuilder.putAll(column, values(index)))
        print("Query for update map record", update)
        try {
            val session: Session = CassandraConnector.getSession
            session.executeAsync(update).asScala.map( resultset => {
                ResponseHandler.OK()
            })
        } catch {
            case e: Exception =>
                e.printStackTrace()
                TelemetryManager.error("Exception Occurred While Saving The Record. | Exception is : " + e.getMessage, e)
                throw new ServerException(ErrorCodes.ERR_SYSTEM_EXCEPTION.name, "Exception Occurred While Saving The Record. Exception is : " + e.getMessage)
        }
    }

    implicit class RichListenableFuture[T](lf: ListenableFuture[T]) {
        def asScala : Future[T] = {
            val p = Promise[T]()
            Futures.addCallback(lf, new FutureCallback[T] {
                def onFailure(t: Throwable): Unit = p failure t
                def onSuccess(result: T): Unit    = p success result
            }, MoreExecutors.directExecutor())
            p.future
        }
    }
}