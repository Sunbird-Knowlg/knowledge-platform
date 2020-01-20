package org.sunbird.test;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
//import org.neo4j.kernel.configuration.BoltConnector;
import org.sunbird.common.Platform;
import org.sunbird.graph.service.util.DriverUtil;

import java.io.File;
import java.io.IOException;


public class BaseTest {

	protected static GraphDatabaseService graphDb = null;

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
					System.out.println("cleanup Done!!");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private static void setupEmbeddedNeo4J() throws Exception {
		if (graphDb == null) {
			//BoltConnector bolt = new BoltConnector("0");
			GraphDatabaseSettings.BoltConnector bolt = GraphDatabaseSettings.boltConnector("0");
			graphDb = new GraphDatabaseFactory()
					.newEmbeddedDatabaseBuilder(new File(Platform.config.getString(GRAPH_DIRECTORY_PROPERTY_KEY)))
					.setConfig(bolt.type, "BOLT").setConfig(bolt.enabled, BOLT_ENABLED)
					.setConfig(bolt.address, NEO4J_SERVER_ADDRESS).newGraphDatabase();
			registerShutdownHook(graphDb);
		}
	}

	private static void tearEmbeddedNeo4JSetup() throws Exception {
		if (null != graphDb)
			graphDb.shutdown();
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
		graphDb.execute("UNWIND [{nodeId:'do_0000123'},{nodeId:'do_0000234'},{nodeId:'do_0000345'}] as row with row.nodeId as Id CREATE (n:domain{IL_UNIQUE_ID:Id});");
	}
}
