#!/bin/bash
export DOCKER_BUILDKIT=1
docker buildx create --name multi-arch-builder --use
docker buildx inspect --bootstrap

# Extract version from pom.xml and remove -SNAPSHOT
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout | sed 's/-SNAPSHOT//')

# Update the version in pom.xml
mvn versions:set -DnewVersion=$VERSION
mvn versions:commit

# Build the project
./mvnw package

# Only building with the release tag - latest is built on each commit to GitHub
docker buildx build -f src/main/docker/Dockerfile.jvm --platform linux/amd64,linux/arm64 -t greystone/slogan-generator:$VERSION --push .

# Revert version in pom.xml to next development version
NEXT_DEV_VERSION="${VERSION%.*}.$((${VERSION##*.} + 1))-SNAPSHOT"
mvn versions:set -DnewVersion=$NEXT_DEV_VERSION
mvn versions:commit

echo "Released version $VERSION and updated the pom to the next development version $NEXT_DEV_VERSION"
