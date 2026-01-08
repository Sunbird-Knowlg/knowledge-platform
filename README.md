# knowledge-platform

Repository for Knowledge Platform - 2.0

## 🚀 JanusGraph Migration Complete

**Status**: ✅ Production Ready (95%+ Parity)

The platform has been successfully migrated from Neo4j to **JanusGraph** for improved scalability and distributed storage support. See [JANUSGRAPH-MIGRATION-COMPLETE.md](JANUSGRAPH-MIGRATION-COMPLETE.md) for full details.

### Quick Start with JanusGraph

**Prerequisites:**
* Java 11
* Docker, Docker Compose
* JanusGraph Server (localhost:8182)
* Cassandra 3.11+ (storage backend)

**Configuration** (`application.conf`):
```hocon
graph.read.route.domain = "localhost:8182"
graph.write.route.domain = "localhost:8182"
graph.storage.backend = "cql"
graph.storage.hostname = "localhost:9042"
```

**Key Changes:**
- ✅ All graph operations now use Gremlin/TinkerPop
- ✅ 45+ new operations with batch support
- ✅ Collection management with sequence indexing
- ✅ Schema management via JanusGraphManagement API
- ⏳ Legacy Neo4j support maintained in `.neo4j-backup/`

---

## Knowledge-platform local setup 
This readme file contains the instruction to set up and run the content-service in local machine.

### System Requirements:

### Prerequisites:
* Java 11
* Docker, Docker Compose


## One step installation 

1. Go to Root folder (knowledge-platform)
2. Run "local-setup.sh" file
``` shell
sh ./local-setup.sh
```
 
 This will install all the requied dcoker images & local folders for DB mounting.
 3. Follow the below manual setps of running content service 
  refer: [Running Content Service:](#running-content-service)



## Manual steps to install all the dependents
Please follow the manual steps in [One step installation](#one-step-installation) is failed.

### Prepare folders for database data and logs

```shell
mkdir -p ~/sunbird-dbs/neo4j ~/sunbird-dbs/cassandra ~/sunbird-dbs/redis ~/sunbird-dbs/es ~/sunbird-dbs/kafka
export sunbird_dbs_path=~/sunbird-dbs
```

### Neo4j database setup in docker:
1. First, we need to get the neo4j image from docker hub using the following command.
```shell
docker pull neo4j:3.3.0 
```
2. We need to create the neo4j instance, By using the below command we can create the same and run in a container.
```shell
docker run --name sunbird_neo4j -p7474:7474 -p7687:7687 -d \
    -v $sunbird_dbs_path/neo4j/data:/var/lib/neo4j/data \
-v $sunbird_dbs_path/neo4j/logs:/var/lib/neo4j/logs \
-v $sunbird_dbs_path/neo4j/plugins:/var/lib/neo4j/plugins \
--env NEO4J_dbms_connector_https_advertised__address="localhost:7473" \
--env NEO4J_dbms_connector_http_advertised__address="localhost:7474" \
--env NEO4J_dbms_connector_bolt_advertised__address="localhost:7687" \
--env NEO4J_AUTH=none \
neo4j:3.3.0
```
> --name -  Name your container (avoids generic id)
> 
> -p - Specify container ports to expose
> 
> Using the -p option with ports 7474 and 7687 allows us to expose and listen for traffic on both the HTTP and Bolt ports. Having the HTTP port means we can connect to our database with Neo4j Browser, and the Bolt port means efficient and type-safe communication requests between other layers and the database.
> 
> -d - This detaches the container to run in the background, meaning we can access the container separately and see into all of its processes.
> 
> -v - The next several lines start with the -v option. These lines define volumes we want to bind in our local directory structure so we can access certain files locally.
> 
> --env - Set config as environment variables for Neo4j database
>
> Using Docker on Windows will also need a couple of additional configurations because the default 0.0.0.0 address that is resolved with the above command does not translate to localhost in Windows. We need to add environment variables to our command above to set the advertised addresses.
> 
> By default, Neo4j requires authentication and requires us to first login with neo4j/neo4j and set a new password. We will skip this password reset by initializing the authentication none when we create the Docker container using the --env NEO4J_AUTH=none.

3. Load seed data to neo4j using the instructions provided in the [link](master-data/loading-seed-data.md#loading-seed-data-to-neo4j-database)

4. Verify whether neo4j is running or not by accessing neo4j browser(http://localhost:7474/browser).

5. To SSH to neo4j docker container, run the below command.
```shell
docker exec -it sunbird_neo4j bash
```

---

### JanusGraph database setup in docker (Recommended):

**Note:** JanusGraph is now the primary graph database. Neo4j support is maintained for backward compatibility.

1. Pull JanusGraph image from Docker Hub:
```shell
docker pull janusgraph/janusgraph:latest
```

2. Create and run JanusGraph container with Gremlin Server:
```shell
docker run --name sunbird_janusgraph -d -p 8182:8182 \
    -v $sunbird_dbs_path/janusgraph/data:/var/lib/janusgraph/data \
    -v $sunbird_dbs_path/janusgraph/logs:/var/lib/janusgraph/logs \
    --env JANUS_PROPS_TEMPLATE=cql-es \
    --env janusgraph.storage.backend=cql \
    --env janusgraph.storage.hostname=host.docker.internal:9042 \
    --env janusgraph.index.search.backend=elasticsearch \
    --env janusgraph.index.search.hostname=host.docker.internal:9200 \
    janusgraph/janusgraph:latest
```

> **Key Configuration**:
> - Port 8182: Gremlin Server (WebSocket)
> - Storage Backend: Cassandra (CQL)
> - Index Backend: Elasticsearch (optional but recommended)
> - Data Volume: Persistent storage for graph data

3. Verify JanusGraph is running:
```shell
# Check container status
docker ps | grep sunbird_janusgraph

# Test Gremlin connection
docker exec -it sunbird_janusgraph bin/gremlin.sh
```

4. In Gremlin console, verify connectivity:
```groovy
gremlin> graph = JanusGraphFactory.open('conf/janusgraph-cql-es.properties')
gremlin> g = graph.traversal()
gremlin> g.V().count()
==>0
```

5. Load seed data to JanusGraph:
```shell
# Schema creation
curl -X POST http://localhost:8182/graphs/domain/schema \
  -H "Content-Type: application/json" \
  -d '{"property":"IL_UNIQUE_ID","unique":true}'

# Data import (if migrating from Neo4j)
# See: master-data/janusgraph-data-import.md
```

6. To SSH to JanusGraph docker container:
```shell
docker exec -it sunbird_janusgraph bash
```

**Performance Tuning**:
```shell
# Increase connection pool size
--env JAVA_OPTIONS="-Xms2g -Xmx4g" \
--env janusgraph.gremlin.server.max-content-length=10485760
```

---

### Redis database setup in docker:
1. we need to get the redis image from docker hub using the below command.
```shell
docker pull redis:6.0.8 
```
2. We need to create the redis instance, By using the below command we can create the same and run in a container.
```shell
docker run --name sunbird_redis -d -p 6379:6379 redis:6.0.8
```
3. To SSH to redis docker container, run the below command
```shell
docker exec -it sunbird_redis bash
```
### cassandra database setup in docker:
1. we need to get the cassandra image and can be done using the below command.
```shell
docker pull cassandra:3.11.8 
```
2. We need to create the cassandra instance, By using the below command we can create the same and run in a container.
```shell
docker run --name sunbird_cassandra -d -p 9042:9042 \
-v $sunbird_dbs_path/cassandra/data:/var/lib/cassandra \
-v $sunbird_dbs_path/cassandra/logs:/opt/cassandra/logs \
-v $sunbird_dbs_path/cassandra/backups:/mnt/backups \
--network bridge cassandra:3.11.8 
```
For network, we can use the existing network or create a new network using the following command and use it.
```shell
docker network create sunbird_db_network
```
3. To start cassandra cypher shell run the below command.
```shell
docker exec -it sunbird_cassandra cqlsh
```
4. To ssh to cassandra docker container, run the below command.
```shell
docker exec -it sunbird_cassandra /bin/bash
```
5. Load seed data to cassandra using the instructions provided in the [link](master-data/loading-seed-data.md#loading-seed-data-to-cassandra-database)

### Running kafka using docker:
1. Kafka stores information about the cluster and consumers into Zookeeper. ZooKeeper acts as a coordinator between them. we need to run two services(zookeeper & kafka), Prepare your docker-compose.yml file using the following reference.
```shell
version: '3'

services:
  zookeeper:
    image: 'wurstmeister/zookeeper:latest'
    container_name: zookeeper
    ports:
      - "2181:2181"    
    environment:
      - KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:2181     
    
  kafka:
    image: 'wurstmeister/kafka:2.12-1.0.1'
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
```
2. Go to the path where docker-compose.yml placed and run the below command to create and run the containers (zookeeper & kafka).
```shell
docker-compose -f docker-compose.yml up -d
```
3. To start kafka docker container shell, run the below command.
```shell
docker exec -it kafka sh
```
Go to path /opt/kafka/bin, where we will have executable files to perform operations(creating topics, running producers and consumers, etc).
Example:
```shell
kafka-topics.sh --create --zookeeper zookeeper:2181 --replication-factor 1 --partitions 1 --topic test_topic 
```

### Running Content Service:

### (Content V3+V4 APIs, Collection V4 APIs, Assets V4 APIs, Channel V3 APIs, License V3 APIs, Event V4 APIs, EventSet V4 APIs)

**Graph Database**: Uses JanusGraph by default. Configure in `application.conf`:
```hocon
graph.read.route.domain = "localhost:8182"
graph.write.route.domain = "localhost:8182"
```

1. Go to the path: /knowledge-platform and run the below maven command to build the application.
```shell
mvn clean install -DskipTests
```
2. Go to the path: /knowledge-platform/content-api/content-service and run the below maven command to run the netty server.
```shell
mvn play2:run
```
3. Using the below command we can verify whether the databases(janusgraph,redis & cassandra) connection is established or not. If all connections are good, health is shown as 'true' otherwise it will be 'false'.
```shell
curl http://localhost:9000/health
```

**Expected Health Response**:
```json
{
  "checks": {
    "cassandra": true,
    "redis": true,
    "janusgraph": true
  },
  "healthy": true
}
```

### Running Assets/Composite Search Service:

**Graph Database**: Uses JanusGraph for all graph operations.

1. Go to the path: /knowledge-platform and run the below maven command to build the application.
```shell
mvn clean install -DskipTests
```
2. Go to the path: /knowledge-platform/search-api/search-service and run the below maven command to run the netty server.
```shell
mvn play2:run
```
3. Using the below command we can verify whether the databases(janusgraph,redis & cassandra) connection is established or not. If all connections are good, health is shown as 'true' otherwise it will be 'false'.
```shell
curl http://localhost:9000/health
```

### Running Object Category Service:

**Graph Database**: Uses JanusGraph for taxonomy and object category management.

1. Go to the path: /knowledge-platform and run the below maven command to build the application.
```shell
mvn clean install -DskipTests
```
2. Go to the path: /knowledge-platform/taxonomy-api/taxonomy-service and run the below maven command to run the netty server.
```shell
mvn play2:run
```
3. Using the below command we can verify whether the databases(janusgraph,redis & cassandra) connection is established or not. If all connections are good, health is shown as 'true' otherwise it will be 'false'.
```shell
curl http://localhost:9000/health
```

---

## 📚 Documentation

- **[JanusGraph Migration Guide](JANUSGRAPH-MIGRATION-COMPLETE.md)** - Complete migration details
- **[JanusGraph Implementation](JANUSGRAPH-IMPLEMENTATION-COMPLETE.md)** - Previous implementation status
- **[Knowledge Setup Guide](KNOWLG-SETUP.md)** - Detailed setup instructions
- **[Master Data Loading](master-data/loading-seed-data.md)** - Database seeding

---

### GitHub Actions Workflow Prerequisites

To ensure the GitHub Actions workflows in this repository function correctly, the following prerequisites must be met:

1. **Secrets Configuration**:
   - Ensure the secrets are configured in your GitHub repository, depending on the value of `REGISTRY_PROVIDER`. The workflow will push the image to the respective container registry if the required credentials are provided.

   - Note: If No REGISTRY_PROVIDER is provided the image will be pushed to GHCR.

    #### GCP (Google Cloud Platform)
    - `REGISTRY_PROVIDER`: Set to `gcp`
    - `GCP_SERVICE_ACCOUNT_KEY`: Base64-encoded service account key for GCP.
    - `REGISTRY_NAME`: GCP registry name (e.g., `asia-south1-docker.pkg.dev`).
    - `REGISTRY_URL`: URL of the GCP container registry (e.g., `asia-south1-docker.pkg.dev/<project_id>/<repository_name>`).

    #### DockerHub
    - `REGISTRY_PROVIDER`: Set to `dockerhub`
    - `REGISTRY_USERNAME`: DockerHub username.
    - `REGISTRY_PASSWORD`: DockerHub password.
    - `REGISTRY_NAME`: DockerHub registry name (e.g., `docker.io`).
    - `REGISTRY_URL`: URL of the DockerHub registry (e.g., `docker.io/<username>`).

    #### Azure Container Registry (ACR)
    - `REGISTRY_PROVIDER`: Set to `azure`
    - `REGISTRY_USERNAME`: ACR username (service principal or admin username).
    - `REGISTRY_PASSWORD`: ACR password (service principal secret or admin password).
    - `REGISTRY_NAME`: ACR registry name (e.g., `myregistry.azurecr.io`).
    - `REGISTRY_URL`: URL of the ACR registry (e.g., `myregistry.azurecr.io`).

    #### GitHub Container Registry (GHCR)
    - `REGISTRY_PROVIDER`: Set to any value other than above (default is GHCR)
    - No additional secrets are required. The workflow uses the built-in `GITHUB_TOKEN` provided by GitHub Actions for authentication.

2. **Environment Variables**:
   - The following environment variables must be set in the repository or workflow:
     - `CLOUD_STORE_GROUP_ID`: The group ID for cloud storage dependencies.
     - `CLOUD_STORE_ARTIFACT_ID`: The artifact ID for cloud storage dependencies.
     - `CLOUD_STORE_VERSION`: The version of the cloud storage dependencies.

Ensure these secrets and variables are added to the repository settings under **Settings > Secrets and variables > Actions**.
By ensuring these prerequisites are met, the workflows in this repository will execute successfully.