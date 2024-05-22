package org.sunbird.graph

import org.sunbird.common.HttpUtil
import org.sunbird.graph.external.dial.DialGraphService
import org.sunbird.kafka.client.KafkaClient

class OntologyEngineContext {

    private val graphDB = new GraphService
    private val dialGraphDB = new DialGraphService
    private val hUtil = new HttpUtil
    private lazy val kfClient = new KafkaClient
    private lazy val janusGraphDB = new JanusGraphService
    def graphService = {
        graphDB
    }

    def janusGraphService = {
        janusGraphDB
    }

    def dialgraphService = {
        dialGraphDB
    }
    def extStoreDB = {

    }

    def redis = {

    }

    def httpUtil = hUtil

    def kafkaClient = kfClient
}
