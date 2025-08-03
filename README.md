[![CI](https://github.com/thegreystone/openjdk-helper/actions/workflows/ci.yml/badge.svg)](https://github.com/thegreystone/openjdk-helper/actions/workflows/ci.yml)
[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.20.0-blue.svg?style=flat&logo=quarkus)](https://quarkus.io/)
[![Docker Pulls](https://img.shields.io/docker/pulls/greystone/openjdk-helper.svg)](https://hub.docker.com/r/greystone/openjdk-helper)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)
[![Version](https://img.shields.io/docker/v/greystone/openjdk-helper?sort=semver)](https://hub.docker.com/r/greystone/openjdk-helper)

# openjdk-helper

This is a simple service and MCP server meant to help with OpenJDK project activities. It is being used by my [OpenJDK Project Assistant](https://chatgpt.com/g/g-cdK5pudqC-openjdk-project-assistant) 
Custom GPT, and can also be used in stdio MCP mode, together with e.g. Claude Desktop.

## Using the openjdk-helper

The openjdk helper is up and running at https://api.hirt.se/openjdk/version. It is available and ready to use. 

The api is described here:  
https://api.hirt.se/openjdk/swagger-ui/

Or in openapi format here:  
https://api.hirt.se/openjdk/openapi/

## MCP Server

In addition to the OpenAPI interface, this service provides Model Context Protocol (MCP) integration for AI assistants like Claude, VS Code, and other MCP clients.
This enables AI assistants to directly access information about OpenJDK projects, people, and GitHub repositories.

### Available MCP Tools

The OpenJDK Helper offers these MCP tools:

- **version**: Get the service version information
- **searchPeople**: Search for people in the OpenJDK census by name or username
- **getPerson**: Get a person's details from the OpenJDK census by ID/username or a comma-separated list of IDs
- **searchProjects**: Search for projects in the OpenJDK census
- **getProject**: Get project details by ID
- **searchGroups**: Search for groups in the OpenJDK census
- **getGroup**: Get group details by ID
- **getGitHubRepos**: Get GitHub repositories for a user
- **getGitHubPulls**: Get pull requests for a GitHub repository

### Transport Options

The MCP functionality is available through multiple transport options:

#### 1. SSE (Server-Sent Events)

The SSE-based MCP endpoint is available at:

```
https://api.hirt.se/openjdk/mcp/sse
```

To connect using the MCP Inspector:

```bash
# Connect to the production server
npx @modelcontextprotocol/inspector https://api.hirt.se/openjdk/mcp --transport-type=sse

# Or for local development
npx @modelcontextprotocol/inspector http://localhost:8080/mcp --transport-type=sse
```

#### 2. Stdio

The openjdk-helper can also run in stdio mode, allowing direct integration via standard input/output:

```bash
# Enable stdio mode (disabled by default)
java -Dquarkus.mcp.server.stdio.enabled=true -jar target/openjdk-helper-[version]-runner.jar
```

### Integrating with AI Assistants

Some AI assistants can access the openjdk helper directly. Some will need a bit of work. Here are some examples.

#### ChatGPT / CustomGPT

A ChatGPT Custom GPT can easily use the service running at hirt.se to define new define new Actions.

An example schema is provided here: https://github.com/thegreystone/openjdk-helper/blob/main/examples/customgpt.txt

See also: [OpenJDK Project Assistant](https://chatgpt.com/g/g-cdK5pudqC-openjdk-project-assistant) and https://api.hirt.se/openjdk/swagger-ui/

#### Claude Desktop

Claude Desktop currently only supports MCP servers running in stdio mode, so the openjdk helper will have to be built and run by Claude Desktop.

First build the uber jar:
```bash
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

Then edit the Claude Desktop file named claude_desktop_config.json (on windows it can be found under C:\Users\\[UserName]\AppData\Roaming\Claude) to start the openjdk-helper, for example:

```bash
{
  "mcpServers": {
    "openjdk-helper": {
      "command": "java",
      "args": [
        "-Dquarkus.mcp.server.stdio.enabled=true",
        "-jar",
        "C:\\Users\\Marcus\\git\\me\\openjdk-helper\\target\\openjdk-helper-0.0.8-SNAPSHOT-runner.jar"
      ]
    }
  }
}
```

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
