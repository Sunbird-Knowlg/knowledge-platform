package org.sunbird.graph.health

import java.util.concurrent.CompletionException

import com.datastax.driver.core.Session
import org.sunbird.cache.util.RedisConnector
import org.sunbird.cassandra.CassandraConnector
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.service.operation.NodeAsyncOperations

import scala.collection.JavaConverters
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

object HealthCheckManager extends CassandraConnector with RedisConnector {
    val CONNECTION_SUCCESS: String = "connection check is Successful"
    val CONNECTION_FAILURE: String = "connection check has Failed"


    def checkAllSystemHealth()(implicit ec: ExecutionContext): Future[Response] = {
        val allChecks: ListBuffer[Map[String, Any]] = ListBuffer()
        var overAllHealth: Boolean = true
        if (checkRedisHealth())
            allChecks += generateCheck(true, "redis cache")
        else {
            overAllHealth = false
            allChecks += generateCheck(false, "redis cache")
        }
        if (checkGraphHealth())
            allChecks += generateCheck(true, "graph db")
        else {
            overAllHealth = false
            allChecks += generateCheck(false, "graph db")
        }
        if (checkCassandraHealth())
            allChecks += generateCheck(true, "cassandra db")
        else {
            overAllHealth = false
            allChecks += generateCheck(false, "cassandra db")
        }
        val response = ResponseHandler.OK()
        response.put("checks", convertToJava(allChecks)) // JavaConverters.bufferAsJavaListConverter(allChecks).asJava)
        response.put("healthy", overAllHealth)
        Future(response)
    }

    private def checkGraphHealth()(implicit ec: ExecutionContext): Boolean = {
        try {
            val futureNode = NodeAsyncOperations.upsertRootNode("domain", new Request()) recoverWith { case e: CompletionException => Future(false) }
            futureNode.isCompleted
        } catch {
            case e: Exception => false
        }

    }

    private def checkCassandraHealth(): Boolean = {
        var session: Session = null
        try {
            session = CassandraConnector.getSession
            if (null != session && !session.isClosed) {
                session.execute("SELECT now() FROM system.local")
                true
            } else
                false
        } catch {
            case e: Exception => false
        }
    }

    private def checkRedisHealth(): Boolean = {
        try {
            val jedis = getConnection
            jedis.close()
            true
        } catch {
            case e: Exception => false
        }
    }

    private def generateCheck(healthy: Boolean = false, serviceName: String): Map[String, Any] = healthy match {
        case true => Map("name" -> serviceName, "healthy" -> healthy)
        case false => Map("name" -> serviceName, "healthy" -> healthy, "err" -> "503", "errMsg" -> (serviceName + " service is unavailable"))
    }

    /**
      * Remove this after converting to scala
      *
      * @param allchecks
      * @return
      */
    private def convertToJava(allchecks: ListBuffer[Map[String, Any]]): java.util.List[java.util.Map[String, Any]] = {
        JavaConverters.bufferAsJavaListConverter(allchecks.map(m => JavaConverters.mapAsJavaMapConverter(m).asJava)).asJava
    }
}
