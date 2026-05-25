package org.sunbird.content.util

import org.sunbird.common.dto.{Request, Response, ResponseHandler}
import org.sunbird.common.exception.ClientException
import org.sunbird.common.Platform
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.nodes.DataNode
import org.sunbird.graph.utils.ScalaJsonUtils
import org.sunbird.kafka.client.KafkaClient
import org.sunbird.telemetry.logger.TelemetryManager

import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

object EnrichManager {

  private val kfClient = new KafkaClient

  def triggerEnrich(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Response] = {
    val identifiers = request.get("identifiers") match {
      case list: util.List[_] => list.asScala.map(_.toString).filter(_.nonEmpty).toList
      case _ => throw new ClientException("ERR_INVALID_REQUEST", "identifiers must be a non-empty list")
    }
    if (identifiers.isEmpty)
      throw new ClientException("ERR_INVALID_REQUEST", "identifiers list must not be empty")

    val topic = Platform.getString("kafka.publish.request.topic", "sunbirddev.publish.job.request")

    // Read each node to validate existence and derive objectType + mimeType
    val readFutures: List[Future[(String, Option[(String, String)])]] = identifiers.map { id =>
      val readReq = new Request(request)
      readReq.put(ContentConstants.IDENTIFIER, id)
      readReq.put("fields", new util.ArrayList[String]())
      readReq.getContext.put("objectType", "Content")
      readReq.getContext.put("schemaName", "content")
      DataNode.read(readReq).map { node =>
        val mimeType   = Option(node.getMetadata.get("mimeType")).map(_.toString).getOrElse("")
        val objectType = resolveObjectType(mimeType)
        id -> Some((objectType, mimeType))
      }.recover { case _ => id -> None }
    }

    Future.sequence(readFutures).flatMap { results =>
      val notFound  = results.collect { case (id, None) => id }
      val valid     = results.collect { case (id, Some(t)) => (id, t) }

      if (notFound.nonEmpty)
        throw new ClientException("ERR_CONTENT_NOT_FOUND",
          s"Content not found for identifier(s): ${notFound.mkString(", ")}")

      valid.foreach { case (id, (objectType, mimeType)) =>
        val ets = System.currentTimeMillis()
        val mid = s"LP.$ets.${java.util.UUID.randomUUID()}"
        val event = Map(
          "eid"     -> "BE_JOB_REQUEST",
          "ets"     -> ets,
          "mid"     -> mid,
          "actor"   -> Map("id" -> "content-enrich-api", "type" -> "System"),
          "context" -> Map("pdata" -> Map("ver" -> "1.0", "id" -> "org.sunbird.platform")),
          "object"  -> Map("id" -> id, "ver" -> "1.0"),
          "edata"   -> Map(
            "action"   -> "enrich",
            "metadata" -> Map(
              "identifier" -> id,
              "objectType" -> objectType,
              "mimeType"   -> mimeType
            )
          )
        )
        kfClient.send(ScalaJsonUtils.serialize(event), topic)
        TelemetryManager.log(s"Enrich request emitted for $id ($objectType) to $topic")
      }

      Future.successful(
        ResponseHandler.OK()
          .put("count", valid.size)
          .put("identifiers", valid.map(_._1).asJava)
      )
    }
  }

  private def resolveObjectType(mimeType: String): String = mimeType match {
    case "application/vnd.ekstep.content-collection" => "Collection"
    case "application/vnd.sunbird.question"          => "Question"
    case "application/vnd.sunbird.questionset"       => "QuestionSet"
    case _                                           => "Content"
  }
}
