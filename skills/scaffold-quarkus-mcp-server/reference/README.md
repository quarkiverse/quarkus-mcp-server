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

Note: Requests require both `Content-Type: application/json` and `Accept: application/json, text/event-stream` headers.

### Step 1: Initialize the session

Send the `initialize` handshake and inspect the response headers to capture the `Mcp-Session-Id`:

```bash
curl -i -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {
        "name": "curl-client",
        "version": "1.0.0"
      }
    }
  }'
```

The response headers include a session ID, e.g.:
```http
Mcp-Session-Id: <session-id>
```

### Step 2: Call an MCP tool

Pass the captured session ID in the `Mcp-Session-Id` header:

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
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
  "id": 2,
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

> **Tip (Stateless mode):** If you configure `quarkus.mcp.server.http.stateless=true` in `application.properties`, you can call tools directly without the `initialize` step or session header.

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
