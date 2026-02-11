package org.sunbird.managers.questionset

import java.io.{File, IOException}
import java.lang.reflect.Field
import java.util
import com.datastax.driver.core.{ResultSet, Session}
import org.apache.commons.io.FileUtils
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.janusgraph.core.{JanusGraph, JanusGraphFactory}
import org.scalatest.flatspec.AsyncFlatSpec
import org.sunbird.cassandra.CassandraConnector
import org.sunbird.common.Platform
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.sunbird.graph.service.util.DriverUtil

import scala.jdk.CollectionConverters._

class BaseSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

    var graph: JanusGraph = _
    var session: Session = null

    def setUpEmbeddedGraph(): Unit = {
        if (null == graph) {
            graph = JanusGraphFactory.build.set("storage.backend", "inmemory").open
            val mgmt = graph.openManagement()
            // Define multi-valued properties
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

    def createVertex(label: String, properties: Map[String, AnyRef]): Unit = {
        val vertex = graph.asInstanceOf[org.janusgraph.core.JanusGraphTransaction].addVertex(org.apache.tinkerpop.gremlin.structure.T.label, label)
        properties.foreach { case (k, v) =>
            v match {
                case list: java.util.List[_] => list.asScala.foreach(item => vertex.property(k, item.asInstanceOf[AnyRef]))
                case _ => vertex.property(k, v)
            }
        }
    }

    def createRelationData(): Unit = {
        // Create test data using Gremlin
        createVertex("domain", Map[String, AnyRef]("IL_UNIQUE_ID" -> "Num:C3:SC2", "identifier" -> "Num:C3:SC2", "code" -> "Num:C3:SC2", "keywords" -> util.Arrays.asList("Subconcept", "Class 3"), "IL_SYS_NODE_TYPE" -> "DATA_NODE", "subject" -> "numeracy", "channel" -> "in.ekstep", "description" -> "Multiplication", "versionKey" -> "1484389136575", "gradeLevel" -> util.Arrays.asList("Grade 3", "Grade 4"), "IL_FUNC_OBJECT_TYPE" -> "Concept", "name" -> "Multiplication", "lastUpdatedOn" -> "2016-06-15T17:15:45.951+0000", "status" -> "Live"))

        createVertex("domain", Map[String, AnyRef]("IL_UNIQUE_ID" -> "do_11232724509261824014", "code" -> "31d521da-61de-4220-9277-21ca7ce8335c", "previewUrl" -> "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf", "downloadUrl" -> "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11232724509261824014/untitled-content_1504790847410_do_11232724509261824014_2.0.ecar", "channel" -> "in.ekstep", "language" -> util.Arrays.asList("English"), "variants" -> "{\"spine\":{\"ecarUrl\":\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11232724509261824014/untitled-content_1504790848197_do_11232724509261824014_2.0_spine.ecar\",\"size\":890.0}}", "mimeType" -> "application/pdf", "streamingUrl" -> "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf", "idealScreenSize" -> "normal", "createdOn" -> "2017-09-07T13:24:20.720+0000", "contentDisposition" -> "inline", "artifactUrl" -> "https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf", "contentEncoding" -> "identity", "lastUpdatedOn" -> "2017-09-07T13:25:53.595+0000", "SYS_INTERNAL_LAST_UPDATED_ON" -> "2017-09-07T13:27:28.417+0000", "contentType" -> "Resource", "lastUpdatedBy" -> "Ekstep", "audience" -> util.Arrays.asList("Student"), "visibility" -> "Default", "os" -> util.Arrays.asList("All"), "IL_SYS_NODE_TYPE" -> "DATA_NODE", "consumerId" -> "e84015d2-a541-4c07-a53f-e31d4553312b", "mediaType" -> "content", "osId" -> "org.ekstep.quiz.app", "lastPublishedBy" -> "Ekstep", "pkgVersion" -> 2.asInstanceOf[AnyRef], "versionKey" -> "1504790848417", "license" -> "Creative Commons Attribution (CC BY)", "idealScreenDensity" -> "hdpi", "s3Key" -> "ecar_files/do_11232724509261824014/untitled-content_1504790847410_do_11232724509261824014_2.0.ecar", "size" -> 4864851.asInstanceOf[AnyRef], "lastPublishedOn" -> "2017-09-07T13:27:27.410+0000", "createdBy" -> "390", "compatibilityLevel" -> 4.asInstanceOf[AnyRef], "IL_FUNC_OBJECT_TYPE" -> "Content", "name" -> "Untitled Content", "publisher" -> "EkStep", "status" -> "Live", "resourceType" -> util.Arrays.asList("Study material")))
        graph.tx().commit()
    }

    // TODO: Fix withFixture for AsyncFlatSpec compatibility
    // override protected def withFixture(test: NoArgTest): Outcome = {
    //     val outcome = super.withFixture(test)
    //     outcome
    // }
}
