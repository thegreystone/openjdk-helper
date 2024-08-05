[![CI](https://github.com/thegreystone/slogan-generator/actions/workflows/ci.yml/badge.svg)](https://github.com/thegreystone/openjdk-helper/actions/workflows/ci.yml)
[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.12.3-blue.svg?style=flat&logo=quarkus)](https://quarkus.io/)
[![Docker Pulls](https://img.shields.io/docker/pulls/greystone/slogan-generator.svg)](https://hub.docker.com/r/greystone/openjdk-helper)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)
[![Version](https://img.shields.io/docker/v/greystone/slogan-generator?sort=semver)](https://hub.docker.com/r/greystone/openjdk-helper)

# openjdk-helper

This is a simple service meant to help with OpenJDK activities. It is meant to be used by my CustomGPT:

## Using the openjdk-helper

The slogan generator is up and running at https://api.hirt.se/openjdk. It is available and ready to use. 

The api is described here:  
https://api.hirt.se/openjdk/swagger-ui/

Or in openapi format here:  
https://api.hirt.se/openjdk/openapi/

Here's an example on how to get a simple slogan text for OpenJDK:  
https://api.hirt.se/openjdk/text?item=OpenJDK

Here is an example of how I use it on my homepage https://hirt.se:
```shell script
https://api.hirt.se/openjdk/describeUser?userid=hirt
```

## Running the application in dev mode

You can run the application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

Browse to localhost:8080/test to try it out.

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

You can then execute your native executable with: `./target/slogan-generator-(version)-SNAPSHOT-runner`

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
docker run -i --rm -p 8080:8080 greystone/slogan-generator:latest
```

## Publishing images (for maintainers)
To create docker images for multiple platforms and push them to Docker hub:

```shell script
./release.sh
```