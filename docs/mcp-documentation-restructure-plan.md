# Prototype: Quarkus MCP Server Documentation Restructure Plan

This document outlines the analysis, content strategy, and implementation plan for restructuring the Quarkus MCP Server documentation to follow the Red Hat Modular Documentation standard.

---

## Phase 1: Analysis of Existing Documentation

### Current File Inventory

| File | Lines | Content Type | Module Type |
|------|-------|--------------|-------------|
| `modules/ROOT/pages/index.adoc` | ~1616 | Mixed (concept, procedure, reference) | None (monolithic) |
| `modules/ROOT/pages/cli-adapter.adoc` | ~19 | Procedure | None |
| `modules/ROOT/pages/hibernate-validator.adoc` | ~125 | Procedure + reference | None |
| `modules/ROOT/nav.adoc` | 4 | Navigation | N/A |
| `modules/ROOT/pages/includes/attributes.adoc` | 3 | Attributes | N/A |
| `modules/ROOT/pages/includes/quarkus-mcp-server-*.adoc` | 10 files | Auto-generated config reference | Reference (generated) |
| `templates/includes/attributes.adoc` | 3 | Template attributes | N/A |
| `antora.yml` | N/A | Antora configuration | N/A |

**Summary:** 3 main pages (~1,760 lines), 10 include files (auto-generated config), 16 total .adoc files

### Structural Assessment

**Strengths:**

1. **Focused scope**: Documentation covers a single, well-defined extension
2. **Existing integration content**: Hibernate Validator integration already separated
3. **Auto-generated config reference**: Configuration properties automatically documented
4. **Clear transport separation**: Different transports (stdio, HTTP, WebSocket) have distinct sections

**Structural Issues:**

1. **Monolithic main document**: The `index.adoc` file is 1600+ lines containing concepts, procedures, and reference material intermixed without clear separation.

2. **No getting started path**: Documentation jumps directly into specification details and limitations rather than guiding new users through their first MCP server.

3. **Feature-oriented organization**: Content is organized by MCP features (Tools, Prompts, Resources) rather than by user goals (tasks users want to accomplish).

4. **No mod-docs compliance**: Files do not follow naming conventions (`con-`, `proc-`, `ref-`, `assembly-`) or structural requirements.

5. **Repeated content**: Method parameters are listed identically for `@Tool`, `@Prompt`, `@Resource`, `@ResourceTemplate`, `@CompletePrompt`, and `@CompleteResourceTemplate` sections (~13 parameters repeated 6 times).

### Content Gaps

| Gap | Impact | Priority |
|-----|--------|----------|
| No conceptual introduction to MCP | Users unfamiliar with MCP have no context | High |
| No quick start tutorial | High barrier to entry for new users | High |
| No client integration guides | Users don't know how to connect Claude Desktop, VS Code, etc. | High |
| No deployment guidance | No native compilation, containerization, or cloud deployment info | Medium |
| No troubleshooting section | Common issues scattered or undocumented | High |
| No migration guidance | Version changes (e.g., artifactId rename) mentioned but not structured | Low |

### Existing Content Catalog

**Conceptual content:**
- `index.adoc` lines 5-9 - MCP protocol overview
- `index.adoc` lines 11-23 - Specification and limitations
- `index.adoc` lines 25-108 - Transport explanations (stdio, HTTP, WebSocket)
- `index.adoc` lines 117-136 - Execution model explanation
- `index.adoc` lines 137-141 - CDI request context

**Procedural content:**
- `index.adoc` (scattered) - Adding transport dependencies
- `index.adoc` - Creating tools with `@Tool` annotation
- `index.adoc` - Creating prompts with `@Prompt` annotation
- `index.adoc` - Creating resources with `@Resource` annotation
- `index.adoc` - Creating resource templates with `@ResourceTemplate`
- `index.adoc` - Programmatic registration (ToolManager, PromptManager, etc.)
- `index.adoc` - Configuring security
- `index.adoc` - Enabling traffic logging and using MCP Inspector
- `cli-adapter.adoc` (19 lines) - CLI adapter setup
- `hibernate-validator.adoc` (125 lines) - Input validation integration

**Reference content:**
- `index.adoc` - Tool return type conversion rules
- `index.adoc` - Prompt return type conversion rules
- `index.adoc` - Resource return type conversion rules
- `index.adoc` - Method parameter types (repeated 6x)
- Configuration properties (11 auto-generated include files)

---

## Phase 2: Content Strategy (Jobs To Be Done)

### Primary User Jobs

| Job ID | User Job | Priority | Current Coverage |
|--------|----------|----------|------------------|
| J1 | Understand what MCP is and why to use Quarkus for it | High | Minimal (2 paragraphs) |
| J2 | Create my first MCP server quickly | High | None |
| J3 | Expose tools that AI clients can call | High | Partial (mixed with reference) |
| J4 | Expose prompt templates for AI clients | Medium | Partial (mixed with reference) |
| J5 | Expose resources/data to AI clients | Medium | Partial (mixed with reference) |
| J6 | Connect my server to Claude Desktop | High | None |
| J7 | Connect my server to VS Code/Cursor | Medium | None |
| J8 | Secure my MCP server endpoints | High | Partial (in main doc) |
| J9 | Test my MCP server | Medium | Partial (brief section) |
| J10 | Debug MCP communication issues | Medium | Partial (traffic logging, inspector) |
| J11 | Deploy to production (container/native) | Medium | None |
| J12 | Register features dynamically at runtime | Low | Covered but buried |
| J13 | Validate tool inputs | Medium | Covered (separate file) |
| J14 | Handle long-running operations with progress | Low | Covered but buried |

### Proposed Content Outline

The existing monolithic structure requires significant restructuring. The primary work involves:
1. Decomposing `index.adoc` (1616 lines) into focused modules
2. Creating new content (getting started, client integration, deployment)
3. Adding mod-docs prefixes for downstream consumption
4. Creating assemblies to group related content

```
Quarkus MCP Server Documentation
├── Getting Started (Assembly) [NEW]
│   ├── [concept] What is MCP? [EXTRACT from index.adoc]
│   ├── [concept] Why Quarkus for MCP servers? [NEW]
│   └── [procedure] Creating your first MCP server [NEW]
│
├── How-To Guides (Assemblies) [NEW]
│   ├── Exposing Tools [NEW]
│   │   ├── [procedure] Creating a basic tool [EXTRACT]
│   │   ├── [procedure] Handling tool arguments [EXTRACT]
│   │   ├── [procedure] Returning structured content [EXTRACT]
│   │   └── [procedure] Adding input validation [REVISE from hibernate-validator.adoc]
│   │
│   ├── Exposing Prompts [NEW]
│   │   ├── [procedure] Creating a prompt template [EXTRACT]
│   │   └── [procedure] Adding argument completion [EXTRACT]
│   │
│   ├── Exposing Resources [NEW]
│   │   ├── [procedure] Creating a static resource [EXTRACT]
│   │   ├── [procedure] Creating a resource template [EXTRACT]
│   │   └── [procedure] Handling resource subscriptions [EXTRACT]
│   │
│   ├── Client Integration [NEW]
│   │   ├── [procedure] Connecting to Claude Desktop [NEW]
│   │   ├── [procedure] Connecting to VS Code extensions [NEW]
│   │   └── [procedure] Using MCP Inspector for testing [EXTRACT]
│   │
│   ├── Security [NEW]
│   │   ├── [procedure] Securing HTTP endpoints [EXTRACT]
│   │   ├── [procedure] Configuring CORS [EXTRACT]
│   │   └── [procedure] Using OIDC authentication [EXTRACT]
│   │
│   ├── Testing [NEW]
│   │   ├── [procedure] Writing unit tests with McpAssured [EXTRACT]
│   │   └── [procedure] Integration testing strategies [NEW]
│   │
│   ├── Advanced Features [NEW]
│   │   ├── [procedure] Registering features programmatically [EXTRACT]
│   │   ├── [procedure] Sending progress notifications [EXTRACT]
│   │   ├── [procedure] Using sampling and elicitation [EXTRACT]
│   │   └── [procedure] Implementing guardrails [EXTRACT]
│   │
│   └── CLI Adapter [REVISE from cli-adapter.adoc]
│       └── [procedure] Setting up the CLI adapter
│
└── Reference (Assembly) [NEW]
    ├── [reference] Transport configuration [EXTRACT]
    ├── [reference] Tool annotation reference [EXTRACT]
    ├── [reference] Prompt annotation reference [EXTRACT]
    ├── [reference] Resource annotation reference [EXTRACT]
    ├── [reference] Method parameter types [CONSOLIDATE - replaces 6 repeated sections]
    ├── [reference] Return type conversion rules [CONSOLIDATE]
    └── [reference] Configuration properties [KEEP - existing auto-generated includes]
```

---

## Phase 3: Implementation Plan

### Module Specifications

#### Getting Started Assembly

| File Name | Type | Source | Action |
|-----------|------|--------|--------|
| `assembly-getting-started.adoc` | assembly | New | Create |
| `con-mcp-overview.adoc` | concept | Extract from index.adoc lines 5-23 | Create |
| `con-quarkus-mcp-benefits.adoc` | concept | New | Create |
| `proc-creating-first-mcp-server.adoc` | procedure | New | Create |

**con-mcp-overview.adoc requirements:**
- Explain MCP protocol purpose
- Describe the client-server model
- List supported MCP specification version
- Note known limitations

**con-quarkus-mcp-benefits.adoc requirements:**
- CDI integration benefits
- Build-time optimization
- Dev mode advantages
- Native compilation support

**proc-creating-first-mcp-server.adoc requirements:**
- Prerequisites (JDK, Maven/Gradle)
- Create new Quarkus project
- Add MCP server dependency
- Create simple tool
- Run in dev mode
- Test with MCP Inspector
- Verification steps

#### How-To: Exposing Tools

| File Name | Type | Source | Action |
|-----------|------|--------|--------|
| `assembly-exposing-tools.adoc` | assembly | New | Create |
| `proc-creating-basic-tool.adoc` | procedure | Extract from index.adoc | Create |
| `proc-handling-tool-arguments.adoc` | procedure | Extract from index.adoc | Create |
| `proc-returning-structured-content.adoc` | procedure | Extract from index.adoc | Create |
| `proc-validating-tool-inputs.adoc` | procedure | Refactor hibernate-validator.adoc | Revise |

#### How-To: Exposing Prompts

| File Name | Type | Source | Action |
|-----------|------|--------|--------|
| `assembly-exposing-prompts.adoc` | assembly | New | Create |
| `proc-creating-prompt-template.adoc` | procedure | Extract from index.adoc | Create |
| `proc-adding-prompt-completion.adoc` | procedure | Extract from index.adoc | Create |

#### How-To: Exposing Resources

| File Name | Type | Source | Action |
|-----------|------|--------|--------|
| `assembly-exposing-resources.adoc` | assembly | New | Create |
| `proc-creating-static-resource.adoc` | procedure | Extract from index.adoc | Create |
| `proc-creating-resource-template.adoc` | procedure | Extract from index.adoc | Create |
| `proc-handling-resource-subscriptions.adoc` | procedure | Extract from index.adoc | Create |

#### How-To: Client Integration

| File Name | Type | Source | Action |
|-----------|------|--------|--------|
| `assembly-client-integration.adoc` | assembly | New | Create |
| `proc-connecting-claude-desktop.adoc` | procedure | New | Create |
| `proc-connecting-vscode.adoc` | procedure | New | Create |
| `proc-using-mcp-inspector.adoc` | procedure | Extract from index.adoc | Create |

**proc-connecting-claude-desktop.adoc requirements:**
- Claude Desktop MCP configuration file location
- JSON configuration format
- stdio vs HTTP transport setup
- Verification steps

#### How-To: Security

| File Name | Type | Source | Action |
|-----------|------|--------|--------|
| `assembly-securing-mcp-server.adoc` | assembly | New | Create |
| `proc-securing-http-endpoints.adoc` | procedure | Extract from index.adoc | Create |
| `proc-configuring-cors.adoc` | procedure | Extract from index.adoc | Create |
| `proc-oidc-authentication.adoc` | procedure | Extract from index.adoc | Create |

#### How-To: Testing

| File Name | Type | Source | Action |
|-----------|------|--------|--------|
| `assembly-testing-mcp-server.adoc` | assembly | New | Create |
| `proc-writing-tests-mcpassured.adoc` | procedure | Extract from index.adoc | Create |
| `proc-integration-testing.adoc` | procedure | New | Create |

#### How-To: Advanced Features

| File Name | Type | Source | Action |
|-----------|------|--------|--------|
| `assembly-advanced-features.adoc` | assembly | New | Create |
| `proc-registering-features-programmatically.adoc` | procedure | Extract from index.adoc | Create |
| `proc-sending-progress-notifications.adoc` | procedure | Extract from index.adoc | Create |
| `proc-using-sampling-elicitation.adoc` | procedure | Extract from index.adoc | Create |
| `proc-implementing-guardrails.adoc` | procedure | Extract from index.adoc | Create |

#### Reference Assembly

| File Name | Type | Source | Action |
|-----------|------|--------|--------|
| `assembly-reference.adoc` | assembly | Refactor index.adoc | Create |
| `ref-transports.adoc` | reference | Extract from index.adoc | Create |
| `ref-tool-annotation.adoc` | reference | Extract from index.adoc | Create |
| `ref-prompt-annotation.adoc` | reference | Extract from index.adoc | Create |
| `ref-resource-annotation.adoc` | reference | Extract from index.adoc | Create |
| `ref-method-parameters.adoc` | reference | Consolidate from index.adoc | Create |
| `ref-return-type-conversions.adoc` | reference | Consolidate from index.adoc | Create |

**ref-method-parameters.adoc requirements:**
- Single consolidated list of injectable parameters
- Replaces 6 repeated parameter lists in current doc
- Cross-referenced from tool/prompt/resource/completion procedures

#### CLI Adapter

| File Name | Type | Source | Action |
|-----------|------|--------|--------|
| `proc-cli-adapter.adoc` | procedure | Rename cli-adapter.adoc | Revise |

### Navigation Update

**Proposed nav.adoc:**
```asciidoc
* xref:assembly-getting-started.adoc[Getting Started]
* How-To Guides
** xref:assembly-exposing-tools.adoc[Exposing Tools]
** xref:assembly-exposing-prompts.adoc[Exposing Prompts]
** xref:assembly-exposing-resources.adoc[Exposing Resources]
** xref:assembly-client-integration.adoc[Client Integration]
** xref:assembly-securing-mcp-server.adoc[Securing Your Server]
** xref:assembly-testing-mcp-server.adoc[Testing]
** xref:assembly-advanced-features.adoc[Advanced Features]
** xref:proc-cli-adapter.adoc[CLI Adapter]
* xref:assembly-reference.adoc[Reference]
```

---

## Phase 4: Prioritized Implementation Tasks

### Priority 1: Getting Started (Critical Path)

1. [ ] Create `con-mcp-overview.adoc` - Extract and expand MCP introduction
2. [ ] Create `con-quarkus-mcp-benefits.adoc` - New content
3. [ ] Create `proc-creating-first-mcp-server.adoc` - New tutorial
4. [ ] Create `assembly-getting-started.adoc` - Combine above modules

### Priority 2: Core How-To Guides (High Value)

5. [ ] Create `proc-creating-basic-tool.adoc` - Extract from index.adoc
6. [ ] Create `proc-connecting-claude-desktop.adoc` - New content (high demand)
7. [ ] Create `proc-using-mcp-inspector.adoc` - Extract from index.adoc
8. [ ] Create `assembly-exposing-tools.adoc` - Combine tool procedures
9. [ ] Create `assembly-client-integration.adoc` - Combine client procedures

### Priority 3: Complete How-To Coverage

10. [ ] Create remaining tool procedures (arguments, structured content, validation)
11. [ ] Create prompt procedures and assembly
12. [ ] Create resource procedures and assembly
13. [ ] Create security procedures and assembly
14. [ ] Create testing procedures and assembly

### Priority 4: Reference Consolidation

15. [ ] Create `ref-method-parameters.adoc` - Consolidate repeated content
16. [ ] Create `ref-return-type-conversions.adoc` - Consolidate conversion rules
17. [ ] Create `ref-tool-annotation.adoc` - Extract annotation details
18. [ ] Create remaining reference modules
19. [ ] Create `assembly-reference.adoc` - Combine all reference modules

### Priority 5: Advanced Features

20. [ ] Create advanced feature procedures (programmatic API, progress, sampling)
21. [ ] Create `assembly-advanced-features.adoc`

### Priority 6: Cleanup

22. [ ] Update `nav.adoc` with new structure
23. [ ] Deprecate/archive original `index.adoc`
24. [ ] Rename `cli-adapter.adoc` to `proc-cli-adapter.adoc`
25. [ ] Review and update cross-references

---

## Appendix: Mod-Docs Quick Reference

### File Naming Conventions

| Prefix | Module Type | Example |
|--------|-------------|---------|
| `con-` | Concept | `con-mcp-overview.adoc` |
| `proc-` | Procedure | `proc-creating-basic-tool.adoc` |
| `ref-` | Reference | `ref-tool-annotation.adoc` |
| `assembly-` | Assembly | `assembly-getting-started.adoc` |

### Module Structure Requirements

**Concept modules:**
- Answer "What is X?" and "Why does X matter?"
- Single introductory paragraph
- No step-by-step instructions

**Procedure modules:**
- Gerund-phrase title (e.g., "Creating a basic tool")
- Prerequisites section (if applicable)
- Numbered steps in imperative form
- Verification section

**Reference modules:**
- Organized data (tables, definition lists)
- Alphabetical or logical ordering
- Minimal prose

**Assemblies:**
- Introduction explaining user goal
- `include::` directives for modules
- Optional: prerequisites, next steps

### Anchor Format

```asciidoc
[id="filename_{context}"]
= Module Title
```

### Content Type Attribute

```asciidoc
:_mod-docs-content-type: PROCEDURE
```

---

## Notes

### Project-Specific Considerations

1. **Small documentation set**: Only 3 main pages makes complete restructure feasible
2. **Monolithic decomposition**: Primary work is splitting `index.adoc` into modules
3. **New content focus**: Significant new content needed (getting started, client integration)
4. **Upstream coordination**: Changes should be coordinated with Quarkiverse maintainers

### Recommended Approach

1. **Create new modules first**: Build new structure alongside existing docs
2. **Incremental migration**: Move content section by section from index.adoc
3. **Preserve original**: Keep index.adoc as reference until migration complete
4. **Validation**: Test all xrefs after each migration batch
