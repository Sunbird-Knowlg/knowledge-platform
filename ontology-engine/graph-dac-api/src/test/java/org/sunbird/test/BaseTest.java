package org.sunbird.test;

import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.neo4j.driver.v1.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
//import org.neo4j.kernel.configuration.BoltConnector;
import org.sunbird.common.Platform;
import org.sunbird.graph.service.util.DriverUtil;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;


public class BaseTest {

	private static Driver driver = null ;
	private static com.typesafe.config.Config configFile = ConfigFactory.load();
	private static String graphDirectory = configFile.getString("graph.dir");
	private static int hostHttpsPort = 7473;
	private static int hostHttpPort = 7474;
	private static int hostBoltPort = 7687;
	private static FixedHostPortGenericContainer<?> neo4jContainer = new FixedHostPortGenericContainer<>("neo4j:3.5.0");

	protected static Session graphDb = null;

	private static String NEO4J_SERVER_ADDRESS = "localhost:7687";
	private static String GRAPH_DIRECTORY_PROPERTY_KEY = "graph.dir";
	private static String BOLT_ENABLED = "true";

	@AfterClass
	public static void afterTest() throws Exception {
		tearEmbeddedNeo4JSetup();
		DriverUtil.closeDrivers();
	}

	@BeforeClass
	public static void before() throws Exception {
		setupEmbeddedNeo4J();
	}

	private static void registerShutdownHook(final GraphDatabaseService graphDb) {
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
	private static void setupEmbeddedNeo4J() throws InterruptedException {
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

	/*private static void setupEmbeddedNeo4J() throws Exception {
		if (graphDb == null) {
			//BoltConnector bolt = new BoltConnector("0");
			GraphDatabaseSettings.BoltConnector bolt = GraphDatabaseSettings.boltConnector("0");
			graphDb = new GraphDatabaseFactory()
					.newEmbeddedDatabaseBuilder(new File(Platform.config.getString(GRAPH_DIRECTORY_PROPERTY_KEY)))
					.setConfig(bolt.type, "BOLT").setConfig(bolt.enabled, BOLT_ENABLED)
					.setConfig(bolt.address, NEO4J_SERVER_ADDRESS).newGraphDatabase();
			registerShutdownHook(graphDb);
		}
	}*/

	private static void tearEmbeddedNeo4JSetup() throws Exception {
		if (null != graphDb)
			graphDb.close();
		Thread.sleep(2000);
		deleteEmbeddedNeo4j(new File(Platform.config.getString(GRAPH_DIRECTORY_PROPERTY_KEY)));
	}

	private static void deleteEmbeddedNeo4j(final File emDb) throws IOException {
		FileUtils.deleteDirectory(emDb);
	}

	protected static void delay(long time) {
		try {
			Thread.sleep(time);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void createBulkNodes() {
		graphDb.run("UNWIND [{nodeId:'do_0000123'},{nodeId:'do_0000234'},{nodeId:'do_0000345'}] as row with row.nodeId as Id CREATE (n:domain{IL_UNIQUE_ID:Id});");
	}
}
