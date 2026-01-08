error id: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/util/DriverUtil.java
file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/util/DriverUtil.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[168,1]

error in qdox parser
file content:
```java
offset: 6578
uri: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/util/DriverUtil.java
text:
```scala
package org.sunbird.graph.service.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph;
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
		TelemetryManager.log("Get GraphTraversalSource for Graph Id: "+ graphId);
		String driverKey = graphId + DACConfigurationConstants.UNDERSCORE
				+ StringUtils.lowerCase(graphOperation.name());
		TelemetryManager.log("Driver Configuration Key: " + driverKey);

		GraphTraversalSource g = graphTraversalSourceMap.get(driverKey);
		if (null == g) {
			g = loadGraphTraversalSource(graphId, graphOperation, driverKey);
			graphTraversalSourceMap.put(driverKey, g);
		}
		return g;
	}

	private static GraphTraversalSource loadGraphTraversalSource(String graphId, GraphOperation graphOperation, String driverKey) {
		TelemetryManager.log("Loading GraphTraversalSource for Graph Id: "+ graphId);
		String route = getRoute(graphId, graphOperation);
		
		Cluster cluster = Cluster.build()
				.addContactPoint(route.split(":")[0])
				.port(Integer.parseInt(route.split(":")[1]))
				.maxWaitForConnection(30000)
				.maxConnectionPoolSize(DACConfigurationConstants.JANUSGRAPH_MAX_CONNECTION_POOL_SIZE)
				.minConnectionPoolSize(DACConfigurationConstants.JANUSGRAPH_MIN_CONNECTION_POOL_SIZE)
				.create();
		
		clusterMap.put(driverKey, cluster);
		
		GraphTraversalSource g = traversal().withRemote(DriverRemoteConnection.using(cluster, "g"));
		
		registerShutdownHook(g, cluster);
		return g;
	}

	public static void closeConnections() {
		for (Iterator<Map.Entry<String, GraphTraversalSource>> it = graphTraversalSourceMap.entrySet().iterator(); it.hasNext();) {
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
		String baseKey = DACConfigurationConstants.DEFAULT_ROUTE_PROP_PREFIX + StringUtils.lowerCase(graphOperation.name())
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
		
		// Create JanusGraph configuration
		org.apache.commons.configuration.Configuration conf = new org.apache.commons.configuration.BaseConfiguration();
		conf.setProperty("storage.backend", "cql");
		conf.setProperty("storage.hostname", route.split(":")[0]);
		
		// Get additional configuration from Platform config if available
		String storageBackend = Platform.config.hasPath("graph.storage.backend") ?
				Platform.config.getString("graph.storage.backend") : "cql";
		String storageHostname = Platform.config.hasPath("graph.storage.hostname") ?
				Platform.config.getString("graph.storage.hostname") : "localhost";
		
		conf.setProperty("storage.backend", storageBackend);
		conf.setProperty("storage.hostname", storageHostname);
		
		// Create and open JanusGraph
		JanusGraph graph = JanusGraphFactory.open(conf);
		
		TelemetryManager.log("JanusGraph instance loaded for Graph Id: " + graphId);
		return graph;
	}

@@
```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:546)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:677)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:674)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:674)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:912)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:840)
```
#### Short summary: 

QDox parse error in file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/util/DriverUtil.java