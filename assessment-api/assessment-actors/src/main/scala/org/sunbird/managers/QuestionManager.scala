package org.sunbird.managers

import java.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform
import org.sunbird.common.dto.Request
import org.sunbird.common.exception.ClientException
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.Node
import org.sunbird.graph.nodes.DataNode
import org.sunbird.kafka.client.KafkaClient
import org.sunbird.telemetry.util.LogTelemetryEventUtil

import scala.concurrent.{ExecutionContext, Future}

object QuestionManager {

    private val kfClient = new KafkaClient

    def getValidatedNodeToReview(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        request.put("mode", "edit")
        DataNode.read(request).map(node => {
            if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
                throw new ClientException("ERR_QUESTION_REVIEW", "Questions with visibility Parent, can't be sent for review individually.")
            if (!StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Draft"))
                throw new ClientException("ERR_QUESTION_REVIEW", "Question with status other than Draft can't be sent for review.")
            node
        })
    }

    def getValidatedNodeToPublish(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        request.put("mode", "edit")
        DataNode.read(request).map(node => {
            if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
                throw new ClientException("ERR_QUESTION_PUBLISH", "Questions with visibility Parent, can't be sent for review individually.")
            node
        })
    }

    def getValidatedNodeToRetire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        DataNode.read(request).map(node => {
            if (StringUtils.equalsIgnoreCase("Retired", node.getMetadata.get("status").asInstanceOf[String]))
                throw new ClientException("ERR_QUESTION_RETIRE", "Question with Identifier " + node.getIdentifier + " is already Retired.")
            node
        })
    }

    @throws[Exception]
    def pushInstructionEvent(identifier: String, node: Node): Unit = {
        val actor: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
        val context: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
        val objectData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
        val edata: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
        generateInstructionEventMetadata(actor, context, objectData, edata, node, identifier)
        val beJobRequestEvent: String = LogTelemetryEventUtil.logInstructionEvent(actor, context, objectData, edata)
        val topic: String = Platform.getString("kafka.topics.instruction", "sunbirddev.learning.job.request")
        if (StringUtils.isBlank(beJobRequestEvent)) throw new ClientException("BE_JOB_REQUEST_EXCEPTION", "Event is not generated properly.")
        kfClient.send(beJobRequestEvent, topic)
    }

    def generateInstructionEventMetadata(actor: util.Map[String, AnyRef], context: util.Map[String, AnyRef], objectData: util.Map[String, AnyRef], edata: util.Map[String, AnyRef], node: Node, identifier: String): Unit = {
        val actorId: String = "Publish Samza Job"
        val actorType: String = "System"
        val pdataId: String = "org.ekstep.platform"
        val pdataVersion: String = "1.0"
        val action: String = "publish"

        val metadata: util.Map[String, AnyRef] = node.getMetadata
        val publishType = if (StringUtils.equalsIgnoreCase(metadata.getOrDefault("status", "").asInstanceOf[String], "Unlisted")) "unlisted" else "public"

        actor.put("id", actorId)
        actor.put("type", actorType)

        context.put("channel", metadata.get("channel"))
        val pdata = new util.HashMap[String, AnyRef]
        pdata.put("id", pdataId)
        pdata.put("ver", pdataVersion)
        context.put("pdata", pdata)
        if (Platform.config.hasPath("cloud_storage.env")) {
            val env = Platform.config.getString("cloud_storage.env")
            context.put("env", env)
        }
        if (Platform.config.hasPath("cloud_storage.env")) {
            val env: String = Platform.getString("cloud_storage.env", "dev")
            context.put("env", env)
        }
        objectData.put("id", identifier)
        objectData.put("ver", metadata.get("versionKey"))
        val instructionEventMetadata = new util.HashMap[String, AnyRef]
//        instructionEventMetadata.put("pkgVersion", metadata.get("pkgVersion"))
        instructionEventMetadata.put("mimeType", metadata.get("mimeType"))
        instructionEventMetadata.put("identifier", identifier)
        //        instructionEventMetadata.put("lastPublishedBy", metadata.get("lastPublishedBy"))
        edata.put("action", action)
        edata.put("metadata", instructionEventMetadata)
        edata.put("publish_type", publishType)
//        edata.put("contentType", metadata.get("contentType"))
    }
}
