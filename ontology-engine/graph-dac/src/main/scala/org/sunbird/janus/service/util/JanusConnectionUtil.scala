package org.sunbird.janus.service.util

import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection
import org.apache.tinkerpop.gremlin.driver.{Client, Cluster}
import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.structure.io.binary.TypeSerializerRegistry
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph
import org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1
import org.janusgraph.core.{JanusGraph, JanusGraphFactory}
import org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry
import org.sunbird.telemetry.logger.TelemetryManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
class JanusConnectionUtil {

  var g: GraphTraversalSource = null
  var graph: JanusGraph = null



  @throws[Exception]
  def initialiseGraphClient(): Unit = {
    try {
      if (null == g) g = traversal.withRemote("/Users/admin/Documents/workspace/knowledge-platform/ontology-engine/graph-dac/src/main/conf/remote-graph.properties")
      if (null == graph) graph = JanusGraphFactory.open("/Users/admin/Documents/workspace/knowledge-platform/ontology-engine/graph-dac/src/main/conf/janusgraph-inmemory.properties")

      println("GraphTraversalSource: " + g)
      println("graph: " + graph)
    }
    catch {
      case e: Exception =>
        TelemetryManager.log("JanusConnectionUtil --> Exception: " + e.getCause)
        e.printStackTrace()
    }
  }

  @throws[Exception]
  def getGts: GraphTraversalSource = g

  @throws[Exception]
  def getGraph: JanusGraph = graph

  @throws[Exception]
  def closeClient(): Unit = {
    g.close()
    graph.close()
  }

}
