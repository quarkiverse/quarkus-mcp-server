# stdio -> SSE MCP Proxy

Starts a proxy server between an MCP client using the `stdio` transport and an MCP server using the `HTTP/SSE` transport.
Sends all MCP messages to the target MCP server and writes the MCP responses to the standard output respectively.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw clean package
```

> [!NOTE]  
> You can also build a native executable using `./mvnw clean package -Dnative`.

This builds a Quarkus uber-jar that can be run directly:

```shell script
java -jar target/quarkus-mcp-stdio-sse-proxy-1.0.0-SNAPSHOT-runner.jar --help
```

Shows the following help message:

```shell script
Usage: stdio-sse-proxy [-hV] [--[no-]reconnect] -e=<sseEndpoint> [-s=<sleep>]
                       [-t=<timeout>]
  -e, --endpoint=<sseEndpoint>
                            The URI of the target SSE endpoint
  -h, --help                Show this help message and exit.
      --[no-]reconnect      If set to true then the proxy attempts to reconnect
                              if a message endpoint returns http status 400
  -s, --sleep=<sleep>       The sleep time in milliseconds; used when
                              processing the stdin queue
  -t, --timeout=<timeout>   The timeout in seconds; used when connecting to the
                              SSE endpoint and to obtain the message endpoint
  -V, --version             Print version information and exit.
```

In order to run a proxy server for a `quarkus-mcp-server` application running in the dev mode:

```shell script
java -jar target/quarkus-mcp-stdio-sse-proxy-1.0.0-SNAPSHOT-runner.jar
```

or if you need a custom endpoint:

```shell script
java -jar target/quarkus-mcp-stdio-sse-proxy-1.0.0-SNAPSHOT-runner.jar http://my.app/mcp
```

> [!IMPORTANT]  
> The target SSE endpoint is mandatory.

## JBang

You can also run the proxy as a JBang script:

```shell script
jbang --quiet src/main/java/io/quarkiverse/mcp/server/proxy/StdioSseProxy.java -h
```