
Below are the steps to set up the Sunbird Knowlg Microservices, DBs with seed data and Jobs. It uses a local Kubernetes cluster deploy the required services.

### Prerequisites:
* Java 11
* Maven
* Docker
* Minikube - It implements a local Kubernetes cluster on macOS, Linux, and Windows.
* KubeCtl - The Kubernetes command-line tool

### Prepare folders for database data and logs

```shell
mkdir -p ~/sunbird-dbs/neo4j ~/sunbird-dbs/cassandra ~/sunbird-dbs/redis ~/sunbird-dbs/es ~/sunbird-dbs/kafka
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

### Load Docker Images to Minikube Cluster
```shell
minikube image load neo4j:3.3.0
minikube image load taxonomy-service:R5.0.0
```

### Create Namespace 
Create the namespaces to deploy the API microservices, DBs and Jobs.
1. knowlg-api
2. knowlg-db
3. knowlg-job

```shell
kubectl create namespace knowlg-api
kubectl create namespace knowlg-db
kubectl create namespace knowlg-job
```

### Setup Databases
Please run the below `helm` commands to set up the required databases within the kubernets cluster.
It requires the below DBs for Knowlg.
1. Neo4J
2. Cassandra
3. Elasticsearch
4. Kafka
5. Redis

```shell
cd kubernetes
helm install redis sunbird-dbs/redis -n knowlg-db
helm install neo4j sunbird-dbs/neo4j -n knowlg-db
helm install cassandra sunbird-dbs/cassandra -n knowlg-db
```
**Note:** 
1. The `helm` charts for Kafka, Elasticsearch will be added soon.
2. The documentation will be updated to enable seed data while starting the service. 

### Define ConfigMap
We use the configmap to load the configuration for the microservices.

#### ConfigMap for Taxonomy-Service
Use the below commands to load the configmap of taxonomy-service.
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