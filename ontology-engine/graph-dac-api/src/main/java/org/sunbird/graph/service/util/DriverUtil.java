package org.sunbird.graph.service.util;

import org.apache.commons.lang3.StringUtils;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.janusgraph.core.JanusGraphTransaction;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ClientException;
import org.sunbird.graph.service.common.DACConfigurationConstants;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.common.GraphOperation;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.HashMap;
import java.util.Map;

public class DriverUtil {

	private static Map<String, JanusGraph> janusGraphMap = new HashMap<>();

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
	public static synchronized JanusGraph getJanusGraph(String graphId) {
		TelemetryManager.log("Get JanusGraph instance for Graph Id: " + graphId);
		JanusGraph graph = janusGraphMap.get(graphId);
		if (graph == null) {
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

		// Force consistency level to QUORUM to ensure Read-Your-Own-Writes consistency
		if (!props.containsKey("storage.cql.read-consistency-level")) {
			props.put("storage.cql.read-consistency-level", "QUORUM");
		}
		if (!props.containsKey("storage.cql.write-consistency-level")) {
			props.put("storage.cql.write-consistency-level", "QUORUM");
		}

		// Prevent Instance ID collisions when multiple graphs are opened in the same
		// JVM
		// by appending a random suffix.
		// JanusGraph expects a non-negative Short for this configuration.
		// Using random helps avoid collisions with "zombie" instances from previous
		// context reloads.
		props.put("graph.unique-instance-id-suffix", (short) (Math.random() * Short.MAX_VALUE));

		org.apache.commons.configuration2.MapConfiguration conf = new org.apache.commons.configuration2.MapConfiguration(
				props);

		// Create and open JanusGraph
		JanusGraph graph = JanusGraphFactory.open(conf);

		TelemetryManager.log("JanusGraph instance loaded for Graph Id: " + graphId);
		registerJanusGraphShutdownHook(graph);
		return graph;
	}

	public static JanusGraphTransaction beginTransaction(String graphId) {
		JanusGraph graph = getJanusGraph(graphId);
		JanusGraphTransaction tx = graph.buildTransaction().logIdentifier("learning_graph_events").start();
		TelemetryManager
				.log("Initialized JanusGraph Transaction with Log Identifier: learning_graph_events | [Graph Id: "
						+ graphId + "]");
		return tx;
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
