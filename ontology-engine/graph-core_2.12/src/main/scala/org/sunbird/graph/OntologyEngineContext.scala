package org.sunbird.graph

import org.sunbird.common.HttpUtil
import org.sunbird.graph.external.dial.DialGraphService
import org.sunbird.kafka.client.KafkaClient

class OntologyEngineContext {

    private val graphDB = new GraphService
    private val dialGraphDB = new DialGraphService
    private val hUtil = new HttpUtil
    private lazy val kfClient = new KafkaClient

    def graphService = {
        graphDB
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
