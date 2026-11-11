package org.sunbird.sync;

import org.apache.commons.cli.*;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.T;
import org.janusgraph.core.JanusGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunbird.graph.service.util.DriverUtil;
import org.sunbird.search.client.ElasticSearchUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class SyncTool {

    private static final Logger logger = LoggerFactory.getLogger(SyncTool.class);
    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        Options options = buildOptions();
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            new HelpFormatter().printHelp("sync-tool", options);
            System.exit(1);
            return;
        }

        if (cmd.hasOption("help")) {
            new HelpFormatter().printHelp("sync-tool", options);
            return;
        }

        SyncConfig config = new SyncConfig();
        SyncTransformer transformer = new SyncTransformer(config);

        try {
            // Initialize connections
            log("Connecting to JanusGraph...");
            JanusGraph graph = DriverUtil.getJanusGraph(config.graphId);
            log("JanusGraph connected.");

            log("Connecting to OpenSearch...");
            ElasticSearchUtil.initialiseESClient(config.indexName, config.esConnInfo);
            log("OpenSearch connected.");

            // Enumerate IDs
            List<String> identifiers = enumerateIds(cmd, graph, config);
            if (identifiers == null || identifiers.isEmpty()) {
                log("No nodes to sync.");
                System.exit(0);
                return;
            }

            log("Enumerated " + identifiers.size() + " nodes");

            // Sync in batches
            int synced = 0;
            int failed = 0;
            List<String> failedIds = new ArrayList<>();
            int totalBatches = (int) Math.ceil((double) identifiers.size() / config.batchSize);

            for (int i = 0; i < identifiers.size(); i += config.batchSize) {
                int batchNum = (i / config.batchSize) + 1;
                List<String> batchIds = identifiers.subList(i, Math.min(i + config.batchSize, identifiers.size()));

                BatchResult result = syncBatch(graph, config, transformer, batchIds);

                synced += result.synced;
                failed += result.failed;
                failedIds.addAll(result.failedIds);

                if (result.failed > 0) {
                    log("Batch " + batchNum + "/" + totalBatches + ": " + result.synced + " synced, " +
                            result.failed + " failed " + result.failedIds);
                } else {
                    log("Batch " + batchNum + "/" + totalBatches + ": " + result.synced + " synced");
                }
            }

            // Summary
            log("Complete. " + synced + " synced, " + failed + " failed.");
            if (!failedIds.isEmpty()) {
                log("Failed IDs: " + String.join(", ", failedIds));
            }

            System.exit(failed > 0 ? 1 : 0);

        } catch (Exception e) {
            logger.error("Sync failed", e);
            System.exit(2);
        }
    }

    private static List<String> enumerateIds(CommandLine cmd, JanusGraph graph, SyncConfig config) throws IOException {
        GraphTraversalSource g = graph.traversal();

        if (cmd.hasOption("identifiers")) {
            String[] ids = cmd.getOptionValue("identifiers").split(",");
            log("Mode: identifiers (" + ids.length + " provided)");
            return Arrays.asList(ids);
        }

        if (cmd.hasOption("file")) {
            String filePath = cmd.getOptionValue("file");
            log("Mode: file (" + filePath + ")");
            List<String> ids = Files.readAllLines(Paths.get(filePath)).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
            return ids;
        }

        if (cmd.hasOption("type") && "full".equalsIgnoreCase(cmd.getOptionValue("type"))) {
            log("Mode: full sync");
            return g.V().has("IL_UNIQUE_ID")
                    .values("IL_UNIQUE_ID")
                    .toList().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        if (cmd.hasOption("objectType")) {
            String objectType = cmd.getOptionValue("objectType");
            log("Mode: objectType=" + objectType);
            return g.V().has("IL_FUNC_OBJECT_TYPE", objectType)
                    .values("IL_UNIQUE_ID")
                    .toList().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        if (cmd.hasOption("days")) {
            int days = Integer.parseInt(cmd.getOptionValue("days"));
            String cutoff = Instant.now().minus(days, ChronoUnit.DAYS).toString();
            log("Mode: last " + days + " days (lastUpdatedOn >= " + cutoff + ")");
            return g.V().has("lastUpdatedOn", org.apache.tinkerpop.gremlin.process.traversal.P.gte(cutoff))
                    .values("IL_UNIQUE_ID")
                    .toList().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        System.err.println("Error: No sync mode specified. Use --help for usage.");
        System.exit(1);
        return null;
    }

    private static BatchResult syncBatch(JanusGraph graph, SyncConfig config,
                                          SyncTransformer transformer, List<String> batchIds) {
        int retries = 0;
        while (retries <= config.retryCount) {
            try {
                return doSyncBatch(graph, config, transformer, batchIds);
            } catch (Exception e) {
                retries++;
                if (retries <= config.retryCount) {
                    logger.warn("Batch failed (attempt {}/{}): {}", retries, config.retryCount, e.getMessage());
                } else {
                    logger.error("Batch failed after {} retries", config.retryCount, e);
                    BatchResult result = new BatchResult();
                    result.failed = batchIds.size();
                    result.failedIds = new ArrayList<>(batchIds);
                    return result;
                }
            }
        }
        // Unreachable
        return new BatchResult();
    }

    @SuppressWarnings("unchecked")
    private static BatchResult doSyncBatch(JanusGraph graph, SyncConfig config,
                                            SyncTransformer transformer, List<String> batchIds) throws Exception {
        GraphTraversalSource g = graph.traversal();
        BatchResult result = new BatchResult();

        // Fetch all properties for batch
        Map<String, Object> bulkDocs = new HashMap<>();

        for (String identifier : batchIds) {
            try {
                List<Map<Object, Object>> vertices = g.V().has("IL_UNIQUE_ID", identifier)
                        .elementMap().toList();

                if (vertices.isEmpty()) {
                    logger.warn("Node not found: {}", identifier);
                    result.failed++;
                    result.failedIds.add(identifier);
                    continue;
                }

                Map<Object, Object> rawProps = vertices.get(0);

                // Convert to String-keyed map
                Map<String, Object> props = new HashMap<>();
                for (Map.Entry<Object, Object> entry : rawProps.entrySet()) {
                    String key = entry.getKey().toString();
                    // elementMap returns T.id and T.label as keys
                    if (entry.getKey() == T.id) {
                        props.put("id", entry.getValue());
                    } else if (entry.getKey() == T.label) {
                        // skip vertex label
                    } else {
                        props.put(key, entry.getValue());
                    }
                }

                Map<String, Object> document = transformer.transform(props);
                if (document == null) {
                    // Restricted objectType, skip
                    continue;
                }

                bulkDocs.put(identifier, document);

            } catch (Exception e) {
                logger.warn("Error processing node {}: {}", identifier, e.getMessage());
                result.failed++;
                result.failedIds.add(identifier);
            }
        }

        // Bulk upsert to OpenSearch
        if (!bulkDocs.isEmpty()) {
            ElasticSearchUtil.bulkIndexWithIndexId(config.indexName, bulkDocs);
            result.synced = bulkDocs.size();
        }

        return result;
    }

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption(Option.builder().longOpt("identifiers")
                .hasArg().desc("Comma-separated node IDs to sync").build());
        options.addOption(Option.builder().longOpt("objectType")
                .hasArg().desc("Sync all nodes of this objectType").build());
        options.addOption(Option.builder().longOpt("days")
                .hasArg().desc("Sync nodes updated in last N days").build());
        options.addOption(Option.builder().longOpt("file")
                .hasArg().desc("Path to CSV file with node IDs").build());
        options.addOption(Option.builder().longOpt("type")
                .hasArg().desc("Sync type: 'full' for all nodes").build());
        options.addOption(Option.builder().longOpt("help")
                .desc("Show usage").build());
        return options;
    }

    private static void log(String message) {
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(LOG_FMT);
        logger.info("[" + timestamp + "] " + message);
    }

    static class BatchResult {
        int synced = 0;
        int failed = 0;
        List<String> failedIds = new ArrayList<>();
    }
}
