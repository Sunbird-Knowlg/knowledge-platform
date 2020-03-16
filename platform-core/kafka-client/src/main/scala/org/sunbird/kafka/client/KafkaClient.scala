package org.sunbird.kafka.client

import java.util.Properties
import org.apache.kafka.clients.consumer.{Consumer, ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.{KafkaProducer, Producer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.{LongDeserializer, LongSerializer, StringDeserializer, StringSerializer}
import org.sunbird.common.Platform
import org.sunbird.common.exception.ClientException
import org.sunbird.telemetry.logger.TelemetryManager


class KafkaClient {

	private val BOOTSTRAP_SERVERS = Platform.getString("kafka.urls","localhost:9092")
	private val producer = createProducer()
	private val consumer = createConsumer()

	protected def getProducer: Producer[Long, String] = producer
	protected def getConsumer: Consumer[Long, String] = consumer

	@throws[Exception]
	def send(event: String, topic: String): Unit = {
		if (!Platform.getBoolean("kafka.topic.send.enable",true)) return
		if (validate(topic))
			getProducer.send(new ProducerRecord[Long, String](topic, event))
		else {
			TelemetryManager.error("Topic with name: " + topic + ", does not exists.")
			throw new ClientException("TOPIC_NOT_FOUND_EXCEPTION", "Topic with name: " + topic + ", does not exists.")
		}
	}

	@throws[Exception]
	def validate(topic: String): Boolean = {
		val topics = getConsumer.listTopics
		topics.keySet.contains(topic)
	}

	private def createProducer(): KafkaProducer[Long, String] = {
		new KafkaProducer[Long, String](new Properties() {
			{
				put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
				put(ProducerConfig.CLIENT_ID_CONFIG, "KafkaClientProducer")
				put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[LongSerializer].getName)
				put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
			}
		})
	}

	private def createConsumer(): KafkaConsumer[Long, String] = {
		new KafkaConsumer[Long, String](new Properties() {
			{
				put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
				put(ConsumerConfig.CLIENT_ID_CONFIG, "KafkaClientConsumer")
				put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[LongDeserializer].getName)
				put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
			}
		})
	}

}
