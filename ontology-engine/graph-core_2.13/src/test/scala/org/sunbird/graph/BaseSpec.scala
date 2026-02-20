package org.sunbird.graph

import java.io.File
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import org.sunbird.cassandra.CassandraConnector
import org.sunbird.common.Platform
import org.janusgraph.core.JanusGraph
import org.janusgraph.core.JanusGraphFactory
import org.sunbird.graph.service.util.DriverUtil
import java.lang.reflect.Field
import java.util
import scala.collection.JavaConverters._

class BaseSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

    var graph: JanusGraph = _
    var session: com.datastax.driver.core.Session = null
    implicit val oec: OntologyEngineContext = new OntologyEngineContext

    private val script_1 = "CREATE KEYSPACE IF NOT EXISTS content_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val script_2 = "CREATE TABLE IF NOT EXISTS content_store.content_data (content_id text, last_updated_on timestamp,body blob,oldBody blob,screenshots blob,stageIcons blob,externallink text,PRIMARY KEY (content_id));"
    private val script_3 = "CREATE KEYSPACE IF NOT EXISTS hierarchy_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val script_4 = "CREATE TABLE IF NOT EXISTS hierarchy_store.content_hierarchy (identifier text, hierarchy text, relational_metadata text, PRIMARY KEY (identifier));"
    private val script_5 = "CREATE KEYSPACE IF NOT EXISTS category_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val script_6 = "CREATE TABLE IF NOT EXISTS category_store.category_definition_data (identifier text, objectmetadata map<text, text>, forms map<text,text> ,PRIMARY KEY (identifier));"
    private val script_7 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_content_all', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
    private val script_8 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:course_collection_all', {'config': '{}', 'schema': '{\"properties\":{\"trackable\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"},\"autoBatch\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"}},\"default\":{\"enabled\":\"No\",\"autoBatch\":\"No\"},\"additionalProperties\":false},\"additionalCategories\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"default\":\"Textbook\"}},\"userConsent\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}}}'});"
    private val script_9 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:course_content_all',{'config': '{}', 'schema': '{\"properties\":{\"trackable\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"},\"autoBatch\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"}},\"default\":{\"enabled\":\"No\",\"autoBatch\":\"No\"},\"additionalProperties\":false},\"additionalCategories\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"default\":\"Textbook\"}},\"userConsent\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}}}'});"
    private val script_10 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_collection_all', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
    private val script_11 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_content_in.ekstep', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
    private val script_13 = "CREATE KEYSPACE IF NOT EXISTS lock_db WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val script_12 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_collection_in.ekstep', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
    private val script_14 = "CREATE TABLE IF NOT EXISTS lock_db.lock (resourceid text, createdby text, createdon timestamp, creatorinfo text, deviceid text, expiresat timestamp, lockid uuid, resourceinfo text,resourcetype text, PRIMARY KEY (resourceid));"

    def setUpEmbeddedGraph(): Unit = {
        if (null == graph) {
            graph = JanusGraphFactory.build.set("storage.backend", "inmemory").open
            val mgmt = graph.openManagement()
            mgmt.makePropertyKey("keywords").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
            mgmt.makePropertyKey("gradeLevel").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
            mgmt.makePropertyKey("language").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
            mgmt.makePropertyKey("audience").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
            mgmt.makePropertyKey("os").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
            mgmt.makePropertyKey("resourceType").dataType(classOf[String]).cardinality(org.janusgraph.core.Cardinality.LIST).make()
            mgmt.commit()
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
        executeCassandraQuery(script_1, script_2, script_3, script_4, script_5, script_6, script_7, script_8, script_9, script_10, script_11, script_12, script_13, script_14)
    }

    override def afterAll(): Unit = {
        if (null != graph) {
            graph.close()
        }
        if(null != session && !session.isClosed)
            session.close()
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
    }


    def executeCassandraQuery(queries: String*): Unit = {
        if(null == session || session.isClosed){
            session = CassandraConnector.getSession
        }
        for(query <- queries) {
            session.execute(query)
        }
    }

    def createVertex(label: String, properties: Map[String, AnyRef]): Unit = {
        val vertex = graph.asInstanceOf[org.janusgraph.core.JanusGraphTransaction].addVertex(org.apache.tinkerpop.gremlin.structure.T.label, label)
        properties.foreach { case (k, v) => vertex.property(k, v) }
    }

    def createRelationData(): Unit = {
        createVertex("domain", Map[String, AnyRef]("IL_UNIQUE_ID" -> "Num:C3:SC2", "identifier" -> "Num:C3:SC2", "code" -> "Num:C3:SC2", "keywords" -> "Subconcept", "keywords" -> "Class 3", "IL_SYS_NODE_TYPE" -> "DATA_NODE", "subject" -> "numeracy", "channel" -> "in.ekstep", "description" -> "Multiplication", "versionKey" -> "1484389136575", "gradeLevel" -> "Grade 3", "gradeLevel" -> "Grade 4", "IL_FUNC_OBJECT_TYPE" -> "Concept", "name" -> "Multiplication", "lastUpdatedOn" -> "2016-06-15T17:15:45.951+0000", "status" -> "Live"))
        createVertex("domain", Map[String, AnyRef]("IL_UNIQUE_ID" -> "do_11232724509261824014", "code" -> "31d521da-61de-4220-9277-21ca7ce8335c", "previewUrl" -> "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf", "downloadUrl" -> "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11232724509261824014/untitled-content_1504790847410_do_11232724509261824014_2.0.ecar", "channel" -> "in.ekstep", "language" -> util.Arrays.asList("English"), "variants" -> "{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11232724509261824014/untitled-content_1504790848197_do_11232724509261824014_2.0_spine.ecar\",\"size\":890.0}}", "mimeType" -> "application/pdf", "streamingUrl" -> "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf", "idealScreenSize" -> "normal", "createdOn" -> "2017-09-07T13:24:20.720+0000", "contentDisposition" -> "inline", "artifactUrl" -> "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf", "contentEncoding" -> "identity", "lastUpdatedOn" -> "2017-09-07T13:25:53.595+0000", "SYS_INTERNAL_LAST_UPDATED_ON" -> "2017-09-07T13:27:28.417+0000", "contentType" -> "Resource", "lastUpdatedBy" -> "Ekstep", "audience" -> util.Arrays.asList("Student"), "visibility" -> "Default", "os" -> util.Arrays.asList("All"), "IL_SYS_NODE_TYPE" -> "DATA_NODE", "consumerId" -> "e84015d2-a541-4c07-a53f-e31d4553312b", "mediaType" -> "content", "osId" -> "org.ekstep.quiz.app", "lastPublishedBy" -> "Ekstep", "pkgVersion" -> Int.box(2), "versionKey" -> "1504790848417", "license" -> "Creative Commons Attribution (CC BY)", "idealScreenDensity" -> "hdpi", "s3Key" -> "ecar_files/do_11232724509261824014/untitled-content_1504790847410_do_11232724509261824014_2.0.ecar", "size" -> Int.box(4864851), "lastPublishedOn" -> "2017-09-07T13:27:27.410+0000", "createdBy" -> "390", "compatibilityLevel" -> Int.box(4), "IL_FUNC_OBJECT_TYPE" -> "Content", "name" -> "Untitled Content", "publisher" -> "EkStep", "status" -> "Live", "resourceType" -> util.Arrays.asList("Study material")))
        graph.tx().commit()
    }

	def createBulkNodes(): Unit ={
        createVertex("domain", Map[String, AnyRef]("IL_UNIQUE_ID" -> "do_0000123", "identifier" -> "do_0000123", "graphId" -> "domain"))
        createVertex("domain", Map[String, AnyRef]("IL_UNIQUE_ID" -> "do_0000234", "identifier" -> "do_0000234", "graphId" -> "domain"))
        createVertex("domain", Map[String, AnyRef]("IL_UNIQUE_ID" -> "do_0000345", "identifier" -> "do_0000345", "graphId" -> "domain"))
		graph.tx().commit()
	}
}
