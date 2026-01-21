# JanusGraph Setup Guide with CDC Extension

This guide provides step-by-step instructions for setting up JanusGraph with YugabyteDB backend and CDC (Change Data Capture) extension for the Knowledge Platform.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Environment Setup](#environment-setup)
3. [Docker Compose Configuration](#docker-compose-configuration)
4. [JanusGraph Configuration](#janusgraph-configuration)
5. [CDC Extension Setup](#cdc-extension-setup)
6. [Verification](#verification)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

- Docker and Docker Compose installed
- Maven (for building CDC extension)
- Basic understanding of JanusGraph and Kafka

---

## Environment Setup

### 1. Create Environment File

Create a `.env` file in the project root:

```bash
# File: .env
sunbird_dbs_path=/Users/YOUR_USERNAME/sunbird-dbs
```

Replace `YOUR_USERNAME` with your actual username.

### 2. Create Data Directories

```bash
mkdir -p ~/sunbird-dbs/{yugabyte/data,janusgraph/data,janusgraph/logs,redis}
```

---

## Docker Compose Configuration

### 1. Create `docker-compose.yml`

```yaml
version: "3.0"
services:
  sunbird-yugabyte:
    image: yugabytedb/yugabyte:latest
    container_name: "sunbird_yugabyte"
    ports:
      - 9042:9042  # YCQL (Cassandra-compatible) port
      - 5434:5433  # YSQL (PostgreSQL-compatible) port
      - 7001:7000  # Master webserver
      - 9001:9000  # Master RPC
    volumes:
      - $sunbird_dbs_path/yugabyte/data:/home/yugabyte/yb_data
    command: ["bin/yugabyted", "start", "--daemon=false", "--ui=false"]
    healthcheck:
      test: ["CMD", "bin/yugabyted", "status"]
      interval: 10s
      timeout: 5s
      retries: 10

  sunbird-janusgraph:
    image: janusgraph/janusgraph:1.1.0
    container_name: "sunbird_janusgraph"
    ports:
      - 8182:8182  # Gremlin Server port
    depends_on:
      sunbird-yugabyte:
        condition: service_healthy
    volumes:
      - $sunbird_dbs_path/janusgraph/data:/var/lib/janusgraph/data
      - $sunbird_dbs_path/janusgraph/logs:/var/lib/janusgraph/logs
      - /Users/YOUR_USERNAME/janusgraph-cql.properties:/opt/janusgraph/conf/janusgraph-cql.properties
    environment:
      - JANUS_PROPS_TEMPLATE=cql
      - janusgraph.storage.backend=cql
      - janusgraph.storage.hostname=sunbird-yugabyte
      - janusgraph.storage.port=9042
      - janusgraph.storage.cql.keyspace=janusgraph

  sunbird-redis: 
    image: redis:6.0.8
    container_name: "sunbird_redis"
    ports:
      - 6379:6379
    command: redis-server
    volumes:
     - $sunbird_dbs_path/redis:/data

  zookeeper:
    image: 'wurstmeister/zookeeper:latest'
    container_name: zookeeper
    ports:
      - "2181:2181"    
    environment:
      - KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:2181     
    
  kafka:
    image: 'wurstmeister/kafka:2.12-2.5.1'
    container_name: kafka
    ports:
      - "9092:9092"
    environment:
      - KAFKA_BROKER_ID=1
      - KAFKA_LISTENERS=PLAINTEXT://:9092
      - KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:9092
      - KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181      
      - ALLOW_PLAINTEXT_LISTENER=yes
    depends_on:
      - zookeeper  

networks:
  default:
    name: janus-net
```

### 2. Start Services

```bash
docker-compose up -d
```

Verify all services are running:

```bash
docker-compose ps
```

---

## JanusGraph Configuration

### 1. Create JanusGraph Properties File

Create `janusgraph-cql.properties` in your home directory:

```properties
# File: ~/janusgraph-cql.properties
gremlin.graph=org.janusgraph.core.JanusGraphFactory
storage.backend=cql
storage.hostname=sunbird-yugabyte
storage.cql.keyspace=janusgraph

# Transaction Log Configuration (Required for CDC)
tx.log.tx.enabled=true
tx.log.tx.instance-id=janusgraph-instance-1
```

### 2. Verify JanusGraph Connection

```bash
docker logs sunbird_janusgraph | grep "Channel started"
```

You should see confirmation that the Gremlin Server started successfully.

---

## CDC Extension Setup

### 1. Build CDC Extension JAR

Navigate to the CDC extension directory and build:

```bash
cd /path/to/janusgraph-cdc-extension
mvn clean package -DskipTests
```

This creates `target/janusgraph-cdc-extension-1.0.0.jar`.

### 2. Deploy CDC JAR to JanusGraph Container

```bash
docker cp target/janusgraph-cdc-extension-1.0.0.jar sunbird_janusgraph:/opt/janusgraph/lib/
```

Verify the JAR is present:

```bash
docker exec sunbird_janusgraph ls -lh /opt/janusgraph/lib/janusgraph-cdc-extension-1.0.0.jar
```

### 3. Deploy CDC Bootstrap Script

```bash
docker cp scripts/register-cdc.groovy sunbird_janusgraph:/opt/janusgraph/scripts/
```

### 4. Configure Transaction Log Backend

Add transaction log configuration to JanusGraph server properties:

```bash
docker exec sunbird_janusgraph sh -c 'cat >> /opt/janusgraph/conf/janusgraph-cql-server.properties << EOF
# CDC Transaction Log Configuration
log.learning_graph_events.backend=default
log.learning_graph_events.key-consistent=true
log.learning_graph_events.read-interval=500
EOF'
```

Verify the configuration:

```bash
docker exec sunbird_janusgraph cat /opt/janusgraph/conf/janusgraph-cql-server.properties | grep learning_graph_events
```

### 5. Update Bootstrap Script Configuration

Update the Kafka bootstrap server in the CDC script:

```bash
docker exec sunbird_janusgraph sed -i 's/kafka:29092/kafka:9092/' /opt/janusgraph/scripts/register-cdc.groovy
```

Verify:

```bash
docker exec sunbird_janusgraph cat /opt/janusgraph/scripts/register-cdc.groovy | grep kafka.bootstrap
```

### 6. Configure JanusGraph Server to Load CDC Script

Update `janusgraph-server.yaml` to load the CDC bootstrap script:

```bash
docker exec sunbird_janusgraph sed -i 's/scripts\/empty-sample.groovy/scripts\/register-cdc.groovy/' /opt/janusgraph/conf/janusgraph-server.yaml
```

Verify:

```bash
docker exec sunbird_janusgraph cat /opt/janusgraph/conf/janusgraph-server.yaml | grep -A2 ScriptFileGremlinPlugin
```

Expected output:
```yaml
org.apache.tinkerpop.gremlin.jsr223.ScriptFileGremlinPlugin: {files: [scripts/register-cdc.groovy]}}}}
```

### 7. Restart JanusGraph Container

```bash
docker restart sunbird_janusgraph
```

Wait ~30 seconds for startup, then verify CDC processor started:

```bash
docker logs sunbird_janusgraph | grep "GraphLogProcessor started successfully"
```

Expected output:
```
INFO  org.sunbird.janusgraph.cdc.GraphLogProcessor - GraphLogProcessor started successfully with 2 sinks.
```

---

## Verification

### 1. Check All Services Status

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

All containers should show "Up" status.

### 2. Verify CDC Processor

Check that CDC processor loaded successfully:

```bash
docker logs sunbird_janusgraph 2>&1 | grep -E "GraphLogProcessor|CDC:"
```

Expected output should include:
```
INFO  org.sunbird.janusgraph.cdc.GraphLogProcessor.start - Starting GraphLogProcessor...
INFO  org.sunbird.janusgraph.cdc.GraphLogProcessor.start - Using SunbirdLegacyMessageConverter
INFO  org.sunbird.janusgraph.cdc.GraphLogProcessor.start - Added Kafka Event Sink (Topic: sunbirddev.learning.graph.events)
INFO  org.sunbird.janusgraph.cdc.GraphLogProcessor.start - Added Log File Event Sink
INFO  org.sunbird.janusgraph.cdc.GraphLogProcessor.start - GraphLogProcessor started successfully with 2 sinks.
```

### 3. Verify Kafka Connection

Check that there are no Kafka connection errors:

```bash
docker logs sunbird_janusgraph 2>&1 | grep -i "kafka.*error" | tail -5
```

If Kafka is properly connected, you should see no recent errors.

### 4. Test CDC Event Flow (Optional)

To test CDC events, you can create a test node via Gremlin console:

```bash
docker exec -it sunbird_janusgraph ./bin/gremlin.sh
```

In the Gremlin console:
```groovy
:remote connect tinkerpop.server conf/remote.yaml
:remote console
graph.tx().logIdentifier("learning_graph_events").start()
g.addV("test").property("name", "CDC Test").next()
graph.tx().commit()
```

Then check Kafka for the event:

```bash
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic sunbirddev.learning.graph.events \
  --from-beginning \
  --timeout-ms 3000
```


---

## Schema Initialization

Before loading data, you must initialize the schema and indexes to ensure performance and data integrity.

### 1. Locate Schema Script

The schema initialization script is located at:
`knowledge-platform/schema_init.groovy`

### 2. Copy Script to Container

```bash
docker cp /Users/YOUR_USERNAME/January/knowledge-platform/schema_init.groovy sunbird_janusgraph:/opt/janusgraph/scripts/
```

### 3. Execute Schema Initialization

Run the script using the Gremlin console inside the container:

```bash
docker exec sunbird_janusgraph ./bin/gremlin.sh -e scripts/schema_init.groovy
```

### 4. Verify Index Status

Create a verification script `check_index_status.groovy`:

```groovy
jg = JanusGraphFactory.open('conf/janusgraph-cql.properties')
mgmt = jg.openManagement()
println "Checking Index Status..."
mgmt.getGraphIndexes(Vertex.class).each { index ->
    println "Index: ${index.name()}"
    index.getFieldKeys().each { key ->
        status = mgmt.getGraphIndex(index.name()).getIndexStatus(key)
        println "  Key: ${key.name()} Status: $status"
    }
}
System.exit(0)
```

Copy and run it:

```bash
docker cp check_index_status.groovy sunbird_janusgraph:/opt/janusgraph/scripts/
docker exec sunbird_janusgraph ./bin/gremlin.sh -e scripts/check_index_status.groovy
```

Ensure all indexes show `Status: ENABLED`.

---

## Data Migration

Follow these steps to migrate data from CSV files (`nodes.csv`, `relationships.csv`) into JanusGraph.

### 1. Mount Data Volume

Ensure your `docker-compose.yml` mounts the data directory to the JanusGraph container.

```yaml
  sunbird-janusgraph:
    # ...
    volumes:
      - /path/to/janus-data:/data
```

Recreate the container if you made changes:
```bash
docker-compose up -d sunbird-janusgraph
```

### 2. Create Migration Script

Create a script `import_data.groovy` with the following content. This script parses the CSVs, handles JSON properties, creates vertices with a `node_id` property, and links them via edges.

```groovy
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.T
import org.apache.tinkerpop.gremlin.structure.Direction
import groovy.json.JsonSlurper

// Open Graph
graph = JanusGraphFactory.open('conf/janusgraph-cql.properties')
// Bind graph and traversal to global binding for access in closures
binding.graph = graph
binding.g = graph.traversal()

println "--- STARTING DATA MIGRATION ---"

// --- 1. NODES ---
println "Importing Nodes..."

if (!binding.hasVariable('state')) {
    binding.state = [accumulating: false, jsonBuffer: '', nodeLine: '']
}

new File('/data/nodes.csv').eachLine { line, idx ->
    try {
        if (idx == 1) return // skip header

        def state = binding.state
        // Access g via binding
        def g = binding.g

        if (state.accumulating) {
            state.nodeLine += ' ' + line.trim()
            if (line.trim().endsWith('}')) state.accumulating = false
            else return
        } else {
            state.nodeLine = line
            if (!line.trim().endsWith('}')) {
                state.accumulating = true
                return
            }
        }

        def parts = state.nodeLine.split(/,(?=\s*\[?["{])/)
        
        if (parts.size() < 3) {
             println "Skipping malformed line $idx: ${state.nodeLine}"
             return
        }

        def nodeIdVal = parts[0].toLong()
        def labelRaw = parts[1].replaceAll(/\[|\]|"/, '')
        def label = labelRaw
        def propsRaw = parts[2..-1].join(',')

        def propsFixed = propsRaw.replaceAll(/([{,]\s*)(\w+):/, '$1"$2":')
                                 .replaceAll(/\bTRUE\b/, 'true')
                                 .replaceAll(/\bFALSE\b/, 'false')

        def propsMap = new JsonSlurper().parseText(propsFixed)

        def existing = g.V().has('node_id', nodeIdVal).tryNext().orElse(null)
        
        if (!existing) {
            def uniqueId = propsMap['IL_UNIQUE_ID']
            if (uniqueId) {
                existing = g.V().has('IL_UNIQUE_ID', uniqueId).tryNext().orElse(null)
            }
        }

        if (!existing) {
            def v = binding.graph.addVertex(T.label, label, 'node_id', nodeIdVal)
            propsMap.each { k, vprop ->
                if (vprop instanceof List) vprop = vprop.join(',')
                else if (vprop instanceof BigDecimal) vprop = vprop.doubleValue()
                v.property(k, vprop)
            }
        }
    } catch (Exception e) {
        println "Error on line $idx: ${e.message}"
    }
}
binding.graph.tx().commit()
println "Nodes Imported."


// --- 2. RELATIONSHIPS ---
println "Importing Relationships..."

new File('/data/relationships.csv').eachLine { line, idx ->
    try {
        if (idx == 1) return

        def g = binding.g
        def graph = binding.graph 

        def matcher = line =~ /^(\d+),\s*"([^"]+)",\s*(\d+),\s*(.*)$/
        if (!matcher.matches()) {
            println "Skipping malformed edge line $idx: $line"
            return
        }

        def fromId = matcher[0][1].toLong()
        def relType = matcher[0][2]
        def toId = matcher[0][3].toLong()
        def propsRaw = matcher[0][4].trim()

        def propsMap = [:]

        if (propsRaw) {
            propsRaw = propsRaw.replaceAll(/^\{|\}$/, '')
            if (propsRaw) {
                propsRaw.split(',').each { kv ->
                    def kvParts = kv.split(':')
                    if (kvParts.size() == 2) {
                        def k = kvParts[0].trim()
                        def v = kvParts[1].trim()
                        if (v ==~ /^\d+$/) v = v.toLong()
                        else if (v ==~ /^\d+\.\d+$/) v = v.toDouble()
                        propsMap[k] = v
                    }
                }
            }
        }

        def fromV = g.V().has('node_id', fromId).tryNext().orElse(null)
        def toV = g.V().has('node_id', toId).tryNext().orElse(null)

        if (fromV && toV) {
            def existing = fromV.edges(Direction.OUT, relType).find { it.inVertex().value('node_id') == toId }
            if (!existing) {
                def e = fromV.addEdge(relType, toV)
                propsMap.each { k, v -> e.property(k, v) }
            }
        } else {
            println "Skipping edge $idx: from ${fromId} or to ${toId} vertex not found"
        }
    } catch (Exception e) {
        println "Error on edge line $idx: ${e.message}"
    }
}
binding.graph.tx().commit()
println "Relationships Imported."

// Verify
println "Vertices: " + binding.g.V().count().next()
println "Edges: " + binding.g.E().count().next()

System.exit(0)
```

### 3. Execute Migration

Copy the script to the container and run it:

```bash
docker cp import_data.groovy sunbird_janusgraph:/opt/janusgraph/scripts/
docker exec sunbird_janusgraph ./bin/gremlin.sh -e scripts/import_data.groovy
```

### 4. Verification

The script will output the total vertex and edge counts. You can also run a quick check manually:

```bash
docker exec -it sunbird_janusgraph ./bin/gremlin.sh
```

```groovy
:remote connect tinkerpop.server conf/remote.yaml
:remote console
g.V().count()
g.E().count()
g.V().limit(1).valueMap(true)
```

---

## Troubleshooting

### Issue: JanusGraph Can't Connect to YugabyteDB

**Symptoms:**
```
Connection refused to sunbird-yugabyte:9042
```

**Solution:**
1. Check YugabyteDB is healthy:
   ```bash
   docker exec sunbird_yugabyte bin/yugabyted status
   ```
2. Ensure the health check passes before JanusGraph starts
3. Verify network connectivity:
   ```bash
   docker network inspect janus-net
   ```

### Issue: CDC Processor Not Starting

**Symptoms:**
```
ClassNotFoundException: org.sunbird.janusgraph.cdc.GraphLogProcessor
```

**Solution:**
1. Verify CDC JAR is in `/opt/janusgraph/lib/`:
   ```bash
   docker exec sunbird_janusgraph ls -lh /opt/janusgraph/lib/janusgraph-cdc-extension-1.0.0.jar
   ```
2. Restart JanusGraph container:
   ```bash
   docker restart sunbird_janusgraph
   ```

### Issue: Kafka Connection Errors

**Symptoms:**
```
Connection to node -1 (kafka/127.0.0.1:9092) could not be established
```

**Solution:**
1. Verify Kafka is running:
   ```bash
   docker ps --filter "name=kafka"
   ```
2. Check Kafka logs:
   ```bash
   docker logs kafka
   ```
3. Ensure `KAFKA_ZOOKEEPER_CONNECT` is set in `docker-compose.yml`
4. Recreate Kafka container:
   ```bash
   docker-compose stop kafka
   docker-compose rm -f kafka
   docker-compose up -d kafka
   ```

### Issue: Transaction Log Not Configured

**Symptoms:**
CDC events not being captured despite processor running.

**Solution:**
1. Verify `learning_graph_events` log is configured:
   ```bash
   docker exec sunbird_janusgraph cat /opt/janusgraph/conf/janusgraph-cql-server.properties | grep learning_graph_events
   ```
2. Ensure application code uses `logIdentifier("learning_graph_events")` when creating transactions

### Issue: Redis Connection Errors in Tests

**Symptoms:**
```
JedisConnectionException: Could not get a resource from the pool
```

**Solution:**
1. Ensure Redis container is running:
   ```bash
   docker ps --filter "name=redis"
   ```
2. For tests, use embedded Redis (see test configuration section)

---

## Application Configuration

### Enable Transaction Logging in Application

In your application's `application.conf`:

```hocon
# Enable transaction logging for CDC
graph.txn.enable_log = true
```

### Example Code Usage

```java
if (TXN_LOG_ENABLED) {
    JanusGraph graph = DriverUtil.getJanusGraph(graphId);
    JanusGraphTransaction tx = graph.buildTransaction()
        .logIdentifier("learning_graph_events")
        .start();
    GraphTraversalSource g = tx.traversal();
    
    // Perform graph operations
    g.addV("Content").property("name", "Test Content").next();
    
    tx.commit();
}
```

---

## Summary

You now have a fully configured JanusGraph setup with:

- ✅ YugabyteDB as the storage backend
- ✅ Redis for caching
- ✅ Kafka for event streaming
- ✅ CDC extension for capturing graph changes
- ✅ Transaction log configured for CDC events
- ✅ All services running in Docker with proper networking

CDC events will be published to the Kafka topic `sunbirddev.learning.graph.events` whenever graph mutations occur with the `learning_graph_events` log identifier.

---

## Next Steps

1. Configure your Knowledge Platform application to use this JanusGraph instance
2. Set up Kafka consumers to process CDC events
3. Monitor CDC event flow and adjust `read-interval` if needed
4. Consider setting up log rotation for CDC log files
5. Implement backup strategies for YugabyteDB data

For more information, refer to:
- [JanusGraph Documentation](https://docs.janusgraph.org/)
- [YugabyteDB Documentation](https://docs.yugabyte.com/)
- [CDC Extension README](/path/to/janusgraph-cdc-extension/README.md)
