# knowledge-platform

Repository for Knowledge Platform - 2.0

## Content-Service local setup
This readme file contains the instruction to set up and run the content-service in local machine.
### Prerequisites:
* Neo4j
* Redis
* Cassandra

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
--name -  Name your container (avoids generic id)

-p - Specify container ports to expose

Using the -p option with ports 7474 and 7687 allows us to expose and listen for traffic on both the HTTP and Bolt ports. Having the HTTP port means we can connect to our database with Neo4j Browser, and the Bolt port means efficient and type-safe communication requests between other layers and the database.

-d - This detaches the container to run in the background, meaning we can access the container separately and see into all of its processes.

-v - The next several lines start with the -v option. These lines define volumes we want to bind in our local directory structure so we can access certain files locally.

--env - Set config as environment variables for Neo4j database

Using Docker on Windows will also need a couple of additional configurations because the default 0.0.0.0 address that is resolved with the above command does not translate to localhost in Windows. We need to add environment variables to our command above to set the advertised addresses.

By default, Neo4j requires authentication and requires us to first login with neo4j/neo4j and set a new password. We will skip this password reset by initializing the authentication none when we create the Docker container using the --env NEO4J_AUTH=none.

3. After running the above command, neo4j instance will be created and container starts running, we can verify the same by accessing neo4j browser(http://localhost:7474/browser).

4. To SSH to neo4j docker container, run the below command.
```shell
docker exec -it sunbird_neo4j bash
```

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
### Running content-service:
1. Go to the path: /knowledge-platform and run the below maven command to build the application.
```shell
mvn clean install -DskipTests
```
2. Go to the path: /knowledge-platform/content-api/content-service and run the below maven command to run the netty server.
```shell
mvn play2:run
```
3. Using the below command we can verify whether the databases(neoj,redis & cassandra) connection is established or not. If all connections are good, health is shown as 'true' otherwise it will be 'false'.
```shell
curl http://localhost:9000/health
```
4. Run the following queries in neo4j DB to create unique constraint and indexes.
```cql
CREATE CONSTRAINT ON (domain:domain) ASSERT domain.IL_UNIQUE_ID IS UNIQUE;
CREATE INDEX ON :domain(IL_FUNC_OBJECT_TYPE);
CREATE INDEX ON :domain(IL_SYS_NODE_TYPE);
```
5. Run the following queries in cassandra DB to create keyspace and table.
```cql
CREATE KEYSPACE IF NOT EXISTS category_store WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'} AND durable_writes = true;
CREATE TABLE IF NOT EXISTS category_store.category_definition_data (
    identifier text PRIMARY KEY,
    forms map<text, text>,
    objectmetadata map<text, text>
);
```
