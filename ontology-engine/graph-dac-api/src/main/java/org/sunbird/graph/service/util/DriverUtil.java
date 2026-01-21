package org.sunbird.graph.service.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.util.ser.GraphSONMessageSerializerV3;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONMapper;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONVersion;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph;
import org.apache.tinkerpop.gremlin.structure.io.binary.TypeSerializerRegistry;
import org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;
import org.sunbird.graph.service.common.DACConfigurationConstants;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;

public class DriverUtil {

	private static Map<String, GraphTraversalSource> graphTraversalSourceMap = new HashMap<>();
	private static Map<String, Cluster> clusterMap = new HashMap<>();
	private static Map<String, JanusGraph> janusGraphMap = new HashMap<>();

	public static GraphTraversalSource getGraphTraversalSource(String graphId, GraphOperation graphOperation) {
		TelemetryManager.log("Get GraphTraversalSource for Graph Id: " + graphId);
		String driverKey = graphId + DACConfigurationConstants.UNDERSCORE
				+ StringUtils.lowerCase(graphOperation.name());
		TelemetryManager.log("Driver Configuration Key: " + driverKey);

		// DEBUG: Print keys
		System.out.println("DEBUG: Requesting key: " + driverKey);
		System.out.println("DEBUG: Available keys: " + graphTraversalSourceMap.keySet());

		GraphTraversalSource g = graphTraversalSourceMap.get(driverKey);
		if (null == g) {
			System.out.println("DEBUG: Key mismatch! Loading remote source.");
			g = loadGraphTraversalSource(graphId, graphOperation, driverKey);
			graphTraversalSourceMap.put(driverKey, g);
		}
		return g;
	}

	private static GraphTraversalSource loadGraphTraversalSource(String graphId, GraphOperation graphOperation,
			String driverKey) {
		TelemetryManager.log("Loading GraphTraversalSource for Graph Id: " + graphId);
		String route = getRoute(graphId, graphOperation);

		// Configure GraphBinary serializer with JanusGraph IoRegistry to support custom
		// types
		// Configure GraphSON serializer with JanusGraph IoRegistry using Builder
		// pattern
		// This ensures that the Serializer adds its default modules (like
		// ResponseMessage serializer)
		// while we add our custom JanusGraph registry.
		GraphSONMapper.Builder builder = GraphSONMapper.build().addRegistry(JanusGraphIoRegistry.instance());
		GraphSONMessageSerializerV3 serializer = new GraphSONMessageSerializerV3(builder);

		Cluster cluster = Cluster.build()
				.addContactPoint(route.split(":")[0])
				.port(Integer.parseInt(route.split(":")[1]))
				.maxWaitForConnection(30000)
				.maxConnectionPoolSize(DACConfigurationConstants.JANUSGRAPH_MAX_CONNECTION_POOL_SIZE)
				.minConnectionPoolSize(DACConfigurationConstants.JANUSGRAPH_MIN_CONNECTION_POOL_SIZE)
				.serializer(serializer)
				.create();

		clusterMap.put(driverKey, cluster);

		GraphTraversalSource g = traversal().withRemote(DriverRemoteConnection.using(cluster, "g"));

		registerShutdownHook(g, cluster);
		return g;
	}

	public static void closeConnections() {
		for (Iterator<Map.Entry<String, GraphTraversalSource>> it = graphTraversalSourceMap.entrySet().iterator(); it
				.hasNext();) {
			Map.Entry<String, GraphTraversalSource> entry = it.next();
			GraphTraversalSource g = entry.getValue();
			try {
				g.close();
			} catch (Exception e) {
				TelemetryManager.error("Error closing GraphTraversalSource", e);
			}
			it.remove();
		}

		for (Iterator<Map.Entry<String, Cluster>> it = clusterMap.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Cluster> entry = it.next();
			Cluster cluster = entry.getValue();
			try {
				cluster.close();
			} catch (Exception e) {
				TelemetryManager.error("Error closing Cluster", e);
			}
			it.remove();
		}

		for (Iterator<Map.Entry<String, JanusGraph>> it = janusGraphMap.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, JanusGraph> entry = it.next();
			JanusGraph graph = entry.getValue();
			try {
				graph.close();
			} catch (Exception e) {
				TelemetryManager.error("Error closing JanusGraph", e);
			}
			it.remove();
		}
	}

	private static void registerShutdownHook(GraphTraversalSource g, Cluster cluster) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				TelemetryManager.log("Closing JanusGraph connections...");
				try {
					if (null != g)
						g.close();
					if (null != cluster)
						cluster.close();
				} catch (Exception e) {
					TelemetryManager.error("Error closing connections", e);
				}
			}
		});
	}

	public static String getRoute(String graphId, GraphOperation graphOperation) {
		// Checking Graph Id for 'null' or 'Empty'
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Graph Id: " + graphId + "]");

		String routeUrl = "localhost:8182";
		String baseKey = DACConfigurationConstants.DEFAULT_ROUTE_PROP_PREFIX
				+ StringUtils.lowerCase(graphOperation.name())
				+ DACConfigurationConstants.DOT;
		if (Platform.config.hasPath(baseKey + graphId)) {
			routeUrl = Platform.config.getString(baseKey + graphId);
		} else if (Platform.config.hasPath(baseKey + DACConfigurationConstants.DEFAULT_JANUSGRAPH_ROUTE_ID)) {
			routeUrl = Platform.config.getString(baseKey + DACConfigurationConstants.DEFAULT_JANUSGRAPH_ROUTE_ID);
		} else {
			TelemetryManager.warn("Graph connection configuration not defined.");
		}
		TelemetryManager.log("Request path for graph: " + graphId + " | URL: " + routeUrl);
		return routeUrl;
	}

	/**
	 * Get a direct JanusGraph instance for schema management operations
	 * This is needed for operations that require JanusGraphManagement API
	 * 
	 * @param graphId the graph identifier
	 * @return JanusGraph instance
	 */
	public static JanusGraph getJanusGraph(String graphId) {
		TelemetryManager.log("Get JanusGraph instance for Graph Id: " + graphId);

		JanusGraph graph = janusGraphMap.get(graphId);
		if (null == graph) {
			graph = loadJanusGraph(graphId);
			janusGraphMap.put(graphId, graph);
		}
		return graph;
	}

	private static JanusGraph loadJanusGraph(String graphId) {
		TelemetryManager.log("Loading JanusGraph instance for Graph Id: " + graphId);
		String route = getRoute(graphId, GraphOperation.WRITE);

		// Get configuration from Platform config if available
		String storageBackend = Platform.config.hasPath("graph.storage.backend")
				? Platform.config.getString("graph.storage.backend")
				: "cql";
		String storageHostname = Platform.config.hasPath("graph.storage.hostname")
				? Platform.config.getString("graph.storage.hostname")
				: route.split(":")[0];

		// Build configuration properties map
		org.apache.commons.configuration2.MapConfiguration conf = new org.apache.commons.configuration2.MapConfiguration(
				new java.util.HashMap<String, Object>() {
					{
						put("storage.backend", storageBackend);
						put("storage.hostname", storageHostname);
						// Enable Transaction Log for CDC
						put("log.learning_graph_events.backend", "default");
					}
				});

		// Create and open JanusGraph
		JanusGraph graph = JanusGraphFactory.open(conf);

		TelemetryManager.log("JanusGraph instance loaded for Graph Id: " + graphId);
		return graph;
	}
}
