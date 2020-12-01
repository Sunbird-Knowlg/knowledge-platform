package org.sunbird.managers

import java.util

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.Platform
import org.sunbird.common.dto.{Request, ResponseHandler}
import org.sunbird.common.exception.{ClientException, ResourceNotFoundException}
import org.sunbird.graph.OntologyEngineContext
import org.sunbird.graph.dac.model.{Node, Relation}
import org.sunbird.graph.nodes.DataNode
import org.sunbird.kafka.client.KafkaClient
import org.sunbird.telemetry.logger.TelemetryManager
import org.sunbird.telemetry.util.LogTelemetryEventUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

object QuestionManager {

    def getQuestionNodeToReview(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        request.put("mode", "edit")
        DataNode.read(request).map(node => {
            if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
                throw new ClientException("ERR_QUESTION_REVIEW", "Questions with visibility Parent, can't be sent for review individually.")
            if (!StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Draft"))
                throw new ClientException("ERR_QUESTION_REVIEW", "Question with status other than Draft can't be sent for review.")
            node
        })
    }

    def getQuestionNodeToPublish(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        request.put("mode", "edit")
        DataNode.read(request).map(node => {
            if (StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
                throw new ClientException("ERR_QUESTION_PUBLISH", "Questions with visibility Parent, can't be sent for review individually.")
            node
        })
    }

    def getQuestionNodeToRetire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        DataNode.read(request).map(node => {
            if (StringUtils.equalsIgnoreCase("Retired", node.getMetadata.get("status").asInstanceOf[String]))
                throw new ClientException("ERR_QUESTION_RETIRE", "Question with Identifier " + node.getIdentifier + " is already Retired.")
            node
        })
    }

    @throws[Exception]
    def pushInstructionEvent(identifier: String, node: Node)(implicit oec: OntologyEngineContext): Unit = {
        val actor: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
        val context: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
        val objectData: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
        val edata: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
        generateInstructionEventMetadata(actor, context, objectData, edata, node, identifier)
        val beJobRequestEvent: String = LogTelemetryEventUtil.logInstructionEvent(actor, context, objectData, edata)
        val topic: String = Platform.getString("kafka.topics.instruction", "sunbirddev.learning.job.request")
        if (StringUtils.isBlank(beJobRequestEvent)) throw new ClientException("BE_JOB_REQUEST_EXCEPTION", "Event is not generated properly.")
        oec.kafkaClient.send(beJobRequestEvent, topic)
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


     def getQuestionSetNodeToReview(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        request.put("mode", "edit")
        DataNode.read(request).map(node => {
            if(StringUtils.equalsIgnoreCase(node.getMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent"))
                throw new ClientException("ERR_QUESTION_SET_REVIEW", "Question Set with visibility Parent, can't be sent for review individually.")
            if(!StringUtils.equalsAnyIgnoreCase(node.getMetadata.getOrDefault("status", "").asInstanceOf[String], "Draft"))
                throw new ClientException("ERR_QUESTION_SET_REVIEW", "Question Set with status other than Draft can't be sent for review.")
            node
        })
    }

//    	private def validateQuestionSetHierarchy(request: Request): Unit = {
//          //Get mode=edit if published //Put this validation in configuration by default = true
//    		getQuestionHierarchy(request).map(hierarchyString => {
//    			val hierarchy = if (!hierarchyString.asInstanceOf[String].isEmpty) {
//    				JsonUtils.deserialize(hierarchyString.asInstanceOf[String], classOf[java.util.HashMap[String, AnyRef]])
//    			} else new java.util.HashMap[String, AnyRef]()
//    			val children = hierarchy.getOrDefault("children", new util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[util.ArrayList[java.util.Map[String, AnyRef]]]
//    			validateChildrenRecursive(children)
//    		})
//
//    	}


//    	private def validateChildrenRecursive(children: util.List[util.Map[String, AnyRef]]): Unit = {
//    		children.toList.foreach(content => {
//    			if(!StringUtils.equalsAnyIgnoreCase(content.getOrDefault("visibility", "").asInstanceOf[String], "Parent")
//    				&& !StringUtils.equalsIgnoreCase(content.getOrDefault("status", "").asInstanceOf[String], "Live"))
//    				throw new ClientException("ERR_QUESTION_SET_REVIEW", "Content with identifier: " + content.get("identifier") + "is not Live. Please Publish it.")
//    			validateChildrenRecursive(content.getOrDefault("children", new util.ArrayList[Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]])
//    		})
//    	}

     def validateChildrenRecursive(outRelations: util.List[Relation]): Unit = {
        outRelations.toList.foreach(relation => {
            if(!StringUtils.equalsAnyIgnoreCase(relation.getEndNodeMetadata.getOrDefault("visibility", "").asInstanceOf[String], "Parent")
                && !StringUtils.equalsIgnoreCase(relation.getEndNodeMetadata.getOrDefault("status", "").asInstanceOf[String], "Live"))
                throw new ClientException("ERR_QUESTION_SET_REVIEW", "Content with identifier: " + relation.getEndNodeId + "is not Live. Please Publish it.")
        })
    }

     def getQuestionSetHierarchy(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Any] = {
        oec.graphService.readExternalProps(request, List("hierarchy")).flatMap(response => {
            if (ResponseHandler.checkError(response) && ResponseHandler.isResponseNotFoundError(response)) {
                oec.graphService.readExternalProps(request, List("hierarchy")).map(resp => {
                    resp.getResult.toMap.getOrElse("hierarchy", "{}").asInstanceOf[String]
                }) recover { case e: ResourceNotFoundException => TelemetryManager.log("No hierarchy is present in cassandra for identifier:" + request.get("identifier")) }
            } else Future(response.getResult.toMap.getOrElse("hierarchy", "{}").asInstanceOf[String])
        })
    }

     def getQuestionSetNodeToRetire(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        DataNode.read(request).map(node => {
            if (StringUtils.equalsIgnoreCase("Retired", node.getMetadata.get("status").asInstanceOf[String]))
                throw new ClientException("ERR_QUESTION_SET_RETIRE", "Question with Identifier " + node.getIdentifier + " is already Retired.")
            node
        })
    }

     def getValidatedQuestionSet(request: Request)(implicit ec: ExecutionContext, oec: OntologyEngineContext): Future[Node] = {
        request.put("mode", "edit")
        DataNode.read(request).map(node => {
            if (!StringUtils.equalsIgnoreCase("QuestionSet", node.getObjectType))
                throw new ClientException("ERR_QUESTION_SET_ADD", "Node with Identifier " + node.getIdentifier + " is not a Question Set")
            node
        })
    }

     def getChildIdsFromRelation(node: Node): (List[String], List[String]) = {
        val outRelations: List[Relation] = if (node.getOutRelations != null) node.getOutRelations.asScala.toList else List[Relation]()
        val visibilityIdMap: Map[String, List[String]] = outRelations
            .groupBy(_.getEndNodeMetadata.get("visibility").asInstanceOf[String])
            .mapValues(_.map(_.getEndNodeId).toList)
        (visibilityIdMap.getOrDefault("Public", List()), visibilityIdMap.getOrDefault("Parent", List()))
    }
}
