---
name: create-quarkus-mcp-server
description: Scaffold a new Quarkus MCP server project with greeting tools, integration tests, and README. Use when the user wants to create, scaffold, or generate a Quarkus MCP server, mentions "quarkus mcp", "quarkus mcp server", "quarkiverse mcp", or asks to generate a Quarkus project with MCP tools.
---

# Create Quarkus MCP Server

Scaffold a complete Quarkus MCP server project using the latest LTS Quarkus release and the
`quarkus-mcp-server` Quarkiverse extension.

## Prerequisite

- **JDK:** `21`
- **Maven:** `3.9`

## Fixed versions (do not change without re-verifying Maven Central)

- **Quarkus platform:** `3.40.1` (LTS, community maintenance until 2027-09-30)

---

## Step 1 — Gather project coordinates

Use `ask_followup_question` to collect:
- **groupId** — e.g. `org.acme`
- **artifactId** — e.g. `my-mcp-server`
- **version** — default `1.0.0-SNAPSHOT`
- **Target directory** — where to create the project (default: current workspace root)

---

## Step 2 — Bootstrap via code.quarkus.io REST API

Download the project skeleton from the `code.quarkus.io` REST endpoint.
The `e` parameter selects extensions; `nc=true` skips starter code; `S` picks
the Quarkus LTS stream.

Derive the following from the **Fixed versions** and **Prerequisite** sections above:
- `<quarkus-stream>` — strip the patch from the Quarkus platform version (e.g. `3.40.1` → `3.40`)
- `<jdk>` — the JDK version from Prerequisites

```bash
curl -sSL -o /tmp/<artifactId>.zip \
  "https://code.quarkus.io/api/download?S=io.quarkus.platform:<quarkus-stream>&j=<jdk>&b=MAVEN&nc=true&g=<groupId>&a=<artifactId>&v=<version>&e=io.quarkiverse.mcp:quarkus-mcp-server-http&e=io.quarkiverse.mcp:quarkus-mcp-server-stdio&e=io.quarkiverse.mcp:quarkus-mcp-server-hibernate-validator"
```

Then extract the archive into the target directory:

```bash
unzip -o /tmp/<artifactId>.zip -d <target-directory>
```

The generated project already includes:
- Quarkus BOM (`io.quarkus.platform:quarkus-bom` at the fixed Quarkus platform version)
- MCP server BOM (managed via the platform)
- `quarkus-mcp-server-http`, `quarkus-mcp-server-stdio`, and `quarkus-mcp-server-hibernate-validator` dependencies
- `quarkus-arc` and `quarkus-junit` (test)
- Maven wrapper, Dockerfiles, Surefire & Failsafe plugins

---

## Step 3 — Patch `pom.xml`

Open `pom.xml` and apply the following:

1. **Enable über-jar packaging and enable ITs**:
   In `<properties>`, ensure:
   ```xml
   <quarkus.package.jar.type>uber-jar</quarkus.package.jar.type>
   <skipITs>false</skipITs>
   ```

2. **Add MCP test utilities**:
   In `<dependencies>`, add:
   ```xml
   <dependency>
       <groupId>io.quarkiverse.mcp</groupId>
       <artifactId>quarkus-mcp-server-test</artifactId>
       <scope>test</scope>
   </dependency>
   ```

---

## Step 4 — Create the `GreetingTools` class

Write the file at `src/main/java/<groupId-as-path>/GreetingTools.java`.
Use the reference file at `reference/GreetingTools.java` as the exact template —
replace the package declaration to match the actual `groupId`.

Use `write_file` to create the file. Do **not** alter the `@Tool` or `@ToolArg` annotations.

---

## Step 5 — Create the `CheckToolsIT` integration test

Write the file at `src/test/java/<groupId-as-path>/CheckToolsIT.java`.
Use the reference file at `reference/CheckToolsIT.java` as the exact template —
replace the package declaration and update the runner jar filename:

```java
.setCommand("java", "-jar", "target/<artifactId>-<version>-runner.jar")
```

Use `write_file` to create the file.

---

## Step 6 — Create `README.md`

Use `reference/README.md` as the template. Replace all `{{artifactId}}` and `{{version}}`
placeholders with the actual values collected in Step 1. Write the file to the project root.

---

## Step 7 — Build the project

```bash
cd <artifactId> && mvn package -DskipTests
```

Confirm `target/<artifactId>-<version>-runner.jar` exists after the build.

---

## Step 8 — Run integration tests

```bash
mvn verify
# Or explicitly if skipITs is true:
mvn verify -DskipITs=false
```

`CheckToolsIT` will launch the über-jar as a subprocess and call both `greetHello` and
`greetBye` via the MCP stdio protocol.

---

## Step 9 — Register with an MCP client

Present the user with the registration snippets for their preferred client.

### IBM Bob (workspace `.bob/mcp.json`)

```json
{
  "mcpServers": {
    "<artifactId>": {
      "command": "java",
      "args": ["-jar", "<absolute-path>/target/<artifactId>-<version>-runner.jar"]
    }
  }
}
```

Write this to `.bob/mcp.json` (read-then-merge if the file already exists).

### Claude Desktop

```json
{
  "mcpServers": {
    "<artifactId>": {
      "command": "java",
      "args": ["-jar", "<absolute-path>/target/<artifactId>-<version>-runner.jar"]
    }
  }
}
```

On macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
On Windows: `%APPDATA%\Claude\claude_desktop_config.json`

---

## Step 10 — Confirm

Tell the user:
- The project is ready at `<target-directory>/<artifactId>/`
- Build command: `mvn package -DskipTests`
- Integration test command: `mvn verify`
- The MCP config has been written / show them the snippet to paste
- Available tools: `greetHello(name)` and `greetBye(name)`
