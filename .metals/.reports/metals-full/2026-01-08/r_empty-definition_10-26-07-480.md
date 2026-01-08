error id: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/JanusGraphSchemaManager.java:java/time/temporal/ChronoUnit#
file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/JanusGraphSchemaManager.java
empty definition using pc, found symbol in pc: java/time/temporal/ChronoUnit#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 3117
uri: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/JanusGraphSchemaManager.java
text:
```scala
package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.janusgraph.core.Cardinality;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.PropertyKey;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.janusgraph.core.schema.JanusGraphIndex;
import org.janusgraph.graphdb.database.management.ManagementSystem;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

/**
 * JanusGraph schema management operations
 * Handles index creation, constraint enforcement, and schema modifications
 */
public class JanusGraphSchemaManager {

	/**
	 * Create a unique index (constraint) on a property for a given graph
	 * Equivalent to Neo4j: CREATE CONSTRAINT ON (n:graphId) ASSERT n.property IS UNIQUE
	 * 
	 * @param graphId the graph identifier (used as vertex label)
	 * @param indexProperty the property name to index
	 */
	public static void createUniqueIndex(String graphId, String indexProperty) {
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Unique Index' Operation Failed.]");

		if (StringUtils.isBlank(indexProperty))
			throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
					DACErrorMessageConstants.INVALID_PROPERTY + " | ['Create Unique Index' Operation Failed.]");

		JanusGraph graph = DriverUtil.getJanusGraph(graphId);
		TelemetryManager.log("Creating unique index on property: " + indexProperty + " for graph: " + graphId);

		try {
			JanusGraphManagement mgmt = graph.openManagement();
			
			// Check if property key already exists
			PropertyKey propertyKey = mgmt.getPropertyKey(indexProperty);
			if (propertyKey == null) {
				// Create property key with SINGLE cardinality
				propertyKey = mgmt.makePropertyKey(indexProperty)
						.dataType(String.class)
						.cardinality(Cardinality.SINGLE)
						.make();
				TelemetryManager.log("Created property key: " + indexProperty);
			}
			
			// Check if unique index already exists
			String indexName = "unique_" + graphId + "_" + indexProperty;
			JanusGraphIndex existingIndex = mgmt.getGraphIndex(indexName);
			
			if (existingIndex == null) {
				// Create unique composite index
				mgmt.buildIndex(indexName, Vertex.class)
						.addKey(propertyKey)
						.unique()
						.buildCompositeIndex();
				
				TelemetryManager.log("Created unique index: " + indexName + " on property: " + indexProperty);
			} else {
				TelemetryManager.log("Unique index already exists: " + indexName);
			}
			
			mgmt.commit();
			
			// Wait for index to be available (async process in JanusGraph)
			ManagementSystem.awaitGraphIndexStatus(graph, indexName)
					.timeout(60, java.time.temporal.@@ChronoUnit.SECONDS)
					.call();
			
			TelemetryManager.log("'Create Unique Index' Operation Finished. | Index: " + indexName);

		} catch (Exception e) {
			TelemetryManager.error("Error creating unique index", e);
			throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
					"Error! Something went wrong while creating unique index. " + e.getMessage(), e);
		}
	}

	/**
	 * Create a composite index on a property for a given graph
	 * Equivalent to Neo4j: CREATE INDEX ON :graphId(property)
	 * 
	 * @param graphId the graph identifier (used as vertex label)
	 * @param indexProperty the property name to index
	 */
	public static void createCompositeIndex(String graphId, String indexProperty) {
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Composite Index' Operation Failed.]");

		if (StringUtils.isBlank(indexProperty))
			throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
					DACErrorMessageConstants.INVALID_PROPERTY + " | ['Create Composite Index' Operation Failed.]");

		JanusGraph graph = DriverUtil.getJanusGraph(graphId);
		TelemetryManager.log("Creating composite index on property: " + indexProperty + " for graph: " + graphId);

		try {
			JanusGraphManagement mgmt = graph.openManagement();
			
			// Check if property key already exists
			PropertyKey propertyKey = mgmt.getPropertyKey(indexProperty);
			if (propertyKey == null) {
				// Create property key with SINGLE cardinality
				propertyKey = mgmt.makePropertyKey(indexProperty)
						.dataType(String.class)
						.cardinality(Cardinality.SINGLE)
						.make();
				TelemetryManager.log("Created property key: " + indexProperty);
			}
			
			// Check if composite index already exists
			String indexName = "composite_" + graphId + "_" + indexProperty;
			JanusGraphIndex existingIndex = mgmt.getGraphIndex(indexName);
			
			if (existingIndex == null) {
				// Create composite index (non-unique)
				mgmt.buildIndex(indexName, Vertex.class)
						.addKey(propertyKey)
						.buildCompositeIndex();
				
				TelemetryManager.log("Created composite index: " + indexName + " on property: " + indexProperty);
			} else {
				TelemetryManager.log("Composite index already exists: " + indexName);
			}
			
			mgmt.commit();
			
			// Wait for index to be available (async process in JanusGraph)
			ManagementSystem.awaitGraphIndexStatus(graph, indexName)
					.timeout(60, java.time.temporal.ChronoUnit.SECONDS)
					.call();
			
			TelemetryManager.log("'Create Composite Index' Operation Finished. | Index: " + indexName);

		} catch (Exception e) {
			TelemetryManager.error("Error creating composite index", e);
			throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
					"Error! Something went wrong while creating composite index. " + e.getMessage(), e);
		}
	}

	/**
	 * Create a mixed index on a property for full-text search capabilities
	 * Uses external indexing backend (Elasticsearch, Solr, Lucene)
	 * 
	 * @param graphId the graph identifier
	 * @param indexProperty the property name to index
	 * @param indexBackend the backend name (e.g., "search" for Elasticsearch)
	 */
	public static void createMixedIndex(String graphId, String indexProperty, String indexBackend) {
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Create Mixed Index' Operation Failed.]");

		if (StringUtils.isBlank(indexProperty))
			throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
					DACErrorMessageConstants.INVALID_PROPERTY + " | ['Create Mixed Index' Operation Failed.]");

		if (StringUtils.isBlank(indexBackend))
			throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
					"Invalid index backend. | ['Create Mixed Index' Operation Failed.]");

		JanusGraph graph = DriverUtil.getJanusGraph(graphId);
		TelemetryManager.log("Creating mixed index on property: " + indexProperty + " for graph: " + graphId);

		try {
			JanusGraphManagement mgmt = graph.openManagement();
			
			// Check if property key already exists
			PropertyKey propertyKey = mgmt.getPropertyKey(indexProperty);
			if (propertyKey == null) {
				// Create property key
				propertyKey = mgmt.makePropertyKey(indexProperty)
						.dataType(String.class)
						.cardinality(Cardinality.SINGLE)
						.make();
				TelemetryManager.log("Created property key: " + indexProperty);
			}
			
			// Check if mixed index already exists
			String indexName = "mixed_" + graphId + "_" + indexProperty;
			JanusGraphIndex existingIndex = mgmt.getGraphIndex(indexName);
			
			if (existingIndex == null) {
				// Create mixed index with external backend
				mgmt.buildIndex(indexName, Vertex.class)
						.addKey(propertyKey)
						.buildMixedIndex(indexBackend);
				
				TelemetryManager.log("Created mixed index: " + indexName + " on property: " + indexProperty + 
						" with backend: " + indexBackend);
			} else {
				TelemetryManager.log("Mixed index already exists: " + indexName);
			}
			
			mgmt.commit();
			
			TelemetryManager.log("'Create Mixed Index' Operation Finished. | Index: " + indexName);

		} catch (Exception e) {
			TelemetryManager.error("Error creating mixed index", e);
			throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
					"Error! Something went wrong while creating mixed index. " + e.getMessage(), e);
		}
	}

	/**
	 * Drop an index by name
	 * 
	 * @param graphId the graph identifier
	 * @param indexName the name of the index to drop
	 */
	public static void dropIndex(String graphId, String indexName) {
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['Drop Index' Operation Failed.]");

		if (StringUtils.isBlank(indexName))
			throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
					"Invalid index name. | ['Drop Index' Operation Failed.]");

		JanusGraph graph = DriverUtil.getJanusGraph(graphId);
		TelemetryManager.log("Dropping index: " + indexName + " for graph: " + graphId);

		try {
			JanusGraphManagement mgmt = graph.openManagement();
			
			JanusGraphIndex index = mgmt.getGraphIndex(indexName);
			
			if (index != null) {
				// Disable index first
				mgmt.updateIndex(index, org.janusgraph.core.schema.SchemaAction.DISABLE_INDEX).get();
				mgmt.commit();
				
				// Wait for index to be disabled
				ManagementSystem.awaitGraphIndexStatus(graph, indexName)
						.status(org.janusgraph.core.schema.SchemaStatus.DISABLED)
						.timeout(60, java.time.temporal.ChronoUnit.SECONDS)
						.call();
				
				// Drop index after disabling
				mgmt = graph.openManagement();
				index = mgmt.getGraphIndex(indexName);
				mgmt.updateIndex(index, org.janusgraph.core.schema.SchemaAction.DROP_INDEX).get();
				mgmt.commit();
				
				TelemetryManager.log("Dropped index: " + indexName);
			} else {
				TelemetryManager.log("Index not found: " + indexName);
			}

		} catch (Exception e) {
			TelemetryManager.error("Error dropping index", e);
			throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
					"Error! Something went wrong while dropping index. " + e.getMessage(), e);
		}
	}

	/**
	 * List all indices for a graph
	 * 
	 * @param graphId the graph identifier
	 * @return Iterable of index names
	 */
	public static Iterable<String> listIndices(String graphId) {
		if (StringUtils.isBlank(graphId))
			throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
					DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['List Indices' Operation Failed.]");

		JanusGraph graph = DriverUtil.getJanusGraph(graphId);
		TelemetryManager.log("Listing indices for graph: " + graphId);

		try {
			JanusGraphManagement mgmt = graph.openManagement();
			
			Iterable<JanusGraphIndex> indices = mgmt.getGraphIndexes(Vertex.class);
			java.util.List<String> indexNames = new java.util.ArrayList<>();
			
			for (JanusGraphIndex index : indices) {
				indexNames.add(index.name());
			}
			
			mgmt.commit();
			
			TelemetryManager.log("Found " + indexNames.size() + " indices for graph: " + graphId);
			return indexNames;

		} catch (Exception e) {
			TelemetryManager.error("Error listing indices", e);
			throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
					"Error! Something went wrong while listing indices. " + e.getMessage(), e);
		}
	}
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: java/time/temporal/ChronoUnit#