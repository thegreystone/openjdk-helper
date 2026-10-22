# Documentation

For installing OpenJDK Helper in an AI client, see the [project README](../README.md#installing).

## Using the tools

- [Tools and workflows](tools.md): the 27 MCP tools, contributor and project-lead examples, REST endpoints and pagination.
- [Configuration](configuration.md): transports, upstream URLs, optional tokens, caching and availability.

## Developing and operating the service

[![Java 25+](https://img.shields.io/badge/Java-25%2B-blue)](https://adoptium.net/)
[![Quarkus](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fthegreystone%2Fopenjdk-helper%2Fmain%2Fpom.xml&query=%2F%2F*%5Blocal-name%28%29%3D%27quarkus.platform.version%27%5D&label=Quarkus&color=blueviolet)](https://quarkus.io/)
[![GraalVM Native](https://img.shields.io/badge/GraalVM-native--image-orange)](https://www.graalvm.org/)

- [Development](development.md): JVM and native builds, tests, local stdio and MCPB packaging.
- [Deployment](deployment.md): container images, reverse proxies and multiple replicas.
- [Releases](releases.md): release artifacts, Docker publication and platform signing.
- [Plugin development](plugin.md): portable plugin packaging, local marketplace installation and OpenAI submission.

Run build, test, packaging and release commands from the repository root unless a guide states otherwise.
