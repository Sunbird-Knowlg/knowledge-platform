#!/bin/bash
# Build script for knowlg-unified-service Docker image
set -eo pipefail

build_tag=${1:-latest}
name=${2:-knowlg-unified-service}
node=${3:-local}
org=${4:-sunbird}

echo "Building knowlg-unified-service Docker image..."
echo "Tag: ${build_tag}"
echo "Organization: ${org}"

# Build the Maven distribution first
echo "Building Maven distribution..."
cd knowlg-unified-service
mvn clean package -DskipTests
mvn play2:dist
cd ..

# Build Docker image
echo "Building Docker image..."
docker build -f build/${name}/Dockerfile \
  --label commitHash=$(git rev-parse --short HEAD) \
  -t ${org}/${name}:${build_tag} .

# Create metadata file
echo "{\"image_name\" : \"${name}\", \"image_tag\" : \"${build_tag}\", \"node_name\" : \"$node\"}" > metadata.json

echo "Docker image built successfully: ${org}/${name}:${build_tag}"
docker images | grep ${name}
