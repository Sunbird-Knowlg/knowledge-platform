#!/bin/bash
TAG=$1
docker rmi $(docker images -a | grep taxonomy-service | awk '{print $1":"$2}')
docker rmi $(docker images -a | grep content-service | awk '{print $1":"$2}')
docker rmi $(docker images -a | grep search-service | awk '{print $1":"$2}')

# Taxonomy Service
cd taxonomy-api/taxonomy-service
mvn play2:dist
cd ../..
docker build -f build/taxonomy-service/Dockerfile  -t taxonomy-service:${TAG} .

# Content Service
cd content-api/content-service
mvn play2:dist
cd ../..
docker build -f build/content-service/Dockerfile -t content-service:${TAG} .

# Search Service
cd search-api/search-service
mvn play2:dist
cd ../..
docker build -f build/search-service/Dockerfile -t search-service:${TAG} .
