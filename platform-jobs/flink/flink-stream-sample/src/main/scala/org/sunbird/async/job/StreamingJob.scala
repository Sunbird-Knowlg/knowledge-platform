package org.sunbird.async.job

import java.util

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.scala.{OutputTag, StreamExecutionEnvironment}
import org.sunbird.async.function.PrintFunction
import org.sunbird.async.task.BaseStreamTask

/**
 * Skeleton for a Flink Streaming Job.
 *
 * For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="https://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */

class StreamingJob(config: TestJobConfig) extends BaseStreamTask(config) {

  def process = {

    implicit val eventTypeInfo: TypeInformation[util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[util.Map[String, AnyRef]])
    implicit val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(config.checkpointingInterval)

    try {

      val kafkaConsumer = createKafkaStreamConsumer(config.kafkaInputTopic)

      val dataStream = env.addSource(kafkaConsumer).process(new PrintFunction(config, "input-task")).setParallelism(1).name("kafka-input")
      val firstTaskStream = dataStream.process(new PrintFunction(config, "first-task")).name("first-task").setParallelism(2).name("first-task")
      val secondTaskStream = firstTaskStream.process(new PrintFunction(config, "second-task")).name("second-task").setParallelism(1).name("second-task")
      secondTaskStream.print()
      env.execute("TestStreamingJob")
    } catch {
      case ex: Exception =>
            ex.printStackTrace()
    }

  }
}

object StreamingJob {

  val config = new TestJobConfig
  def apply(): StreamingJob = new StreamingJob(config)

  def main(args: Array[String]): Unit = {
    StreamingJob.apply().process
  }
}
