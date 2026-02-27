# Cloud Storage SDK — Build & Docker Image Guide

This document explains how the cloud-storage-sdk 2.0.0 CSP (Cloud Storage Provider)
selection works during Maven builds and Docker image creation, following the
**Option A — Build-Time CSP Selection** pattern from the SDK integration guide.

---

## Overview

The platform uses `cloud-storage-sdk 2.0.0`, a modular Java SDK where:

- `cloud-storage-sdk-api` — compile-time interface (always present)
- `cloud-storage-sdk-<csp>` — one runtime implementation per provider (AWS, Azure, GCP, OCI)

The runtime implementation is discovered automatically via **Java ServiceLoader** — no code
changes are required when switching providers. Only one CSP jar should be on the classpath
at runtime. The correct jar is selected at **Maven build time** using profiles.

---

## Affected Modules

Only one module declares CSP dependencies — all other modules consume cloud storage
indirectly through it:

| Module | Has CSP dependency | Notes |
|--------|-------------------|-------|
| `platform-modules/mimetype-manager` | **Yes** — Maven profiles | The only module with `cloud-storage-sdk-*` dependencies |
| `content-api` | No | Gets CSP jar transitively via `mimetype-manager` |
| `knowlg-service` | No | Gets CSP jar transitively via `content-api` |
| `assessment-api` | No | No cloud storage usage |
| `search-api` | No | No cloud storage usage |
| `taxonomy-api` | No | No cloud storage usage |

---

## Maven Profiles

`platform-modules/mimetype-manager/pom.xml` defines four mutually exclusive profiles.
**`azure` is the default** (activated when no `-P` flag is supplied).

| Profile | CSP jar bundled | Activate with |
|---------|----------------|---------------|
| `azure` *(default)* | `cloud-storage-sdk-azure` | *(no flag needed)* or `-Pazure` |
| `aws` | `cloud-storage-sdk-aws` | `-Paws` |
| `gcp` | `cloud-storage-sdk-gcp` | `-Pgcp` |
| `oci` | `cloud-storage-sdk-oci` | `-Poci` |

### Build Commands

```bash
# Azure (default) — no explicit profile needed
mvn clean install -DskipTests

# Explicit CSP selection
mvn clean install -DskipTests -Pazure
mvn clean install -DskipTests -Paws
mvn clean install -DskipTests -Pgcp
mvn clean install -DskipTests -Poci
```

Profiles propagate to all sub-modules automatically when building from the root or
from a parent module (e.g., `content-api`), so `-Paws` at the root produces an
`aws`-flavoured dist ZIP for every service.

---

## Docker Image Build

All five service Dockerfiles use an `ARG CSP=azure` build argument.
This sets the `cloud_storage_type` environment variable baked into the image.

**The Maven profile and the Docker `--build-arg CSP` must always match.**

### Build Pattern

```bash
# 1. Build Maven artifacts with the target CSP profile
mvn clean install -DskipTests -P<csp>

# 2. Build Docker images passing the same CSP value
docker build --build-arg CSP=<csp> \
  -t sunbird/<service>:<csp>-<tag> \
  -f build/<service>/Dockerfile .
```

### Examples

```bash
# Azure (production default)
mvn clean install -DskipTests -Pazure
docker build --build-arg CSP=azure -t sunbird/content-service:azure-1.0 \
  -f build/content-service/Dockerfile .

# AWS
mvn clean install -DskipTests -Paws
docker build --build-arg CSP=aws -t sunbird/content-service:aws-1.0 \
  -f build/content-service/Dockerfile .

# GCP
mvn clean install -DskipTests -Pgcp
docker build --build-arg CSP=gcp -t sunbird/content-service:gcp-1.0 \
  -f build/content-service/Dockerfile .

# OCI
mvn clean install -DskipTests -Poci
docker build --build-arg CSP=oci -t sunbird/content-service:oci-1.0 \
  -f build/content-service/Dockerfile .
```

The same `-P<csp>` / `--build-arg CSP=<csp>` pair applies to all five services:
`content-service`, `knowlg-service`, `assessment-service`, `search-service`,
`taxonomy-service`.

---

## Environment Variables in Docker Images

Each image is built with these environment variables:

| Variable | Default (baked in) | Purpose |
|----------|-------------------|---------|
| `cloud_storage_type` | value of `ARG CSP` (azure) | Tells `StorageModule` which CSP to initialise |
| `cloud_storage_auth_type` | `OIDC` | Auth method — OIDC for Kubernetes Workload Identity |

Both can be **overridden at runtime** via Kubernetes ConfigMap, Secret, or `docker run -e`:

```bash
# Local development with static credentials
docker run \
  -e cloud_storage_type=azure \
  -e cloud_storage_auth_type=ACCESS_KEY \
  -e cloud_storage_key=<your-key> \
  -e cloud_storage_secret=<your-secret> \
  sunbird/content-service:azure-1.0
```

### Auth Types

| `cloud_storage_auth_type` | When to use |
|--------------------------|-------------|
| `OIDC` | Production on Kubernetes (Workload Identity / IRSA / Workload Identity Federation) — no static credentials |
| `ACCESS_KEY` | Local development or CI environments with static key/secret |
| `IAM` | AWS EC2 instance profile or Azure Managed Identity |
| `IAM_ROLE` | AWS STS assumed role |
| `INSTANCE_PROFILE` | AWS EC2 or OCI Instance Principal |

---

## How CSP Selection Works at Runtime

1. The Maven profile places exactly one `cloud-storage-sdk-<csp>-2.0.0.jar` into the
   service's `lib/` directory inside the dist ZIP.
2. At JVM startup, `StorageServiceFactory.getStorageService(config)` calls
   `java.util.ServiceLoader` to discover the `IStorageService` implementation from
   `META-INF/services/org.sunbird.cloud.storage.IStorageService` inside that jar.
3. `StorageModule` (Guice) reads `cloud_storage_type` and `cloud_storage_auth_type`
   from Play's config and builds a `StorageConfig` to initialise the discovered service.
4. The resulting `IStorageService` singleton is injected wherever `StorageService`
   is used across the platform.

---

## CI/CD Guidance

Define `CSP` as a pipeline parameter (default `azure`). A single pipeline job can
produce all four images in parallel:

```yaml
# Example — CI pseudo-code
matrix:
  csp: [azure, aws, gcp, oci]

steps:
  - run: mvn clean install -DskipTests -P${{ matrix.csp }}
  - run: |
      docker build \
        --build-arg CSP=${{ matrix.csp }} \
        -t sunbird/content-service:${{ matrix.csp }}-${{ env.BUILD_TAG }} \
        -f build/content-service/Dockerfile .
```

---

## Local Development (Quick Reference)

```bash
# Build with default Azure profile (recommended starting point)
mvn clean install -DskipTests

# Run a service locally pointing at a dev blob container
cd content-api/content-service
mvn play2:run \
  -Dcloud_storage_type=azure \
  -Dcloud_storage_auth_type=ACCESS_KEY \
  -Dcloud_storage_key=<dev-key> \
  -Dcloud_storage_secret=<dev-secret> \
  -Dcloud_storage_container=<dev-container>
```
