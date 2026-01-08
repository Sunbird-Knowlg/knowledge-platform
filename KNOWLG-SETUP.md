
Below are the steps to set up the Sunbird Knowlg Microservices, DBs with seed data and Jobs. It uses a local Kubernetes cluster deploy the required services.

### Prerequisites:
* Java 11
* Maven
* Docker
* Minikube - It implements a local Kubernetes cluster on macOS, Linux, and Windows.
* KubeCtl - The Kubernetes command-line tool

### Prepare folders for database data and logs

```shell
mkdir -p ~/sunbird-dbs/janusgraph ~/sunbird-dbs/yugabyte ~/sunbird-dbs/redis ~/sunbird-dbs/es ~/sunbird-dbs/kafka
export sunbird_dbs_path=~/sunbird-dbs
```



### Docker Images of Knowlg MicroServices
Start Docker in your machine and create the Docker Images of below microservices using the shell script.
1. taxonomy-service
2. content-service
3. search-service

```shell
sh ./knowlg-docker-image.sh <TAG> # provide the TAG for the docker image.
```
**Note:** Please specify the TAG for the Docker Images and update the configuration in helm chart of respective deployment.

Check the Docker Images
```shell
docker image ls -a
```
**Output:**
```shell
❯❯ docker image ls -a
REPOSITORY                    TAG       IMAGE ID       CREATED          SIZE
assessment-service            R5.0.0    72a9cc1b2cc4   14 seconds ago   479MB
search-service                R5.0.0    24b7d8947a4f   23 seconds ago   465MB
content-service               R5.0.0    afcbc9c10fa3   33 seconds ago   556MB
taxonomy-service              R5.0.0    a8a24a6241f2   47 seconds ago   480MB
```

### Kubernetes Cluster Setup
Please use the minikube to quickly set up the kubernetes cluster in local machine.

```shell
minikube start
```

### Setup Databases Using Docker Compose
We now use Docker Compose for local development instead of Kubernetes/Minikube.
The required databases for Knowlg are:
1. **JanusGraph** (replaces Neo4j) - Graph database using Gremlin traversal language
2. **Yugabyte** (replaces Cassandra) - Distributed SQL with Cassandra CQL compatibility
3. Elasticsearch
4. Kafka
5. Redis

#### Start All Database Services
```shell
docker-compose up -d
```

This will start:
- **sunbird-yugabyte** - Yugabyte database on ports 9042 (YCQL), 5433 (YSQL)
- **sunbird-janusgraph** - JanusGraph with Gremlin Server on port 8182
- **sunbird-redis** - Redis on port 6379
- **sunbird-kafka** - Kafka on port 9092
- **sunbird-zookeeper** - Zookeeper on port 2181

#### Initialize Database Schema and Keyspaces
Run the setup script to create Yugabyte keyspaces and JanusGraph schema:

```shell
chmod +x setup-databases.sh
./setup-databases.sh
```

This script will:
- Create 7 Yugabyte keyspaces: content_store, hierarchy_store, question_store, dialcodes, category_store, janusgraph, sunbirddev_dialcode_store
- Initialize JanusGraph schema with vertex labels (Content, Concept, Domain) and edge labels (hasContent, associatedTo)
- Create necessary indices

#### Verify Database Connectivity
```shell
chmod +x test-connections.sh
./test-connections.sh
```

**Note:** 
- JanusGraph uses WebSocket protocol (ws://localhost:8182/gremlin) instead of Neo4j's Bolt protocol
- Yugabyte is fully Cassandra-compatible and uses the same CQL interface on port 9042
- See DATABASE-MIGRATION.md and JANUSGRAPH-QUERY-REFERENCE.md for migration details

### Define ConfigMap
We use configuration files for the microservices (Docker Compose approach - configurations are already updated in application.conf files).

**Note:** The following configurations have been updated to use Yugabyte and JanusGraph:
- `content-api/content-service/conf/application.conf`
- `taxonomy-api/taxonomy-service/conf/application.conf`
- `taxonomy-service-sbt/conf/application.conf`

Key configuration changes:
- **Yugabyte YCQL endpoint**: `cassandra.lp.connection="127.0.0.1:9042"`
- **JanusGraph Gremlin endpoint**: `route.domain="ws://localhost:8182/gremlin"`
- All Neo4j `bolt://` URLs replaced with JanusGraph `ws://` WebSocket endpoints

#### ConfigMap for Taxonomy-Service (Kubernetes deployment)
If using Kubernetes, use these commands to load the configmap:
1. `taxonomy-config` - this has the application configuration. Please update the variables with respect to your context and load.
2. `taxonomy-xml-config` - this has the logback configuration to handle the logs.

We have to update the below configurations in `taxonomy/templates/taxonomy-service_application.conf` specific to your context.

```shell
cd kubernetes
kubectl create configmap taxonomy-xml-config --from-file=taxonomy/taxonomy-service_logback.xml -n knowlg-api -o=yaml
kubectl create configmap taxonomy-config --from-file=taxonomy/taxonomy-service_application.conf -n knowlg-api  -o=yaml
```

### Run Taxonomy-Service
Use the `taxonomy` helm chart to run the taxonomy-service in local kubernetes cluster.

```shell
cd kubernetes
helm install taxonomy taxonomy -n knowlg-api
```
Use Port Forwarding to access the application in the cluster from local.

```shell
kubectl port-forward <pod-name> 9000:9000 -n knowlg-api
curl 'localhost:9000/health'
```

### Define ConfigMap for Content-Service
Use the below commands to load the configmap of content-Service.
1. `content-config` - this has the application configuration. Please update the variables with respect to your context and load.
2. `content-xml-config` - this has the logback configuration to handle the logs.

We have to update the below configurations in `content/templates/content-service_application` specific to your context.

```shell
cd kubernetes
kubectl create configmap content-xml-config --from-file=content/content-service_logback.xml -n knowlg-api -o=yaml
kubectl create configmap content-config --from-file=content/content-service_application.conf -n knowlg-api  -o=yaml
```

### Run Content-Service
Use the `taxonomy` helm chart to run the Content-Service in local kubernetes cluster.

```shell
cd kubernetes
helm install content content -n knowlg-api
```
Use Port Forwarding to access the application in the cluster from local.

```shell
kubectl port-forward <pod-name> 9000:9000 -n knowlg-api
curl 'localhost:9000/health'
```