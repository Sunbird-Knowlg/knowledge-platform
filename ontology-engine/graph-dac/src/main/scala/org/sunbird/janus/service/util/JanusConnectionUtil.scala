package org.sunbird.janus.service.util

import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.janusgraph.core.{JanusGraph, JanusGraphFactory}
class JanusConnectionUtil {

  var g: GraphTraversalSource = _
  var graph: JanusGraph = _

  @throws[Exception]
  def initialiseGraphClient(): Unit = {
    if (null == g) g = traversal.withRemote("/Users/admin/Documents/workspace/knowledge-platform/ontology-engine/graph-dac/src/conf/remote-graph.properties")
    if (null == graph) graph = JanusGraphFactory.open("/Users/admin/Documents/workspace/knowledge-platform/ontology-engine/graph-dac/src/conf/janusgraph-inmemory.properties")

    println("GraphTraversalSource: " + g)
    println("graph: " + graph)
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
