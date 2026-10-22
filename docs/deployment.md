# Containers and reverse proxies

[Documentation](README.md) · [Installation](../README.md#installing)

Run build and publication commands from the repository root. See [development](development.md) for prerequisites, [configuration](configuration.md) for upstream endpoints and optional credentials, and [releases](releases.md) for release artifacts and signing.

Container creation is opt-in:

```sh
./mvnw package -Dquarkus.container-image.build=true
# Linux native container
./mvnw package -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true

docker run --rm -p 8080:8080 greystone/openjdk-helper:latest
```

Pushing a `v*` tag publishes two multi-architecture images (linux/amd64 and linux/arm64) through the [release workflow](../.github/workflows/release.yml), using the `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` repository secrets:

| Image | Contents | Size and footprint |
| --- | --- | --- |
| `greystone/openjdk-helper:X.Y.Z`, `:latest` | Temurin 25 JRE with the fast-jar | about 360 MB; roughly 150 MiB resident |
| `greystone/openjdk-helper:X.Y.Z-native`, `:native` | GraalVM native executable on the minimal UBI 9 base, built with [Dockerfile.native-release](../src/main/docker/Dockerfile.native-release) from the release binaries | about 100 MB; roughly 30 MiB resident, starts in about a second |

The native image is the better fit for small nodes such as a Raspberry Pi cluster: no JVM flags, no heap sizing, and the same REST and MCP endpoints and configuration properties. Both architectures are started and checked in the workflow before the image is pushed. `:latest` is no longer pushed on every commit to `main`. To publish the JVM image by hand, for example a hotfix without a release:

```sh
./mvnw clean package -DskipTests
docker buildx build -f src/main/docker/Dockerfile.jvm --platform linux/amd64,linux/arm64 \
  -t greystone/openjdk-helper:X.Y.Z -t greystone/openjdk-helper:latest --push .
```

## Reverse proxy and replicas

For hirt.se, forward `/openjdk/` to the application's `/`, preserve the request method, and pass `MCP-Session-Id` and `MCP-Protocol-Version` headers. Disable response buffering and allow long-lived connections for MCP event streams. Forwarded host/prefix support is enabled; supply these headers only from the trusted reverse proxy.

All tools are stateless, and from 0.2.1 the server accepts Streamable HTTP requests whose session it never issued (`quarkus.mcp.server.http.streamable.auto-init`), so several replicas can sit behind one load balancer without session affinity; each pod serves whichever request reaches it. Server-initiated messages are not used, so nothing is lost. The 0.2.0 image still keeps sessions in memory and needs a single replica or sticky routing.

Streamable HTTP uses the single `/openjdk/mcp` URL. With the legacy SSE transport, this extension advertises `/mcp/messages/{id}` in its first event; a proxy that strips `/openjdk` must also route that advertised message path to this service. Prefer Streamable HTTP for a deployment under a path prefix.
