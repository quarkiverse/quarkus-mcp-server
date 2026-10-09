# create-quarkus-mcp-server

A Bob skill that scaffolds a complete Quarkus MCP server project from scratch.

## What it does

When activated, the skill:

1. Asks for `groupId`, `artifactId`, `version`, and target directory
2. Bootstraps a Quarkus **3.40.1** (latest LTS) project via `mvn quarkus:create`
3. Patches `pom.xml` — adds `quarkus-mcp-server-bom:2.0.2` to `<dependencyManagement>`,
   the HTTP transport and test dependencies, the Failsafe plugin, and `uber-jar` packaging
4. Creates `GreetingTools.java` with `@Tool`-annotated `greetHello` and `greetBye` methods
5. Creates `CheckToolsIT.java` — an integration test that launches the über-jar via stdio
6. Creates `README.md` in the project root with build, test, and MCP client registration instructions
7. Builds the project and runs `mvn verify`
8. Writes the `.bob/mcp.json` or Claude Desktop config snippet

## Trigger phrases

- "create a Quarkus MCP server"
- "scaffold quarkus mcp"
- "quarkus mcp server"
- "quarkiverse mcp"
- "generate a quarkus project with MCP tools"

## Fixed versions

| Component | Version |
|-----------|---------|
| Quarkus platform (LTS) | `3.40.1` |
| quarkus-mcp-server-bom | `2.0.2` |
| Java minimum | 21 |

## File layout

```
~/.bob/skills/create-quarkus-mcp-server/
├── SKILL.md                  ← procedural instructions Bob follows
├── README.md                 ← this file (skill documentation)
└── reference/
    ├── GreetingTools.java    ← @Tool template copied into the generated project
    ├── CheckToolsIT.java     ← integration test template
    └── README.md             ← project README template ({{artifactId}} placeholders)
```

## Usage

Start a new conversation and say:

> "Create a Quarkus MCP server called my-greeter in groupId org.acme"

Bob will ask for any missing coordinates and scaffold the full project.
