package org.sunbird.graph

import org.sunbird.common.{HttpUtil, Platform}
import org.sunbird.graph.exception.GraphServiceException
import org.sunbird.graph.external.dial.DialGraphService
import org.sunbird.kafka.client.KafkaClient

class OntologyEngineContext {

    private val graphDBName: String = if(Platform.config.hasPath("graphDatabase")) Platform.config.getConfig("graphDatabase").asInstanceOf[String] else "janusgraph"
    private val dialGraphDB = new DialGraphService
    private val hUtil = new HttpUtil
    private lazy val kfClient = new KafkaClient
    def graphService: BaseGraphService = {
        graphDBName.toLowerCase() match {
            case "neo4j" =>
                new Neo4jGraphService();
            case "janusgraph" =>
                new JanusGraphService();
            case _ =>
                throw new GraphServiceException("Unknown graph type found");
        }
    }

    def dialgraphService: DialGraphService = {
        dialGraphDB
    }
    def extStoreDB = {

    }

    def redis = {

    }

    def httpUtil = hUtil

    def kafkaClient = kfClient
}
