package org.sunbird.kafka.test

import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class KafkaBaseTest extends FlatSpec with Matchers with BeforeAndAfterAll with EmbeddedKafka {

	implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)

	override def beforeAll(): Unit = {
		try {
			EmbeddedKafka.start()
		} catch {
			case e: Exception => e.printStackTrace()
		}
	}

	override def afterAll(): Unit = {
		try {
			EmbeddedKafka.stop()
		} catch {
			case e: Exception => e.printStackTrace()
		}
	}

	def createTopic(topicName: String): Unit = {
		EmbeddedKafka.createCustomTopic(topicName)
	}
}
