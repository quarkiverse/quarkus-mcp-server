# Quarkus MCP Server to greet users

A Quarkus-based MCP (Model Context Protocol) server exposing `greetHello` and `greetBye` tools via the
[quarkus-mcp-server](https://github.com/quarkiverse/quarkus-mcp-server) extension.

---

## Prerequisites

| Tool | Minimum version |
|------|----------------|
| Java (JDK) | 21 |
| Maven | 3.9+ |

---

## Build the über-jar

```bash
mvn package -DskipTests
```

The runner jar is created at:

```
target/{{artifactId}}-{{version}}-runner.jar
```

---

## Run locally

```bash
java -jar target/{{artifactId}}-{{version}}-runner.jar
```

The server starts and listens for MCP connections on stdio (default HTTP transport).

---

## Test the MCP tools interactively

You can exercise the tools using `curl` against the Streamable HTTP endpoint (`/mcp`).

The examples below use the **stateless** protocol (`2026-07-28`). Each request is self-contained — no
`initialize` handshake or session header is needed. The server detects the protocol version automatically.

### Call an MCP tool (stateless)

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2026-07-28" \
  -H "Mcp-Method: tools/call" \
  -H "Mcp-Name: greetHello" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "_meta": {
        "io.modelcontextprotocol/protocolVersion": "2026-07-28",
        "io.modelcontextprotocol/clientInfo": {
          "name": "curl-client",
          "version": "1.0.0"
        },
        "io.modelcontextprotocol/clientCapabilities": {}
      },
      "name": "greetHello",
      "arguments": {
        "name": "World"
      }
    }
  }'
```

Expected response:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Hello from the MCP server, World! - 09/10/2026 15:30:00"
      }
    ],
    "isError": false
  }
}
```

> **Tip (Stateful mode):** You can also use the older stateful protocol (`2025-03-26` or earlier) by sending
> an `initialize` request first, then passing the `Mcp-Session-Id` header on subsequent calls. See the
> [transport docs](https://docs.quarkiverse.io/quarkus-mcp-server/dev/concepts-transports.html) for details.

---

## Run integration tests

The `CheckToolsIT` class launches the über-jar as a child process and tests each tool via the
MCP stdio protocol.

```bash
# Build the jar first (required for the IT to find the runner)
mvn package -DskipTests

# Then run the integration tests (Failsafe integration test phase)
mvn verify -DskipITs=false
```

Maven Failsafe runs classes matching `*IT.java` during the `verify` phase.
`CheckToolsIT` will:
1. Start `target/{{artifactId}}-{{version}}-runner.jar` as a subprocess.
2. Call `greetHello` and `greetBye` via the MCP stdio protocol.
3. Assert the response text starts with the expected prefix.

---

## Register with an MCP client

**Note**: Replace within the following JSON file the `/absolute/path/to` with yours !

### IBM Bob (workspace scope)

Add the following to `.bob/mcp.json` in your workspace root, then save — Bob hot-reloads on change:

```json
{
  "mcpServers": {
    "{{artifactId}}": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/target/{{artifactId}}-{{version}}-runner.jar"]
    }
  }
}
```

### Claude Desktop (`claude_desktop_config.json`)

```json
{
  "mcpServers": {
    "{{artifactId}}": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/target/{{artifactId}}-{{version}}-runner.jar"]
    }
  }
}
```

### Generic MCP client (stdio transport)

Any client that supports the MCP stdio transport can launch the server with:

```bash
java -jar /path/to/target/{{artifactId}}-{{version}}-runner.jar
```

---

## Available tools

| Tool name | Description | Parameter |
|-----------|-------------|-----------|
| `greetHello` | Greet a user with Hello | `name` (string, default: `"Quarkus"`) |
| `greetBye` | Greet a user with Bye | `name` (string, default: `"Quarkus"`) |

---

## Project structure

```
src/
  main/java/org/acme/
    GreetingTools.java      ← @Tool-annotated methods
  test/java/org/acme/
    CheckToolsIT.java       ← Integration test (runs über-jar via stdio)
pom.xml
```
