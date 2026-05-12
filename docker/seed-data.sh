#!/bin/bash
set -e

# Path to the data-seed directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_SEED_DIR="${SCRIPT_DIR}/data-seed"

# Environment prefix
ENV=${1:-dev}

# Force Reset option
FORCE_RESET=${FORCE_RESET:-false}

echo "Starting data restoration for environment: ${ENV} from ${DATA_SEED_DIR}..."

# Find all CSV files in the data-seed directory
for file in "${DATA_SEED_DIR}"/*.csv; do
    [ -e "$file" ] || continue
    
    filename=$(basename "$file")
    
    # Split filename into parts (keyspace.table.csv)
    IFS='.' read -r keyspace table ext <<< "$filename"
    
    target_table="${ENV}_${keyspace}.${table}"
    
    echo "Restoring table: ${target_table}"
    
    # Copy file to container
    docker cp "$file" yugabyte:/tmp/"$filename"
    
    # Optional: Truncate
    if [ "$FORCE_RESET" = "true" ]; then
        echo "  Truncating ${target_table}"
        MSYS_NO_PATHCONV=1 docker exec yugabyte /home/yugabyte/bin/ycqlsh -e "TRUNCATE $target_table;"
    fi
    
    # Execute COPY FROM
    MSYS_NO_PATHCONV=1 docker exec yugabyte /home/yugabyte/bin/ycqlsh -e "COPY $target_table FROM '/tmp/$filename' WITH HEADER=TRUE;"
    echo "  Restored ${target_table}"
done

# JanusGraph Import
GRAPH_SNAPSHOT="${DATA_SEED_DIR}/graph_snapshot.json"
if [ -f "$GRAPH_SNAPSHOT" ]; then
    echo "Restoring JanusGraph data..."
    docker cp "$GRAPH_SNAPSHOT" janusgraph:/tmp/graph_snapshot.json
    docker cp "${DATA_SEED_DIR}/import_graph.groovy" janusgraph:/tmp/import_graph.groovy
    
    # Execute Gremlin import
    docker exec -u 0 janusgraph bash -c 'HADOOP_GREMLIN_LIBS="" /opt/bitnami/janusgraph/bin/gremlin.sh -e /tmp/import_graph.groovy'
    echo "Restored JanusGraph data."
else
    echo "No graph snapshot found, skipping JanusGraph restoration."
fi

echo "Data restoration complete."
