package org.sunbird.graph.health


import com.datastax.driver.core.Session
import org.sunbird.cache.util.RedisConnector
import org.sunbird.cassandra.CassandraConnector
import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.service.operation.NodeAsyncOperations

import scala.collection.JavaConverters
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object HealthCheckManager extends CassandraConnector with RedisConnector {
    val CONNECTION_SUCCESS: String = "connection check is Successful"
    val CONNECTION_FAILURE: String = "connection check has Failed"
    val redisLabel = "redis cache"
    val cassandraLabel = "cassandra db"
    val graphDBLabel = "graph db"

    def checkAllSystemHealth()(implicit oec: OntologyEngineContext, ec: ExecutionContext): Future[Response] = {

        val allChecks: List[Map[String, Any]] = List(checkRedisHealth(), checkGraphHealth(), checkCassandraHealth())
        val overAllHealth = allChecks.map(check => check.getOrElse("healthy",false).asInstanceOf[Boolean]).foldLeft(true)(_ && _)
        val response = ResponseHandler.OK()
        response.put("checks", allChecks.map(m => JavaConverters.mapAsJavaMapConverter(m).asJava).asJava)
        response.put("healthy", overAllHealth)
        Future(response)
    }

    private def checkGraphHealth()(implicit oec: OntologyEngineContext, ec: ExecutionContext): Map[String, Any] = {
        try {
            val futureNode = oec.graphService.upsertRootNode("domain", new Request())
            if (futureNode.isCompleted) {
                generateCheck(true, graphDBLabel)
            } else {
                generateCheck(false, graphDBLabel)
            }
        } catch {
            case e: Exception => generateCheck(false, graphDBLabel)
        }
    }

    private def checkCassandraHealth(): Map[String, Any] = {
        var session: Session = null
        try {
            session = CassandraConnector.getSession
            if (null != session && !session.isClosed) {
                session.execute("SELECT now() FROM system.local")
                generateCheck(true, cassandraLabel)
            } else
                generateCheck(false, cassandraLabel)
        } catch {
            case e: Exception => generateCheck(false, cassandraLabel)
        }
    }

    private def checkRedisHealth(): Map[String, Any] = {
        try {
            val jedis = getConnection
            jedis.close()
            generateCheck(true, redisLabel)
        } catch {
            case e: Exception => generateCheck(false, redisLabel)
        }
    }

    def generateCheck(healthy: Boolean = false, serviceName: String, err: Option[String] = None, errMsg: Option[String] = None): Map[String, Any] = healthy match {
        case true => Map("name" -> serviceName, "healthy" -> healthy)
        case false => Map("name" -> serviceName, "healthy" -> healthy, "err" -> err.getOrElse("503"), "errMsg" -> errMsg.getOrElse((serviceName + " service is unavailable")))
    }
}
