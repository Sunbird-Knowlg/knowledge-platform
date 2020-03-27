package org.sunbird.async.job

import org.sunbird.async.core.BaseJobConfig

class TestJobConfig extends BaseJobConfig {

    val kafkaInputTopic: String = config.getString("kafka.input.topic")
    val kafkaSuccessTopic: String = config.getString("kafka.output.success.topic")
}
