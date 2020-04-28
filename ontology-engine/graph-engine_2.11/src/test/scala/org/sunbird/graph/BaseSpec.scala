package org.sunbird.graph

import java.io.{File, IOException}

import com.datastax.driver.core.Session
import org.apache.commons.io.FileUtils
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.factory.GraphDatabaseSettings.Connector.ConnectorType
import org.neo4j.kernel.configuration.BoltConnector
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import org.sunbird.cassandra.CassandraConnector
import org.sunbird.common.Platform

class BaseSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

    var graphDb: GraphDatabaseService = null
    var session: Session = null
    implicit val oec: OntologyEngineContext = new OntologyEngineContext

    private val script_1 = "CREATE KEYSPACE IF NOT EXISTS content_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val script_2 = "CREATE TABLE IF NOT EXISTS content_store.content_data (content_id text, last_updated_on timestamp,body blob,oldBody blob,screenshots blob,stageIcons blob,externallink text,PRIMARY KEY (content_id));"


    def setUpEmbeddedNeo4j(): Unit = {
        if(null == graphDb) {
            val bolt: BoltConnector = new BoltConnector("0")
            println("GraphDB : " + Platform.config.getString("graph.dir"))
            graphDb = new GraphDatabaseFactory()
                            .newEmbeddedDatabaseBuilder(new File(Platform.config.getString("graph.dir")))
                            .setConfig(bolt.`type`, ConnectorType.BOLT.name())
                    .setConfig(bolt.enabled, "true").setConfig(bolt.listen_address, "localhost:7687").newGraphDatabase
            registerShutdownHook(graphDb)
        }
    }

    private def registerShutdownHook(graphDb: GraphDatabaseService): Unit = {
        Runtime.getRuntime.addShutdownHook(new Thread() {
            override def run(): Unit = {
                try {
                    tearEmbeddedNeo4JSetup
                    System.out.println("cleanup Done!!")
                } catch {
                    case e: Exception =>
                        e.printStackTrace()
                }
            }
        })
    }


    @throws[Exception]
    private def tearEmbeddedNeo4JSetup(): Unit = {
        if (null != graphDb) graphDb.shutdown
        Thread.sleep(2000)
        deleteEmbeddedNeo4j(new File(Platform.config.getString("graph.dir")))
    }

    private def deleteEmbeddedNeo4j(emDb: File): Unit = {
        try{
            if(emDb.exists() && emDb.isDirectory)
                FileUtils.deleteDirectory(emDb)
        }catch{
            case e: Exception =>
                e.printStackTrace()
        }
    }


    def setUpEmbeddedCassandra(): Unit = {
        System.setProperty("cassandra.unsafesystem", "true")
        EmbeddedCassandraServerHelper.startEmbeddedCassandra("/cassandra-unit.yaml", 100000L)
    }

    override def beforeAll(): Unit = {
        tearEmbeddedNeo4JSetup()
        setUpEmbeddedNeo4j()
        setUpEmbeddedCassandra()
        executeCassandraQuery(script_1, script_2)
    }

    override def afterAll(): Unit = {
        tearEmbeddedNeo4JSetup()
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

    def createRelationData(): Unit = {
        graphDb.execute("UNWIND [{identifier:\"Num:C3:SC2\",code:\"Num:C3:SC2\",keywords:[\"Subconcept\",\"Class 3\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",subject:\"numeracy\",channel:\"in.ekstep\",description:\"Multiplication\",versionKey:\"1484389136575\",gradeLevel:[\"Grade 3\",\"Grade 4\"],IL_FUNC_OBJECT_TYPE:\"Concept\",name:\"Multiplication\",lastUpdatedOn:\"2016-06-15T17:15:45.951+0000\",IL_UNIQUE_ID:\"Num:C3:SC2\",status:\"Live\"}, {code:\"31d521da-61de-4220-9277-21ca7ce8335c\",previewUrl:\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf\",downloadUrl:\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11232724509261824014/untitled-content_1504790847410_do_11232724509261824014_2.0.ecar\",channel:\"in.ekstep\",language:[\"English\"],variants:\"{\\\"spine\\\":{\\\"ecarUrl\\\":\\\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/ecar_files/do_11232724509261824014/untitled-content_1504790848197_do_11232724509261824014_2.0_spine.ecar\\\",\\\"size\\\":890.0}}\",mimeType:\"application/pdf\",streamingUrl:\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf\",idealScreenSize:\"normal\",createdOn:\"2017-09-07T13:24:20.720+0000\",contentDisposition:\"inline\",artifactUrl:\"https://ekstep-public-dev.s3-ap-south-1.amazonaws.com/assets/do_11232724509261824014/object-oriented-javascript.pdf\",contentEncoding:\"identity\",lastUpdatedOn:\"2017-09-07T13:25:53.595+0000\",SYS_INTERNAL_LAST_UPDATED_ON:\"2017-09-07T13:27:28.417+0000\",contentType:\"Resource\",lastUpdatedBy:\"Ekstep\",audience:[\"Learner\"],visibility:\"Default\",os:[\"All\"],IL_SYS_NODE_TYPE:\"DATA_NODE\",consumerId:\"e84015d2-a541-4c07-a53f-e31d4553312b\",mediaType:\"content\",osId:\"org.ekstep.quiz.app\",lastPublishedBy:\"Ekstep\",pkgVersion:2,versionKey:\"1504790848417\",license:\"Creative Commons Attribution (CC BY)\",idealScreenDensity:\"hdpi\",s3Key:\"ecar_files/do_11232724509261824014/untitled-content_1504790847410_do_11232724509261824014_2.0.ecar\",size:4864851,lastPublishedOn:\"2017-09-07T13:27:27.410+0000\",createdBy:\"390\",compatibilityLevel:4,IL_FUNC_OBJECT_TYPE:\"Content\",name:\"Untitled Content\",publisher:\"EkStep\",IL_UNIQUE_ID:\"do_11232724509261824014\",status:\"Live\",resourceType:[\"Study material\"]}] as row CREATE (n:domain) SET n += row")
    }

	def createBulkNodes(): Unit ={
		graphDb.execute("UNWIND [{nodeId:'do_0000123'},{nodeId:'do_0000234'},{nodeId:'do_0000345'}] as row with row.nodeId as Id CREATE (n:domain{IL_UNIQUE_ID:Id});")
	}

    def executeNeo4jQuery(query: String): Unit = {
        graphDb.execute(query)
    }
}
