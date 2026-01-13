package org.sunbird.graph.service.operation;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.driver.v1.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.service.util.DriverUtil;
import org.testcontainers.containers.wait.strategy.Wait;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import com.typesafe.config.ConfigFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
//import java.time.Duration;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class NodeAsyncOperationsExceptionTest {

	private static Driver driver = null ;
	private static com.typesafe.config.Config configFile = ConfigFactory.load();
	private static String graphDirectory = configFile.getString("graph.dir");
	private static int hostHttpsPort = 7473;
	private static int hostHttpPort = 7474;
	private static int hostBoltPort = 7687;
	private static FixedHostPortGenericContainer<?> neo4jContainer = new FixedHostPortGenericContainer<>("neo4j:3.5.0");

	protected static Session graphDb = null;
	@BeforeClass
	public static void setup() throws Exception {
		startEmbeddedNeo4jWithReadOnly();
	}

	@AfterClass
	public static void finish() throws Exception {
		if (neo4jContainer != null) {
			neo4jContainer.stop();
		}
//		tearEmbeddedNeo4JSetup();
		DriverUtil.closeDrivers();
	}

	@Ignore
	@Test(expected = CompletionException.class)
	public void testAddNodeExpectServerException() throws Exception {
		Node node = new Node("domain", "DATA_NODE", "Content");
		node.setIdentifier("do_00000000113");
		node.setMetadata(new HashMap<String, Object>() {{
			put("status", "Draft");
		}});
		Future<Node> resultFuture = NodeAsyncOperations.addNode("domain", node);
		Await.result(resultFuture, Duration.apply("30s"));
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

	private static void startEmbeddedNeo4jWithReadOnly() throws InterruptedException {
		if (graphDb == null) {
			File graphDir = new File(graphDirectory);
			if (!graphDir.exists()) {
				graphDir.mkdirs();
			}

			neo4jContainer.withFixedExposedPort(hostHttpsPort, hostHttpsPort);
			neo4jContainer.withFixedExposedPort(hostHttpPort, hostHttpPort);
			neo4jContainer.withFixedExposedPort(hostBoltPort, hostBoltPort);
			neo4jContainer.withEnv("NEO4J_dbms_directories_data", graphDirectory);
			neo4jContainer.withEnv("NEO4J_dbms_security_auth__enabled", "false");
			neo4jContainer.withCommand("neo4j", "console");
			neo4jContainer.withStartupTimeout(java.time.Duration.ofSeconds(60));
			neo4jContainer.waitingFor(Wait.forListeningPort());
			neo4jContainer.start();

			Thread.sleep(20000);

			String boltAddress = "bolt://localhost:" + hostBoltPort;

			Config config = Config.builder()
					.withConnectionTimeout(30, TimeUnit.SECONDS)
					.withMaxTransactionRetryTime(1, TimeUnit.MINUTES)
					.build();

			driver = GraphDatabase.driver(boltAddress, AuthTokens.none(), config);
			graphDb = driver.session();
		}
	}


	protected static void registerShutdownHook(final GraphDatabaseService graphDb) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					tearEmbeddedNeo4JSetup();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private static void tearEmbeddedNeo4JSetup() throws Exception {
		if (null != graphDb)
			graphDb.close();
		Thread.sleep(2000);
		FileUtils.deleteDirectory(new File(Platform.config.getString("graph.dir")));
	}

}
