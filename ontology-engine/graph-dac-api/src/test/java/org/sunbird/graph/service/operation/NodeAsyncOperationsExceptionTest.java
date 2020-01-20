package org.sunbird.graph.service.operation;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.service.util.DriverUtil;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

public class NodeAsyncOperationsExceptionTest {

	protected static GraphDatabaseService graphDb = null;

	@BeforeClass
	public static void setup() throws Exception {
		startEmbeddedNeo4jWithReadOnly();
	}

	@AfterClass
	public static void finish() throws Exception {
		tearEmbeddedNeo4JSetup();
		DriverUtil.closeDrivers();
	}

	@Test(expected = CompletionException.class)
	public void testAddNodeExpectServerException() throws Exception {
		Node node = new Node("domain", "DATA_NODE", "Content");
		node.setIdentifier("do_00000000113");
		node.setMetadata(new HashMap<String, Object>() {{
			put("status", "Draft");
		}});
		Future<Node> resultFuture = NodeAsyncOperations.addNode("domain", node);
		Node result = Await.result(resultFuture, Duration.apply("30s"));
	}

	@Ignore
	@Test(expected = ServerException.class)
	public void testUpdateNodesExpectServerException() throws Exception {
		List<String> ids = Arrays.asList("do_0000123", "do_0000234");
		Map<String, Object> data = new HashMap<String, Object>() {{
			put("status", "Review");
		}};
		Future<Map<String, Node>> resultFuture = NodeAsyncOperations.updateNodes("domain",ids, data);
		Map<String, Node> result = Await.result(resultFuture, Duration.apply("30s"));
	}


	private static void startEmbeddedNeo4jWithReadOnly() {
		GraphDatabaseSettings.BoltConnector bolt = GraphDatabaseSettings.boltConnector("0");
		graphDb = new GraphDatabaseFactory()
				.newEmbeddedDatabaseBuilder(new File(Platform.config.getString("graph.dir")))
				.setConfig(bolt.type, "BOLT").setConfig(bolt.enabled, "true")
				.setConfig(GraphDatabaseSettings.read_only, "true")
				.setConfig(bolt.address, "localhost:7687").newGraphDatabase();
		registerShutdownHook(graphDb);
	}

	protected static void registerShutdownHook(final GraphDatabaseService graphDb) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					tearEmbeddedNeo4JSetup();
					System.out.println("cleanup Done!!");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private static void tearEmbeddedNeo4JSetup() throws Exception {
		if (null != graphDb)
			graphDb.shutdown();
		Thread.sleep(2000);
		FileUtils.deleteDirectory(new File(Platform.config.getString("graph.dir")));
	}

}
