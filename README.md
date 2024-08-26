[![CI](https://github.com/thegreystone/openjdk-helper/actions/workflows/ci.yml/badge.svg)](https://github.com/thegreystone/openjdk-helper/actions/workflows/ci.yml)
[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.12.3-blue.svg?style=flat&logo=quarkus)](https://quarkus.io/)
[![Docker Pulls](https://img.shields.io/docker/pulls/greystone/openjdk-helper.svg)](https://hub.docker.com/r/greystone/openjdk-helper)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)
[![Version](https://img.shields.io/docker/v/greystone/openjdk-helper?sort=semver)](https://hub.docker.com/r/greystone/openjdk-helper)

# openjdk-helper

This is a simple service meant to help with OpenJDK project activities. It is being used by my [OpenJDK Project Assistant](https://chatgpt.com/g/g-cdK5pudqC-openjdk-project-assistant) Custom GPT. 

## Using the openjdk-helper

The openjdk helper is up and running at https://api.hirt.se/openjdk/version. It is available and ready to use. 

The api is described here:  
https://api.hirt.se/openjdk/swagger-ui/

Or in openapi format here:  
https://api.hirt.se/openjdk/openapi/

## Running the application in dev mode

You can run the application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.  
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/openjdk-helper-(version)-SNAPSHOT-runner`

## Creating the docker image
You can create a docker image using:

```shell script
mvnw clean package -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true
```
To create one with a native image:

```shell script
mvnw clean package -Pnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true
```

Run the image using:
```shell script
docker run -i --rm -p 8080:8080 greystone/openjdk-helper:latest
```

## Publishing images (for maintainers)
To create docker images for multiple platforms and push them to Docker hub:

```shell script
./release.sh
```
