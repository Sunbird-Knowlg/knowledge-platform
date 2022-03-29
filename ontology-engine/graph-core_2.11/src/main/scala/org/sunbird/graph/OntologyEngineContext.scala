package org.sunbird.graph

import org.sunbird.common.HttpUtil
import org.sunbird.kafka.client.KafkaClient

class OntologyEngineContext {

    private val graphDB = new GraphService
    private val hUtil = new HttpUtil
    private lazy val kfClient = new KafkaClient

    def graphService = {
        graphDB
    }

    def extStoreDB = {

    }

    def redis = {

    }

    def httpUtil = hUtil

    def kafkaClient = kfClient
}
