package org.sunbird.graph.service.util

import org.apache.commons.lang3.StringUtils
import org.apache.tinkerpop.gremlin.driver.{Client, Cluster}
import org.apache.tinkerpop.gremlin.structure.io.binary.TypeSerializerRegistry
import org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1
import org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry
import org.sunbird.common.Platform
import org.sunbird.graph.service.common.{DACConfigurationConstants, GraphOperation}
import org.sunbird.telemetry.logger.TelemetryManager

import java.util

object ClientUtil {

  private val driverMap: util.Map[String, Client] = new util.HashMap[String, Client]
  private val contactUrl: String = if (Platform.config.hasPath("janusContactPoint")) Platform.config.getString("janusContactPoint") else "localhost"
  private val port: Any = if (Platform.config.hasPath("janusPort")) Platform.config.getInt("janusPort") else 8182

  def getGraphClient(graphId: String, graphOperation: GraphOperation): Client = {
    println("getGraphClient ...")
    TelemetryManager.log("Get Driver for Graph Id: " + graphId)
    val driverKey = graphId + DACConfigurationConstants.UNDERSCORE + StringUtils.lowerCase(graphOperation.name)
    TelemetryManager.log("Driver Configuration Key: " + driverKey)
    var client = driverMap.get(driverKey);
    if (null == client) {
      client = loadClient(graphId);
      driverMap.put(driverKey, client);
    }
    client
  }

  def loadClient(graphId: String): Client = {
    println("loadClient ...")
    TelemetryManager.log("Loading driver for Graph Id: " + graphId)
    val typeSerializerRegistry = TypeSerializerRegistry.build.addRegistry(JanusGraphIoRegistry.instance).create
    val cluster = Cluster.build()
      .addContactPoint(contactUrl)
      .port(port.asInstanceOf[Int])
      .serializer(new GraphBinaryMessageSerializerV1(typeSerializerRegistry))
      .create()

    cluster.connect()
  }

  def closeClients(): Unit = {
    val it = driverMap.entrySet.iterator
    while (it.hasNext) {
      val entry = it.next
      val client = entry.getValue
      client.close()
      it.remove()
    }
  }

}
