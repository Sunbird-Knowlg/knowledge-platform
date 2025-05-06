package org.sunbird.graph.external.dial

import com.datastax.driver.core.Session
import com.datastax.driver.core.querybuilder.{Insert, QueryBuilder}
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture, MoreExecutors}
import org.sunbird.cassandra.{CassandraConnector, CassandraStore}
import org.sunbird.common.JsonUtils
import org.sunbird.common.dto.{Response, ResponseHandler}
import org.sunbird.common.exception.{ErrorCodes, ServerException}
import org.sunbird.telemetry.logger.TelemetryManager

import java.sql.Timestamp
import java.util
import java.util.{Date, UUID}
import scala.concurrent.{ExecutionContext, Future, Promise}

// $COVERAGE-OFF$ Disabling scoverage
class DialStore(keySpace: String, table: String, primaryKey: java.util.List[String]) extends CassandraStore(keySpace, table, primaryKey) {

  def insert(request: util.Map[String, AnyRef], propsMapping: Map[String, String])(implicit ec: ExecutionContext): Future[Response] = {
    val insertQuery: Insert = QueryBuilder.insertInto(keySpace, table)
    val identifier = request.get("identifier")

    if (identifier.isInstanceOf[String]) insertQuery.value(primaryKey.get(0), UUID.fromString(identifier.asInstanceOf[String]))
    else insertQuery.value(primaryKey.get(0), identifier)

    request.remove("identifier")
    request.remove("last_updated_on")
    if (propsMapping.keySet.contains("last_updated_on"))
      insertQuery.value("last_updated_on", new Timestamp(new Date().getTime))
    import scala.collection.JavaConverters._
    for ((key, value) <- request.asScala) {
      propsMapping.getOrElse(key, "") match {
        case "blob" => value match {
          case value: String => insertQuery.value(key, QueryBuilder.fcall("textAsBlob", value))
          case _ => insertQuery.value(key, QueryBuilder.fcall("textAsBlob", JsonUtils.serialize(value)))
        }
        case "string" => request.getOrDefault(key, "") match {
          case value: String => insertQuery.value(key, value)
          case _ => insertQuery.value(key, JsonUtils.serialize(request.getOrDefault(key, "")))
        }
        case _ => insertQuery.value(key, value)
      }
    }
    try {
      val session: Session = CassandraConnector.getSession
      val sessionExecute = session.executeAsync(insertQuery)
      sessionExecute.asScala.map(resultset => {
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
    def asScala: Future[T] = {
      val p = Promise[T]()
      Futures.addCallback(lf, new FutureCallback[T] {
        def onFailure(t: Throwable): Unit = p failure t

        def onSuccess(result: T): Unit = p success result
      }, MoreExecutors.directExecutor())
      p.future
    }
  }
}
// $COVERAGE-ON$ Enabling scoverage
