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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;

public class DriverUtil {

	private static Map<String, GraphTraversalSource> graphTraversalSourceMap = new ConcurrentHashMap<>();
	private static Map<String, Cluster> clusterMap = new ConcurrentHashMap<>();
	private static Map<String, JanusGraph> janusGraphMap = new ConcurrentHashMap<>();

	public static GraphTraversalSource getGraphTraversalSource(String graphId, GraphOperation graphOperation) {
		TelemetryManager.log("Get GraphTraversalSource for Graph Id: " + graphId);
		String driverKey = graphId + DACConfigurationConstants.UNDERSCORE
				+ StringUtils.lowerCase(graphOperation.name());
		TelemetryManager.log("Driver Configuration Key: " + driverKey);

		// DEBUG: Print keys
		System.out.println("DEBUG: Requesting key: " + driverKey);
		System.out.println("DEBUG: Available keys: " + graphTraversalSourceMap.keySet());

		return graphTraversalSourceMap.computeIfAbsent(driverKey, key -> {
			System.out.println("DEBUG: Key mismatch! Loading remote source for key: " + key);
			return loadGraphTraversalSource(graphId, graphOperation, key);
		});
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

		String routeUrl = Platform.config.getString("route.all");
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
		return janusGraphMap.computeIfAbsent(graphId, key -> loadJanusGraph(key));
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
		if ("janusgraph".equals(storageHostname))
			storageHostname = Platform.config.getString("graph.storage.hostname");

		// Build configuration properties map
		// Build configuration properties map
		java.util.Map<String, Object> props = new java.util.HashMap<>();
		props.put("storage.backend", storageBackend);
		props.put("storage.hostname", storageHostname);
		if (Platform.config.hasPath("graph.storage.port")) {
			props.put("storage.port", Platform.config.getString("graph.storage.port"));
		} else if ("localhost".equals(storageHostname)) {
			// Fallback: If forced to localhost and no port specified, default to 9042 but
			// log warning
			// Assuming 9042 is standard, but user mentioned 8082. Rely on config first.
		}
		props.put("log.learning_graph_events.backend", "default");

		if (Platform.config.hasPath("graph")) {
			java.util.Set<java.util.Map.Entry<String, com.typesafe.config.ConfigValue>> entries = Platform.config
					.getConfig("graph").entrySet();
			for (java.util.Map.Entry<String, com.typesafe.config.ConfigValue> entry : entries) {
				props.put(entry.getKey(), entry.getValue().unwrapped());
			}
		}

		if (!props.containsKey("storage.cql.local-datacenter")) {
			props.put("storage.cql.local-datacenter", "datacenter1");
		}

		// Force consistency level to ONE to avoid issues with remote port forwarding
		// (internal IPs unreachable)
		if (!props.containsKey("storage.cql.read-consistency-level")) {
			props.put("storage.cql.read-consistency-level", "ONE");
		}
		if (!props.containsKey("storage.cql.write-consistency-level")) {
			props.put("storage.cql.write-consistency-level", "ONE");
		}

		System.out.println("DEBUG: JanusGraph Config: " + props);

		org.apache.commons.configuration2.MapConfiguration conf = new org.apache.commons.configuration2.MapConfiguration(
				props);

		// Create and open JanusGraph
		JanusGraph graph = JanusGraphFactory.open(conf);

		TelemetryManager.log("JanusGraph instance loaded for Graph Id: " + graphId);
		registerJanusGraphShutdownHook(graph);
		return graph;
	}

	private static void registerJanusGraphShutdownHook(JanusGraph graph) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				TelemetryManager.log("Closing JanusGraph instance...");
				try {
					if (null != graph)
						graph.close();
				} catch (Exception e) {
					TelemetryManager.error("Error closing JanusGraph instance", e);
				}
			}
		});
	}
}
