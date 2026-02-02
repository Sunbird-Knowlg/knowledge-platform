# Bitnami JanusGraph Setup Guide

This guide details the specific configuration and workarounds required to run JanusGraph using the `bitnamilegacy/janusgraph` Docker image in this project.

## 1. Docker Compose Configuration

The Bitnami image requires a custom `entrypoint` to bypass its default startup script (`setup.sh`), which hangs due to network isolation issues when checking the storage backend.

**File:** `docker-compose.yml`

```yaml
  sunbird-janusgraph:
    image: docker.io/bitnamilegacy/janusgraph:1.1.0-debian-12-r21
    container_name: "sunbird_janusgraph"
    ports:
      - 8182:8182
    depends_on:
      sunbird-yugabyte:
        condition: service_healthy
    volumes:
      # Mount data directories
      - $sunbird_dbs_path/janusgraph/data:/bitnami/janusgraph/data
      - $sunbird_dbs_path/janusgraph/logs:/bitnami/janusgraph/logs
      # Mount configuration files
      - /Users/sanketikam4/janusgraph-cql.properties:/opt/bitnami/janusgraph/conf/janusgraph-cql.properties
      - ./gremlin-server.yaml:/opt/bitnami/janusgraph/conf/gremlin-server.yaml
      # Mount data for import
      - /Users/sanketikam4/janus-data:/data
    environment:
      - JANUSGRAPH_STORAGE_BACKEND=cql
      - JANUSGRAPH_STORAGE_HOSTNAME=sunbird-yugabyte
      - JANUSGRAPH_STORAGE_PORT=9042
      - JANUSGRAPH_CFG_storage_cql_keyspace=janusgraph
      - JANUSGRAPH_CFG_storage_directory=/bitnami/janusgraph/data
    # CUSTOM ENTRYPOINT (Critical Fix)
    entrypoint:
      - /opt/bitnami/janusgraph/bin/janusgraph-server.sh
      - /opt/bitnami/janusgraph/conf/gremlin-server.yaml
```

## 2. Configuration Files

### `gremlin-server.yaml`
Ensure the graph configuration points to the mounted properties file:
```yaml
graphs: {graph: /opt/bitnami/janusgraph/conf/janusgraph-cql.properties}
```

### `janusgraph-cql.properties`
Ensure the hostname matches the Docker service name:
```properties
storage.backend=cql
storage.hostname=sunbird-yugabyte
```

## 3. Running Gremlin Console & Scripts

The `gremlin.sh` script in this image fails with a `LoginException` unless run as `root` with specific Hadoop environment variables.

### Interactive Console
```bash
docker exec -u 0 -e USER=root -e HADOOP_USER_NAME=root -it sunbird_janusgraph /opt/bitnami/janusgraph/bin/gremlin.sh
```

### Executing Migration Scripts
To run Groovy scripts (e.g., for data import or schema creation):

1.  **Copy script to container:**
    ```bash
    docker cp path/to/script.groovy sunbird_janusgraph:/data/
    ```

2.  **Execute script:**
    ```bash
    docker exec -u 0 -e USER=root -e HADOOP_USER_NAME=root -i sunbird_janusgraph /opt/bitnami/janusgraph/bin/gremlin.sh -e /data/script.groovy
    ```

## 4. Migration Scripts Used

*   **`import_data_final.groovy`**: Imports nodes and relationships from CSV files.
*   **`schema_init.groovy`**: Creates schemas and indexes to fix performance warnings.
*   **`verify_migration.groovy`**: Verifies the count of vertices and edges.

## Troubleshooting

*   **"Waiting for Storage backend..."**: If you see this log hanging indefinitely, ensure you are using the custom `entrypoint` shown above, NOT the default.
*   **"LoginException... invalid null input: name"**: You forgot the `-e USER=root -e HADOOP_USER_NAME=root` flags when running `gremlin.sh`.
