package org.sunbird.graph.service.operation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphEdge;
import org.janusgraph.core.JanusGraphTransaction;
import org.janusgraph.core.JanusGraphVertex;
import org.sunbird.common.dto.Property;
import org.sunbird.common.dto.Request;
import org.sunbird.common.exception.ClientException;
import org.sunbird.common.exception.MiddlewareException;
import org.sunbird.common.exception.ResourceNotFoundException;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.model.Filter;
import org.sunbird.graph.dac.model.MetadataCriterion;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;
import org.sunbird.graph.dac.model.RelationCriterion;
import org.sunbird.graph.dac.model.SearchConditions;
import org.sunbird.graph.dac.model.SearchCriteria;
import org.sunbird.graph.dac.model.Sort;
import org.sunbird.graph.dac.util.JanusGraphNodeUtil;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Search async operations using JanusGraph Native API
 */
public class SearchAsyncOperations {

    /**
     * Helper class to hold vertex and its transaction together.
     */
    private static class VertexWithTransaction {
        final JanusGraphVertex vertex;
        final JanusGraphTransaction transaction;
        
        VertexWithTransaction(JanusGraphVertex vertex, JanusGraphTransaction transaction) {
            this.vertex = vertex;
            this.transaction = transaction;
        }
    }

    /**
     * Helper method to get a vertex by its unique identifier with its transaction.
     * Extracts common logic used by getNodeByUniqueId and getNodeProperty.
     *
     * @param graphId the graph id
     * @param identifier the node unique identifier
     * @param operationName name of the operation for error messages
     * @return VertexWithTransaction containing the found vertex and its transaction
     * @throws ClientException if graphId or identifier is invalid
     * @throws ResourceNotFoundException if node is not found
     */
    private static VertexWithTransaction getVertexByUniqueId(String graphId, String identifier, String operationName) {
        if (StringUtils.isBlank(graphId))
            throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                    DACErrorMessageConstants.INVALID_GRAPH_ID + " | ['" + operationName + "' Operation Failed.]");

        if (StringUtils.isBlank(identifier))
            throw new ClientException(DACErrorCodeConstants.INVALID_IDENTIFIER.name(),
                    DACErrorMessageConstants.INVALID_IDENTIFIER + " | ['" + operationName + "' Operation Failed.]");

        JanusGraph graph = DriverUtil.getJanusGraph(graphId);
        JanusGraphTransaction tx = graph.newTransaction();
        TelemetryManager.log("JanusGraph Transaction Initialized. | [Graph Id: " + graphId + "]");

        Iterator<JanusGraphVertex> vertexIter = tx.query()
                .has(SystemProperties.IL_UNIQUE_ID.name(), identifier)
                .has("graphId", graphId)
                .vertices().iterator();

        if (!vertexIter.hasNext()) {
            throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name(),
                    "Node not found with id: " + identifier + " | ['" + operationName + "' Operation Failed.]");
        }

        return new VertexWithTransaction(vertexIter.next(), tx);
    }

    /**
     * Get a node by its unique identifier.
     *
     * @param graphId the graph id
     * @param nodeId  the node unique identifier
     * @param getTags whether to fetch tags (relations)
     * @param request the request
     * @return Future<Node> with the found node
     */
    public static Future<Node> getNodeByUniqueId(String graphId, String nodeId, Boolean getTags, Request request) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            JanusGraphTransaction tx = null;
            try {
                VertexWithTransaction vt = getVertexByUniqueId(graphId, nodeId, "Get Node By Unique Id");
                tx = vt.transaction;
                JanusGraphVertex vertex = vt.vertex;
                
                Node node;
                if (getTags != null && getTags) {
                    // Get node with relations (tags)
                    node = JanusGraphNodeUtil.getNode(graphId, vertex);
                } else {
                    // Get node without relations
                    node = JanusGraphNodeUtil.getNodeWithoutRelations(graphId, vertex);
                }

                tx.commit();
                TelemetryManager.log("'Get Node By Unique Id' Operation Finished. | Node ID: " + nodeId);
                return node;

            } catch (MiddlewareException e) {
                if (null != tx)
                    tx.rollback();
                throw e;
            } catch (Exception e) {
                if (null != tx)
                    tx.rollback();
                TelemetryManager.error(
                        "Error getting node by unique id | Node ID: " + nodeId + " | Error: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching node. ", e);
            }
        }));
    }

    /**
     * Get a specific property of a node.
     *
     * @param graphId    the graph id
     * @param identifier the node identifier
     * @param property   the property name to fetch
     * @return Future<Property> with the property value
     */
    public static Future<Property> getNodeProperty(String graphId, String identifier, String property) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(property))
                throw new ClientException(DACErrorCodeConstants.INVALID_PROPERTY.name(),
                        "Invalid property name. | ['Get Node Property' Operation Failed.]");

            JanusGraphTransaction tx = null;
            try {
                VertexWithTransaction vt = getVertexByUniqueId(graphId, identifier, "Get Node Property");
                tx = vt.transaction;
                JanusGraphVertex vertex = vt.vertex;

                // Check if property exists
                if (!vertex.keys().contains(property)) {
                    throw new ResourceNotFoundException(DACErrorCodeConstants.NOT_FOUND.name(),
                            "Property '" + property + "' not found on node: " + identifier);
                }

                Object value = vertex.property(property).value();
                Property prop = new Property(property, value);

                tx.commit();
                TelemetryManager.log(
                        "'Get Node Property' Operation Finished. | Node ID: " + identifier + ", Property: " + property);
                return prop;

            } catch (MiddlewareException e) {
                if (null != tx)
                    tx.rollback();
                throw e;
            } catch (Exception e) {
                if (null != tx)
                    tx.rollback();
                TelemetryManager.error("Error getting node property: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching node property. ", e);
            }
        }));
    }

    /**
     * Get multiple nodes by their unique identifiers using search criteria.
     * Replaced GremlinQueryBuilder with Native API implementation.
     *
     * @param graphId        the graph id
     * @param searchCriteria the search criteria containing node identifiers
     * @return Future<List < Node>> list of found nodes
     */
    public static Future<List<Node>> getNodeByUniqueIds(String graphId, SearchCriteria searchCriteria) {
        return FutureConverters.toScala(CompletableFuture.supplyAsync(() -> {
            if (StringUtils.isBlank(graphId))
                throw new ClientException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                        DACErrorMessageConstants.INVALID_GRAPH_ID
                                + " | ['Get Nodes By Search Criteria' Operation Failed.]");

            if (null == searchCriteria)
                throw new ClientException(DACErrorCodeConstants.INVALID_CRITERIA.name(),
                        DACErrorMessageConstants.INVALID_SEARCH_CRITERIA
                                + " | ['Get Nodes By Search Criteria' Operation Failed.]");

            JanusGraphTransaction tx = null;
            try {
                JanusGraph graph = DriverUtil.getJanusGraph(graphId);
                tx = graph.newTransaction();
                TelemetryManager.log("JanusGraph Transaction Initialized. | [Graph Id: " + graphId + "]");

                // Native Search Implementation
                List<Node> nodes = executeNativeSearch(tx, graphId, searchCriteria);

                tx.commit();
                TelemetryManager
                        .log("'Get Nodes By Search Criteria' Operation Finished. | Found: " + nodes.size() + " nodes");
                return nodes;

            } catch (MiddlewareException e) {
                if (null != tx)
                    tx.rollback();
                throw e;
            } catch (Exception e) {
                if (null != tx)
                    tx.rollback();
                TelemetryManager.error("Error getting nodes by search criteria: " + e.getMessage(), e);
                throw new ServerException(DACErrorCodeConstants.SERVER_ERROR.name(),
                        "Error! Something went wrong while fetching nodes. ", e);
            }
        }));
    }

    private static List<Node> executeNativeSearch(JanusGraphTransaction tx, String graphId, SearchCriteria sc) {
        // 1. Build Base Query (AND conditions)
        org.janusgraph.core.JanusGraphQuery query = tx.query().has("graphId", graphId);

        // Extract primary filters to optimize query
        List<String> ids = new ArrayList<>();
        extractIdsFromMetadata(sc.getMetadata(), ids);

        if (StringUtils.isNotBlank(sc.getNodeType())) {
            query.has(SystemProperties.IL_SYS_NODE_TYPE.name(), sc.getNodeType());
        }
        if (StringUtils.isNotBlank(sc.getObjectType())) {
            query.has(SystemProperties.IL_FUNC_OBJECT_TYPE.name(), sc.getObjectType());
        }

        // Execute Base Query
        Iterable<JanusGraphVertex> vertices;
        if (!ids.isEmpty()) {
            // Optimization: If IDs are known, fetch them directly
            List<JanusGraphVertex> found = new ArrayList<>();
            for (String id : ids) {
                Iterator<JanusGraphVertex> iter = tx.query()
                        .has(SystemProperties.IL_UNIQUE_ID.name(), id)
                        .has("graphId", graphId)
                        .vertices().iterator();
                while (iter.hasNext())
                    found.add(iter.next());
            }
            vertices = found;
        } else {
            vertices = query.vertices();
        }

        // 2. In-Memory Filtering (Complex Metadata & Relations)
        Stream<JanusGraphVertex> stream = StreamSupport.stream(vertices.spliterator(), false);

        // Filter by Metadata (complex)
        if (CollectionUtils.isNotEmpty(sc.getMetadata())) {
            stream = stream.filter(v -> matchesMetadata(v, sc.getMetadata()));
        }

        // Filter by Relations
        if (CollectionUtils.isNotEmpty(sc.getRelations())) {
            stream = stream.filter(v -> matchesRelations(v, sc.getRelations(), graphId));
        }

        // Convert to Nodes
        List<Node> nodeList = stream.map(v -> JanusGraphNodeUtil.getNode(graphId, v))
                .collect(Collectors.toList());

        // 3. Sorting
        if (CollectionUtils.isNotEmpty(sc.getSortOrder())) {
            try {
                nodeList.sort(new NodeComparator(sc.getSortOrder()));
            } catch (Exception e) {
                TelemetryManager.error("Error during sorting", e);
            }
        }

        // 4. Pagination
        int start = sc.getStartPosition();
        int size = sc.getResultSize() > 0 ? sc.getResultSize() : nodeList.size();

        if (start >= nodeList.size()) {
            return new ArrayList<>();
        }
        int end = Math.min(start + size, nodeList.size());
        return new ArrayList<>(nodeList.subList(start, end));
    }

    private static void extractIdsFromMetadata(List<MetadataCriterion> metadata, List<String> ids) {
        if (metadata == null)
            return;
        for (MetadataCriterion mc : metadata) {
            if (mc.getFilters() != null) {
                for (Filter f : mc.getFilters()) {
                    if (StringUtils.equals(f.getProperty(), SystemProperties.IL_UNIQUE_ID.name())
                            || StringUtils.equals(f.getProperty(), "identifier")) {
                        Object val = f.getValue();
                        if (val instanceof String)
                            ids.add((String) val);
                        else if (val instanceof List) {
                            for (Object o : (List) val)
                                if (o instanceof String)
                                    ids.add((String) o);
                        } else if (val instanceof String[])
                            ids.addAll(Arrays.asList((String[]) val));
                    }
                }
            }
            // Recurse? Usually IDs are top level or we just optimize top level.
        }
    }

    private static boolean matchesMetadata(JanusGraphVertex v, List<MetadataCriterion> criteriaList) {
        for (MetadataCriterion mc : criteriaList) {
            if (!checkMetadataCriterion(v, mc))
                return false;
        }
        return true;
    }

    private static boolean checkMetadataCriterion(JanusGraphVertex v, MetadataCriterion mc) {
        // Op: AND or OR. Default AND.
        boolean isOr = StringUtils.equalsIgnoreCase(SearchConditions.LOGICAL_OR, mc.getOp());

        if (isOr) {
            // If any filter matches, return true.
            if (mc.getFilters() != null) {
                for (Filter f : mc.getFilters()) {
                    if (checkFilter(v, f))
                        return true;
                }
            }
            if (mc.getMetadata() != null) {
                for (MetadataCriterion nested : mc.getMetadata()) {
                    if (checkMetadataCriterion(v, nested))
                        return true;
                }
            }
            return false; // None matched
        } else {
            // AND: All must match
            if (mc.getFilters() != null) {
                for (Filter f : mc.getFilters()) {
                    if (!checkFilter(v, f))
                        return false;
                }
            }
            if (mc.getMetadata() != null) {
                for (MetadataCriterion nested : mc.getMetadata()) {
                    if (!checkMetadataCriterion(v, nested))
                        return false;
                }
            }
            return true;
        }
    }

    private static boolean checkFilter(JanusGraphVertex v, Filter f) {
        String prop = f.getProperty();
        if ("identifier".equals(prop))
            prop = SystemProperties.IL_UNIQUE_ID.name();

        Object val = null;
        if (v.property(prop).isPresent()) {
            val = v.value(prop);
        }

        Object filterVal = f.getValue();

        if (val == null)
            return false;

        String op = f.getOperator();
        if (op == null)
            op = SearchConditions.OP_EQUAL;

        switch (op) {
            case SearchConditions.OP_EQUAL:
                return val.equals(filterVal);
            case SearchConditions.OP_NOT_EQUAL:
                return !val.equals(filterVal);
            case SearchConditions.OP_IN:
                if (filterVal instanceof List)
                    return ((List<?>) filterVal).contains(val);
                if (filterVal instanceof Object[])
                    return Arrays.asList((Object[]) filterVal).contains(val);
                return val.equals(filterVal);
            case SearchConditions.OP_GREATER_THAN:
                return compare(val, filterVal) > 0;
            case SearchConditions.OP_GREATER_OR_EQUAL:
                return compare(val, filterVal) >= 0;
            case SearchConditions.OP_LESS_THAN:
                return compare(val, filterVal) < 0;
            case SearchConditions.OP_LESS_OR_EQUAL:
                return compare(val, filterVal) <= 0;
            case SearchConditions.OP_STARTS_WITH:
                return String.valueOf(val).startsWith(String.valueOf(filterVal));
            case SearchConditions.OP_ENDS_WITH:
                return String.valueOf(val).endsWith(String.valueOf(filterVal));
            case SearchConditions.OP_LIKE:
                return String.valueOf(val).contains(String.valueOf(filterVal));
            default:
                return val.equals(filterVal);
        }
    }

    private static int compare(Object v1, Object v2) {
        if (v1 instanceof Comparable && v2 instanceof Comparable) {
            try {
                return ((Comparable) v1).compareTo(v2);
            } catch (Exception e) {
                return 0;
            }
        }
        return String.valueOf(v1).compareTo(String.valueOf(v2));
    }

    private static boolean matchesRelations(JanusGraphVertex v, List<RelationCriterion> relations, String graphId) {
        for (RelationCriterion rc : relations) {
            if (!checkRelation(v, rc))
                return false;
        }
        return true;
    }

    private static boolean checkRelation(JanusGraphVertex v, RelationCriterion rc) {
        Iterator<JanusGraphEdge> edges = JanusGraphNodeUtil.getEdges(v,
                rc.getDirection() != null ? rc.getDirection().name() : "OUT", rc.getName());
        while (edges.hasNext()) {
            JanusGraphEdge e = edges.next();
            // Check Other Vertex Filters
            JanusGraphVertex other = e.inVertex().equals(v) ? e.outVertex() : e.inVertex();

            // Apply RC filters on 'other'
            boolean match = true;
            if (CollectionUtils.isNotEmpty(rc.getIdentifiers())) {
                String id = other.property(SystemProperties.IL_UNIQUE_ID.name()).isPresent()
                        ? (String) other.value(SystemProperties.IL_UNIQUE_ID.name())
                        : null;
                if (!rc.getIdentifiers().contains(id))
                    match = false;
            }
            if (StringUtils.isNotBlank(rc.getObjectType())) {
                String type = other.property(SystemProperties.IL_FUNC_OBJECT_TYPE.name()).isPresent()
                        ? (String) other.value(SystemProperties.IL_FUNC_OBJECT_TYPE.name())
                        : null;
                if (!StringUtils.equals(type, rc.getObjectType()))
                    match = false;
            }
            // Nested metadata on relation node
            if (match && CollectionUtils.isNotEmpty(rc.getMetadata())) {
                if (!matchesMetadata(other, rc.getMetadata()))
                    match = false;
            }

            if (match)
                return true; // Found at least one matching relation
        }
        return rc.isOptional();
    }

    private static class NodeComparator implements Comparator<Node> {
        private final List<Sort> sortOrder;

        public NodeComparator(List<Sort> sortOrder) {
            this.sortOrder = sortOrder;
        }

        @Override
        public int compare(Node n1, Node n2) {
            for (Sort sort : sortOrder) {
                Object v1 = n1.getMetadata() != null ? n1.getMetadata().get(sort.getSortField()) : null;
                Object v2 = n2.getMetadata() != null ? n2.getMetadata().get(sort.getSortField()) : null;

                int result = compareValues(v1, v2);
                if (StringUtils.equalsIgnoreCase(Sort.SORT_DESC, sort.getSortOrder())) {
                    result = -result;
                }

                if (result != 0)
                    return result;
            }
            return 0;
        }

        private int compareValues(Object v1, Object v2) {
            if (v1 == null && v2 == null)
                return 0;
            if (v1 == null)
                return -1;
            if (v2 == null)
                return 1;
            if (v1 instanceof Comparable && v2 instanceof Comparable) {
                try {
                    return ((Comparable) v1).compareTo(v2);
                } catch (Exception e) {
                    return 0;
                }
            }
            return String.valueOf(v1).compareTo(String.valueOf(v2));
        }
    }
}
