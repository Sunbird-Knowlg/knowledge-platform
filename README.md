# Knowledge Platform

Play Framework APIs for the Sunbird Knowledge Platform. Each service exposes REST endpoints for managing content, taxonomy, search, and assessments, backed by JanusGraph, YugabyteDB, Elasticsearch, and Redis.

---

## Table of Contents

1. [Modules](#modules)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
   - [Step 1 — Clone the repository](#step-1--clone-the-repository)
   - [Step 2 — Start infrastructure](#step-2--start-infrastructure)
   - [Step 3 — Initialize YugabyteDB keyspaces](#step-3--initialize-yugabytedb-keyspaces)
   - [Step 4 — Initialize Elasticsearch indices](#step-4--initialize-elasticsearch-indices)
   - [Step 5 — Seed data](#step-5--seed-data)
   - [Step 6 — Build the project](#step-6--build-the-project)
   - [Step 7 — Run a service](#step-7--run-a-service)
4. [Redis (optional)](#redis-optional)
5. [Cloud Storage Configuration](#cloud-storage-configuration)
6. [CI/CD — GitHub Actions](#cicd--github-actions)

---

## Modules

| Module | Description |
|--------|-------------|
| `platform-core` | Shared libraries: graph engine, schema validators, actors, cloud storage |
| `ontology-engine` | Graph operations for content, taxonomy, and assessment nodes |
| `content-api/content-service` | Content and collection CRUD, hierarchy, publishing triggers |
| `taxonomy-api/taxonomy-service` | Frameworks, categories, terms, channels, licenses |
| `search-api/search-service` | Composite search across the knowledge graph via Elasticsearch |
| `assessment-api/assessment-service` | QuestionSets and assessment items |
| `knowlg-service` | Aggregator service bundling Content, Taxonomy, and Assessment APIs into a single runtime |
| `platform-modules` | MIME-type management, URL management, and import utilities |

---

## Prerequisites

Make sure these are installed before you begin:

- **Java 11** — verify with `java -version`
- **Maven 3.9+** — verify with `mvn -version`
- **Docker Desktop** — verify with `docker --version`
  - Allocate at least **6 GB RAM** to Docker Desktop (Settings > Resources > Memory). The default 3.8 GB is not enough.
- **Git** — verify with `git --version`

---

## Local Development Setup

Follow these steps in order. The full setup takes about 5 minutes.

### Step 1 — Clone the repository

```shell
git clone https://github.com/Sunbird-Knowlg/knowledge-platform.git
cd knowledge-platform
```

### Step 2 — Start infrastructure

```shell
cd docker
docker compose up -d
```

This starts **YugabyteDB**, **JanusGraph**, **Elasticsearch**, and **Kafka**.

Wait about **90 seconds** for everything to initialize (YugabyteDB starts first, then JanusGraph connects to it and creates the graph schema). You can check progress with:

```shell
docker compose ps                  # all containers should show "Up"
docker logs janusgraph | grep "SCHEMA INITIALIZATION"
# Expected: --- SCHEMA INITIALIZATION COMPLETE ---
```

### Step 3 — Initialize YugabyteDB keyspaces

Still inside the `docker/` directory, run the migration script to create the required keyspaces and tables:

```shell
./init-yugabyte.sh
```

This downloads CQL migration files from [sunbird-spark-installer](https://github.com/Sunbird-Spark/sunbird-spark-installer/tree/develop/scripts/sunbird-yugabyte-migrations/sunbird-knowlg) and executes them. By default it uses `dev` as the keyspace prefix (e.g. `dev_content_store`) and the `develop` branch.

```shell
./init-yugabyte.sh sb           # use 'sb' as keyspace prefix instead
./init-yugabyte.sh dev main     # use a different branch
```

You only need to run this once. Run it again after `docker compose down -v` (which deletes volumes).

### Step 4 — Initialize Elasticsearch indices

Still inside the `docker/` directory, run the Elasticsearch init script to create the required indices and mappings:

```shell
./init-elasticsearch.sh
```

This downloads index and mapping definitions from [sunbird-devops](https://github.com/project-sunbird/sunbird-devops/tree/release-8.0.0/ansible/roles/es7-mapping/files) and applies them via the Elasticsearch REST API. By default it uses the `release-8.0.0` branch.

```shell
./init-elasticsearch.sh release-9.0.0    # use a different branch
```

You only need to run this once. Run it again after `docker compose down -v` (which deletes volumes).

### Step 5 — Populate seed data

After initializing keyspaces (Step 3), populate the database with seed data:

```shell
# Populate data into the default 'dev' environment
bash docker/data-seed/scripts/restore-all.sh

# Populate data into a custom environment (e.g., 'sb')
bash docker/data-seed/scripts/restore-all.sh sb

# Optionally, truncate tables before loading to ensure a clean state
FORCE_RESET=true bash docker/data-seed/scripts/restore-all.sh
```

### Step 6 — Build the project

Go back to the repository root and build:

```shell
cd ..
mvn clean install -DskipTests
```

This takes a few minutes the first time (Maven downloads dependencies). A successful build ends with `BUILD SUCCESS`.

To build for a specific cloud provider:
```shell
mvn clean install -DskipTests -Paws   # AWS S3
mvn clean install -DskipTests -Pgcp   # Google Cloud Storage
mvn clean install -DskipTests -Poci   # Oracle Cloud Infrastructure
```

### Step 7 — Run a service

> **Required:** Set [cloud storage environment variables](#cloud-storage-configuration) before starting any service. The `StorageModule` initializes eagerly on startup and the service will fail if the variables are empty. If you don't have real credentials, set placeholder values — storage will only fail when you actually upload/download content:
> ```shell
> export cloud_storage_type=azure
> export cloud_storage_key=placeholder
> export cloud_storage_secret=placeholder
> export cloud_storage_container=placeholder
> ```

You can either run services individually or run Content, Taxonomy, and Assessment together via `knowlg-service`.

#### Option A — Run an individual service

| Service | Module Path | Default Port |
|---------|-------------|--------------|
| **Content Service** | `content-api/content-service` | 9000 |
| **Search Service** | `search-api/search-service` | 9000 |
| **Taxonomy Service** | `taxonomy-api/taxonomy-service` | 9000 |
| **Assessment Service** | `assessment-api/assessment-service` | 9000 |

Example — running Taxonomy Service:

**Linux:**
```shell
cd taxonomy-api/taxonomy-service
mvn play2:run
```

**macOS:**
```shell
cd taxonomy-api/taxonomy-service
mvn play2:dist
cd target
tar xvzf taxonomy-service-1.0-SNAPSHOT-dist.zip
cd taxonomy-service-1.0-SNAPSHOT
./start
```

#### Option B — Run Content, Taxonomy, and Assessment together

The `knowlg-service` module bundles Content, Taxonomy, and Assessment into a single Play application.

**Linux:**
```shell
cd knowlg-service
mvn play2:run
```

**macOS:**
```shell
cd knowlg-service
mvn play2:dist
cd target
tar xvzf knowlg-service-1.0-SNAPSHOT-dist.zip
cd knowlg-service-1.0-SNAPSHOT
./start
```

#### Verify it works

```shell
curl http://localhost:9000/health
```

You should get a `200 OK` response.

---

### Stopping and resetting

```shell
cd docker
docker compose down            # stop containers, keep data
docker compose down -v         # stop containers and delete all data
```

---

## Redis (optional)

Redis is disabled by default. All service `application.conf` files ship with `redis.enable = false`, so the services read directly from the graph database.

To enable Redis caching:

1. Start Redis:
   ```shell
   cd docker
   docker compose --profile redis up -d
   ```

2. Set `redis.enable = true` in the `application.conf` of the service you are running.

---

## Cloud Storage Configuration

Cloud storage is needed for uploading/downloading content artifacts. If you are only testing APIs that don't involve file uploads, you can skip this.

Set these environment variables before running a service:

#### Azure (default)
```shell
export cloud_storage_type=azure
export cloud_storage_auth_type=ACCESS_KEY
export cloud_storage_key=your-account-name
export cloud_storage_secret=your-account-key
export cloud_storage_container=your-container-name
```

#### AWS S3
```shell
export cloud_storage_type=aws
export cloud_storage_auth_type=ACCESS_KEY
export cloud_storage_key=your-access-key-id
export cloud_storage_secret=your-secret-access-key
export cloud_storage_region=ap-south-1
export cloud_storage_container=your-s3-bucket-name
```

#### Google Cloud Storage
```shell
export cloud_storage_type=gcloud
export cloud_storage_auth_type=ACCESS_KEY
export cloud_storage_key=your-client-email
export cloud_storage_secret=/path/to/key.json
export cloud_storage_container=your-gcs-bucket-name
```

---

## CI/CD — GitHub Actions

The project uses **GitHub Actions** for CI/CD. Workflows are defined in `.github/workflows/` and triggered on tag push.

### Required variables (Settings > Secrets and variables > Actions)

| Variable | Description |
|----------|-------------|
| `REGISTRY_PROVIDER` | Registry type: `gcp`, `dockerhub`, `azure`, `aws`, or `ghcr` |
| `REGISTRY_URL` | Container registry URL |
| `CLOUD_STORE_GROUP_ID` | Cloud storage SDK group ID |
| `ARTIFACT_ID` | Cloud storage SDK artifact ID |
| `VERSION` | Cloud storage SDK version |

### Registry credentials

**GitHub Container Registry (GHCR)** — default, no setup needed. Uses the built-in `GITHUB_TOKEN`.

**DockerHub**

| Secret | Example |
|--------|---------|
| `REGISTRY_USERNAME` | `myusername` |
| `REGISTRY_PASSWORD` | DockerHub password or access token |
| `REGISTRY_NAME` | `docker.io` |

**Azure Container Registry**

| Secret | Example |
|--------|---------|
| `REGISTRY_USERNAME` | ACR username |
| `REGISTRY_PASSWORD` | ACR password |
| `REGISTRY_NAME` | `myregistry.azurecr.io` |

**GCP Artifact Registry**

| Secret | Example |
|--------|---------|
| `GCP_SERVICE_ACCOUNT_KEY` | Base64-encoded service account JSON key |
| `REGISTRY_NAME` | `asia-south1-docker.pkg.dev` |

**Amazon ECR**

| Secret | Example |
|--------|---------|
| `AWS_ACCESS_KEY_ID` | AWS access key ID |
| `AWS_SECRET_ACCESS_KEY` | AWS secret access key |
| `AWS_REGION` | `us-east-1` |
