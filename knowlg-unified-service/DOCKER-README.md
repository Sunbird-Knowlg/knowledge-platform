# Knowlg Unified Service - Docker Deployment

## Docker Image Build

The Docker image has been successfully built and includes:
- All Assessment APIs (Question, QuestionSet, ItemSet - v4 and v5)
- All Taxonomy APIs (Framework, Category, Channel, Term, Lock, ObjectCategory, etc.)
- All Content APIs (Content, Collection, Asset, App, Event, EventSet, License, etc.)
- All Search APIs (Search, AuditHistory)
- Complete schema support from both knowledge-platform and inquiry-api-service

## Image Details

- **Image Name**: `sunbird/knowlg-unified-service:latest`
- **Base Image**: eclipse-temurin:11.0.20.1_1-jdk-focal (Java 11)
- **Image Size**: ~1.52GB
- **Port**: 9000

## Quick Start

### Run with Docker:

```bash
docker run -d \
  --name knowlg-unified-service \
  -p 9000:9000 \
  -e JAVA_OPTIONS="-Xms512m -Xmx2048m" \
  sunbird/knowlg-unified-service:latest
```

### Run with Docker Compose:

```bash
cd /Users/sanketikam4/November/v2/knowledge-platform/knowlg-unified-service
docker-compose up -d
```

### Check Logs:

```bash
docker logs -f knowlg-unified-service
```

### Stop the Service:

```bash
docker stop knowlg-unified-service
```

Or with docker-compose:
```bash
docker-compose down
```

## API Endpoints

The service exposes all APIs on port 9000:

### Assessment APIs:
- `GET/POST /question/v4/*` - Question management (v4)
- `GET/POST /questionset/v4/*` - QuestionSet management (v4)
- `GET/POST /itemset/v3/*` - ItemSet management (v3)
- `GET/POST /question/v5/*` - Question management (v5)
- `GET/POST /questionset/v5/*` - QuestionSet management (v5)

### Taxonomy APIs:
- `GET/POST /framework/v3/*` - Framework management
- `GET/POST /category/v3/*` - Category management
- `GET/POST /channel/v3/*` - Channel management
- `GET/POST /term/v3/*` - Term management

### Content APIs:
- `GET/POST /content/v3/*` - Content management
- `GET/POST /collection/v3/*` - Collection management
- `GET/POST /asset/v3/*` - Asset management

### Search APIs:
- `POST /composite/v3/search` - Composite search

### Health Check:
- `GET /health` - Service health status

## Configuration

The service uses the application.conf from the distribution. To override configurations, you can:

1. Mount a custom config file:
```bash
docker run -d \
  --name knowlg-unified-service \
  -p 9000:9000 \
  -v /path/to/custom/application.conf:/home/sunbird/knowlg-unified-service-1.0-SNAPSHOT/config/application.conf \
  sunbird/knowlg-unified-service:latest
```

2. Use environment variables:
```bash
docker run -d \
  --name knowlg-unified-service \
  -p 9000:9000 \
  -e JAVA_OPTIONS="-Xms512m -Xmx2048m -Dconfig.cassandra.host=cassandra-host" \
  sunbird/knowlg-unified-service:latest
```

## Rebuild Image

To rebuild the image after code changes:

```bash
cd /Users/sanketikam4/November/v2/knowledge-platform
# First build the distribution
cd knowlg-unified-service
mvn clean package -DskipTests && mvn play2:dist

# Then rebuild the Docker image
cd ..
docker build -f build/knowlg-unified-service/Dockerfile -t sunbird/knowlg-unified-service:latest .
```

## Notes

- Ensure Cassandra, Neo4j, Redis, and Elasticsearch are accessible if using external instances
- Default port is 9000, can be changed in docker-compose.yml
- All schemas are included in the image from both knowledge-platform/schemas and inquiry-api-service/test_schema
