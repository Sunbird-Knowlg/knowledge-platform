package org.sunbird.managers.content

import java.io.File
import java.lang.reflect.Field
import java.util
import com.datastax.driver.core.ResultSet
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.apache.tinkerpop.gremlin.structure.Graph
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.janusgraph.core.JanusGraphFactory
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.sunbird.cassandra.CassandraConnector
import org.sunbird.common.Platform
import org.sunbird.graph.service.util.DriverUtil

import scala.jdk.CollectionConverters._

class BaseSpec extends AsyncFlatSpec with BeforeAndAfterAll with BeforeAndAfterEach {

  var graph: Graph = _
  var g: GraphTraversalSource = _
  var session: com.datastax.driver.core.Session = null

  private val script_1 = "CREATE KEYSPACE IF NOT EXISTS content_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
  private val script_2 = "CREATE TABLE IF NOT EXISTS content_store.content_data (content_id text, last_updated_on timestamp,body blob,oldBody blob,stageIcons blob,PRIMARY KEY (content_id));"
  private val script_5 = "CREATE KEYSPACE IF NOT EXISTS category_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
  private val script_6 = "CREATE TABLE IF NOT EXISTS category_store.category_definition_data (identifier text, objectmetadata map<text, text>, forms map<text,text> ,PRIMARY KEY (identifier));"
  private val script_7 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_content_all', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
  private val script_8 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:course_collection_all', {'config': '{}', 'schema': '{\"properties\":{\"trackable\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"},\"autoBatch\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"}},\"default\":{\"enabled\":\"No\",\"autoBatch\":\"No\"},\"additionalProperties\":false},\"additionalCategories\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"default\":\"Textbook\"}},\"userConsent\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}}}'});"
  private val script_9 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:course_content_all',{'config': '{}', 'schema': '{\"properties\":{\"trackable\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"},\"autoBatch\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"}},\"default\":{\"enabled\":\"No\",\"autoBatch\":\"No\"},\"additionalProperties\":false},\"additionalCategories\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"default\":\"Textbook\"}},\"userConsent\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}}}'});"
  private val script_10 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_collection_all', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
  private val script_11 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_content_in.ekstep', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
  private val script_12 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_collection_in.ekstep', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"

  def setUpEmbeddedGraph(): Unit = {
    if (null == graph) {
      graph = JanusGraphFactory.build.set("storage.backend", "inmemory").open
      val mgmt = graph.asInstanceOf[org.janusgraph.core.JanusGraph].openManagement()
      // Define multi-valued properties
      mgmt.makePropertyKey("keywords").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
      mgmt.makePropertyKey("gradeLevel").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
      mgmt.makePropertyKey("language").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
      mgmt.makePropertyKey("audience").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
      mgmt.makePropertyKey("os").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
      mgmt.makePropertyKey("resourceType").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
      mgmt.commit()
      g = graph.traversal

      // Inject into DriverUtil for global access
      val driverUtil = classOf[DriverUtil]
      val field: Field = driverUtil.getDeclaredField("graphTraversalSourceMap")
      field.setAccessible(true)
      val graphTraversalSourceMap = new util.HashMap[String, GraphTraversalSource]()
      graphTraversalSourceMap.put("domain_read", g)
      graphTraversalSourceMap.put("domain_write", g)
      field.set(null, graphTraversalSourceMap)
    }
  }

  def setUpEmbeddedCassandra(): Unit = {
    System.setProperty("cassandra.unsafesystem", "true")
    EmbeddedCassandraServerHelper.startEmbeddedCassandra("/cassandra-unit.yaml", 100000L)
  }

  override def beforeAll(): Unit = {
    setUpEmbeddedGraph()
    setUpEmbeddedCassandra()
    createRelationData()
    executeCassandraQuery(script_1, script_2, script_5, script_6, script_7, script_8, script_9, script_10, script_11, script_12)
  }

  override def afterAll(): Unit = {
    if (null != graph) {
      graph.close()
      DriverUtil.closeConnections()
    }
    if (null != session && !session.isClosed)
      session.close()
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
  }

  def executeCassandraQuery(queries: String*): Unit = {
    if (null == session || session.isClosed) {
      session = CassandraConnector.getSession
    }
    for (query <- queries) {
      session.execute(query)
    }
  }

  def readFromCassandra(query: String): ResultSet = {
    if (null == session || session.isClosed) {
      session = CassandraConnector.getSession
    }
    session.execute(query)
  }

  def createRelationData(): Unit = {
    // Create test data using Gremlin
    g.addV("domain")
      .property("IL_UNIQUE_ID", "Num:C3:SC2")
      .property("identifier", "Num:C3:SC2")
      .property("code", "Num:C3:SC2")
      .property("keywords", "Subconcept")
      .property("keywords", "Class 3")
      .property("IL_SYS_NODE_TYPE", "DATA_NODE")
      .property("subject", "numeracy")
      .property("channel", "in.ekstep")
      .property("description", "Multiplication")
      .property("versionKey", "1484389136575")
      .property("gradeLevel", "Grade 3")
      .property("gradeLevel", "Grade 4")
      .property("IL_FUNC_OBJECT_TYPE", "Concept")
      .property("name", "Multiplication")
      .property("lastUpdatedOn", "2016-06-15T17:15:45.951+0000")
      .property("status", "Live")
      .next()

    g.addV("domain")
      .property("IL_UNIQUE_ID", "do_11232724509261824014")
      .property("code", "31d521da-61de-4220-9277-21ca7ce8335c")
      .property("previewUrl", "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf")
      .property("downloadUrl", "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11232724509261824014/untitled-content_1504790847410_do_11232724509261824014_2.0.ecar")
      .property("channel", "in.ekstep")
      .property("language", "English")
      .property("variants", "{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11232724509261824014/untitled-content_1504790848197_do_11232724509261824014_2.0_spine.ecar\",\"size\":890.0}}")
      .property("mimeType", "application/pdf")
      .property("streamingUrl", "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf")
      .property("idealScreenSize", "normal")
      .property("createdOn", "2017-09-07T13:24:20.720+0000")
      .property("contentDisposition", "inline")
      .property("artifactUrl", "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf")
      .property("contentEncoding", "identity")
      .property("lastUpdatedOn", "2017-09-07T13:25:53.595+0000")
      .property("SYS_INTERNAL_LAST_UPDATED_ON", "2017-09-07T13:27:28.417+0000")
      .property("contentType", "Resource")
      .property("lastUpdatedBy", "Ekstep")
      .property("audience", "Student")
      .property("visibility", "Default")
      .property("os", "All")
      .property("IL_SYS_NODE_TYPE", "DATA_NODE")
      .property("consumerId", "e84015d2-a541-4c07-a53f-e31d4553312b")
      .property("mediaType", "content")
      .property("osId", "org.ekstep.quiz.app")
      .property("lastPublishedBy", "Ekstep")
      .property("pkgVersion", 2)
      .property("versionKey", "1504790848417")
      .property("license", "Creative Commons Attribution (CC BY)")
      .property("idealScreenDensity", "hdpi")
      .property("s3Key", "ecar_files/do_11232724509261824014/untitled-content_1504790847410_do_11232724509261824014_2.0.ecar")
      .property("size", 4864851)
      .property("lastPublishedOn", "2017-09-07T13:27:27.410+0000")
      .property("createdBy", "390")
      .property("compatibilityLevel", 4)
      .property("IL_FUNC_OBJECT_TYPE", "Content")
      .property("name", "Untitled Content")
      .property("publisher", "EkStep")
      .property("status", "Live")
      .property("resourceType", "Study material")
      .next()

    // Add framework nodes
    List("NCF", "K-12", "tpd").foreach { code =>
      g.addV("domain")
        .property("IL_UNIQUE_ID", code)
        .property("owner", "in.ekstep")
        .property("code", code)
        .property("IL_SYS_NODE_TYPE", "DATA_NODE")
        .property("apoc_json", "{\"batch\": true}")
        .property("consumerId", "9393568c-3a56-47dd-a9a3-34da3c821638")
        .property("channel", "in.ekstep")
        .property("description", "NCF ")
        .property("type", "K-12")
        .property("createdOn", "2018-01-23T09:53:50.189+0000")
        .property("versionKey", "1545195552163")
        .property("apoc_text", "APOC")
        .property("appId", "dev.sunbird.portal")
        .property("IL_FUNC_OBJECT_TYPE", "Framework")
        .property("name", "State (Uttar Pradesh)")
        .property("lastUpdatedOn", "2018-12-19T04:59:12.163+0000")
        .property("status", "Live")
        .property("apoc_num", 1)
        .next()
    }

    g.tx().commit()
  }
}
