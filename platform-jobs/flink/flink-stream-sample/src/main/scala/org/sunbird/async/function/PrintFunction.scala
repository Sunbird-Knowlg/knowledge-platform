package org.sunbird.async.function

import java.util

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.streaming.api.scala.OutputTag
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory
import org.sunbird.async.job.TestJobConfig

class PrintFunction(config: TestJobConfig, taskName: String)(implicit val eventTypeInfo: TypeInformation[util.Map[String, AnyRef]]) extends ProcessFunction[util.Map[String, AnyRef], util.Map[String, AnyRef]] {

    private[this] val logger = LoggerFactory.getLogger(this.getClass.getCanonicalName+"."+taskName)

//    lazy val outputTag: OutputTag[util.Map[String, AnyRef]] = new OutputTag[util.Map[String, AnyRef]](id = "kafka-output")

    override def processElement(i: util.Map[String, AnyRef],
                                context: ProcessFunction[util.Map[String, AnyRef], util.Map[String, AnyRef]]#Context,
                                collector: Collector[util.Map[String, AnyRef]]): Unit = {
        logger.info("Event reached to PrintFunction: " + taskName)
        Thread.sleep(1000)
        logger.info("Triggered print function for event: " + i.get("mid") + " :via: " + taskName + " :at: " + System.currentTimeMillis)
        collector.collect(i)
    }

}
