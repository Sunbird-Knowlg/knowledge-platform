package org.sunbird.janusgraph.cdc;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Properties;

public class KafkaEventProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaEventProducer.class);
    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final ObjectMapper mapper = new ObjectMapper();

    public KafkaEventProducer(Map<String, String> config) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getOrDefault("kafka.bootstrap.servers", "localhost:9092"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put("acks", "all");
        props.put("retries", 0);
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);

        this.topic = config.getOrDefault("kafka.topics.graph.event", "sunbirddev.learning.graph.events");
        this.producer = new KafkaProducer<>(props);
    }

    public void publish(Map<String, Object> event) {
        try {
            String json = mapper.writeValueAsString(event);
            producer.send(new ProducerRecord<>(topic, json));
        } catch (Exception e) {
            LOGGER.error("Error publishing event to Kafka", e);
        }
    }

    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
