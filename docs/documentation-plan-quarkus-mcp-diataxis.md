# Quarkus MCP Server Documentation Restructure Plan (Diataxis)

This document outlines the analysis, content strategy, and implementation plan for restructuring the Quarkus MCP Server documentation according to the [Diataxis framework](https://diataxis.fr/).

Diataxis organizes documentation into four types along two axes:

|  | **Acquisition** (learning) | **Application** (working) |
|---|---|---|
| **Action** (practical) | Tutorial | How-to Guide |
| **Cognition** (theoretical) | Explanation | Reference |

Each type serves a fundamentally different user need. Mixing types within a single document degrades all of them.

---

## Phase 1: Analysis of Existing Documentation

### Current File Inventory

| File | Lines | Content Type | Diataxis Types Present |
|------|-------|--------------|------------------------|
| `modules/ROOT/pages/index.adoc` | ~1616 | Mixed | Tutorial + How-to + Reference + Explanation (all four, intermixed) |
| `modules/ROOT/pages/cli-adapter.adoc` | ~19 | Procedure | How-to Guide |
| `modules/ROOT/pages/hibernate-validator.adoc` | ~125 | Procedure + reference | How-to Guide + Reference |
| `modules/ROOT/nav.adoc` | 4 | Navigation | N/A |
| `modules/ROOT/pages/includes/attributes.adoc` | 3 | Attributes | N/A |
| `modules/ROOT/pages/includes/quarkus-mcp-server-*.adoc` | 10 files | Auto-generated config reference | Reference |
| `templates/includes/attributes.adoc` | 3 | Template attributes | N/A |
| `antora.yml` | N/A | Antora configuration | N/A |

**Summary:** 3 main pages (~1,760 lines), 10 include files (auto-generated config), 16 total .adoc files

### Structural Assessment

**Strengths:**

1. **Focused scope**: Documentation covers a single, well-defined extension
2. **Existing integration content**: Hibernate Validator integration already separated
3. **Auto-generated config reference**: Configuration properties automatically documented
4. **Clear transport separation**: Different transports (stdio, HTTP, WebSocket) have distinct sections

**Diataxis Issues:**

1. **Monolithic main document**: The `index.adoc` file contains all four Diataxis types intermixed without separation. Tutorials bleed into reference, how-to steps include explanation, and reference material appears mid-procedure.

2. **No tutorial**: Documentation jumps directly into specification details and limitations rather than guiding new users through a learning experience. There is no guided path from zero to working MCP server.

3. **Feature-oriented organization**: Content is organized by MCP features (Tools, Prompts, Resources) rather than by user goals. This is acceptable for Reference (which mirrors product structure) but wrong for How-to Guides (which should mirror user goals).

4. **Missing Explanation content**: Conceptual material about transports, execution model, and CDI context exists in `index.adoc` but is buried within procedural sections rather than standing as dedicated Explanation documents. Users who want to *understand* these topics cannot find them without wading through how-to steps.

5. **Repeated content**: Method parameters are listed identically for `@Tool`, `@Prompt`, `@Resource`, `@ResourceTemplate`, `@CompletePrompt`, and `@CompleteResourceTemplate` sections (~13 parameters repeated 6 times). Reference content should be consolidated and cross-referenced.

### Content Gaps

| Gap | Diataxis Type Needed | Priority | Notes |
|-----|----------------------|----------|-------|
| No guided learning experience for beginners | Tutorial | High | The single most impactful addition |
| No conceptual context for transports | Explanation | Medium | Content exists in index.adoc but needs extraction and separation |
| No conceptual context for execution model | Explanation | Medium | Content exists in index.adoc but needs extraction and separation |
| No "Why Quarkus for MCP?" framing | Explanation | Low | Link to modelcontextprotocol.io for MCP protocol concepts |
| No deployment guidance | How-to Guide | Medium | Reference standard Quarkus deployment guides |
| No troubleshooting section | How-to Guide | High | Content TBD |
| No migration guidance | How-to Guide | Low | Version changes mentioned but not structured |

### Existing Content Catalog by Diataxis Type

**Explanation content** (currently buried in index.adoc):
- Lines 5-9 — MCP protocol overview
- Lines 11-23 — Specification and limitations
- Lines 25-108 — Transport explanations (stdio, HTTP, WebSocket): why different transports exist, trade-offs, architecture
- Lines 117-136 — Execution model explanation: blocking vs non-blocking, why it matters
- Lines 137-141 — CDI request context: how and why CDI scoping works in MCP

**How-to Guide content** (scattered across index.adoc):
- Adding transport dependencies
- Creating tools with `@Tool` annotation
- Creating prompts with `@Prompt` annotation
- Creating resources with `@Resource` annotation
- Creating resource templates with `@ResourceTemplate`
- Programmatic registration (ToolManager, PromptManager, etc.)
- Configuring security (HTTP, CORS, OIDC)
- Enabling traffic logging and using MCP Inspector
- `cli-adapter.adoc` (19 lines) — CLI adapter setup
- `hibernate-validator.adoc` (125 lines) — Input validation integration

**Reference content** (scattered across index.adoc + includes):
- Tool return type conversion rules
- Prompt return type conversion rules
- Resource return type conversion rules
- Method parameter types (repeated 6x — consolidation needed)
- Configuration properties (11 auto-generated include files)

**Tutorial content**: None exists. This is the primary gap.

---

## Phase 2: Content Strategy

### Primary User Needs by Diataxis Type

| ID | User Need | Diataxis Type | Current Coverage | Priority |
|----|-----------|---------------|------------------|----------|
| T1 | Learn to build an MCP server from scratch | Tutorial | None | High |
| H1 | Expose tools that AI clients can call | How-to Guide | Partial (mixed with reference) | High |
| H2 | Expose prompt templates for AI clients | How-to Guide | Partial (mixed with reference) | Medium |
| H3 | Expose resources/data to AI clients | How-to Guide | Partial (mixed with reference) | Medium |
| H4 | Secure MCP server endpoints | How-to Guide | Partial (buried in main doc) | High |
| H5 | Test an MCP server | How-to Guide | Partial (brief section) | Medium |
| H6 | Validate tool inputs | How-to Guide | Covered (separate file) | Medium |
| H7 | Debug MCP communication issues | How-to Guide | Partial (traffic logging, inspector) | Medium |
| H8 | Register features dynamically at runtime | How-to Guide | Covered but buried | Low |
| H9 | Send progress notifications for long operations | How-to Guide | Covered but buried | Low |
| H10 | Use sampling and elicitation | How-to Guide | Covered but buried | Low |
| H11 | Implement guardrails | How-to Guide | Covered but buried | Low |
| H12 | Set up the CLI adapter | How-to Guide | Covered (separate file, 19 lines) | Low |
| R1 | Look up annotation attributes and behavior | Reference | Partial (mixed with procedures) | High |
| R2 | Look up method parameter types | Reference | Covered but repeated 6x | High |
| R3 | Look up return type conversion rules | Reference | Covered but scattered | Medium |
| R4 | Look up transport configuration options | Reference | Partial (mixed with explanation) | Medium |
| R5 | Look up configuration properties | Reference | Covered (auto-generated) | Low |
| E1 | Understand MCP transport trade-offs | Explanation | Exists but buried | Medium |
| E2 | Understand the execution model | Explanation | Exists but buried | Medium |
| E3 | Understand why Quarkus for MCP servers | Explanation | Minimal (2 paragraphs) | Low |

### Proposed Content Structure

```
Quarkus MCP Server Documentation
│
├── Tutorial
│   └── Getting started with Quarkus MCP Server [NEW — T1]
│       Guided learning experience: create project → add dependency →
│       write first tool → run in dev mode → test with MCP Inspector
│
├── How-to Guides
│   ├── How to expose tools to AI clients [EXTRACT + CONSOLIDATE — H1]
│   │   (Covers: basic tool creation, tool arguments, structured return types)
│   ├── How to expose prompt templates [EXTRACT — H2]
│   │   (Covers: prompt creation, argument completion)
│   ├── How to expose resources and resource templates [EXTRACT + CONSOLIDATE — H3]
│   │   (Covers: static resources, resource templates, subscriptions)
│   ├── How to secure your MCP server [EXTRACT + CONSOLIDATE — H4]
│   │   (Covers: HTTP endpoint security, CORS, OIDC authentication)
│   ├── How to test your MCP server [EXTRACT + NEW — H5]
│   │   (Covers: McpAssured unit tests, integration testing)
│   ├── How to validate tool inputs [REVISE from hibernate-validator.adoc — H6]
│   ├── How to debug MCP communication [EXTRACT — H7]
│   │   (Covers: traffic logging, MCP Inspector)
│   ├── How to register features programmatically [EXTRACT — H8]
│   ├── How to send progress notifications [EXTRACT — H9]
│   ├── How to use sampling and elicitation [EXTRACT — H10]
│   ├── How to implement guardrails [EXTRACT — H11]
│   └── How to set up the CLI adapter [REVISE from cli-adapter.adoc — H12]
│
├── Reference
│   ├── Transport configuration [EXTRACT — R4]
│   ├── Tool annotation reference [EXTRACT — R1]
│   ├── Prompt annotation reference [EXTRACT — R1]
│   ├── Resource annotation reference [EXTRACT — R1]
│   ├── Method parameter types [CONSOLIDATE from 6 repeated sections — R2]
│   ├── Return type conversion rules [CONSOLIDATE — R3]
│   └── Configuration properties [KEEP — auto-generated includes — R5]
│
└── Explanation
    ├── About MCP transports [EXTRACT from index.adoc — E1]
    │   (Why stdio vs HTTP vs SSE, trade-offs, when to use which)
    ├── About the execution model [EXTRACT from index.adoc — E2]
    │   (Blocking vs non-blocking, CDI request context, why it matters)
    └── About Quarkus for MCP servers [NEW — E3]
        (CDI integration benefits, build-time optimization, dev mode,
         native compilation; links to modelcontextprotocol.io for MCP concepts)
```

### Cross-referencing Strategy

Each document type must link to related documents of other types:

| From | Links to | Example |
|------|----------|---------|
| Tutorial | Reference for details it glosses over | "Getting started" links to Tool annotation reference for the full `@Tool` attribute list |
| Tutorial | Explanation for concepts it doesn't explain | "Getting started" links to "About MCP transports" when choosing a transport |
| How-to Guide | Reference for option/parameter details | "How to expose tools" links to Method parameter types and Return type conversion rules |
| How-to Guide | Explanation for background context | "How to secure your MCP server" links to "About MCP transports" for transport-specific security context |
| Reference | How-to Guide for common usage | Tool annotation reference links to "How to expose tools" |
| Explanation | Tutorial for hands-on learning | "About MCP transports" links to "Getting started" for trying it out |

---

## Phase 3: Implementation Plan

### Document Specifications

#### Tutorial

| File Name | Diataxis Type | Source | Action |
|-----------|---------------|--------|--------|
| `tutorial-getting-started.adoc` | Tutorial | New | Create |

**tutorial-getting-started.adoc requirements:**
- Voice: first-person plural ("we"), direct imperatives, observational prompts ("Notice that...")
- Structure: single narrative path from zero to working MCP server
- Prerequisites (JDK, Maven/Gradle)
- Create new Quarkus project
- Add MCP server dependency
- Create a simple tool with `@Tool`
- Run in dev mode
- Test with MCP Inspector — show expected output at each step
- Every step must produce a visible, verifiable result
- No extended explanations (link to Explanation docs)
- No alternatives or optional paths
- Must work reliably every time
- Cross-references: link to Tool annotation reference, "About MCP transports" explanation

#### How-to Guides

| File Name | Diataxis Type | Source | Action |
|-----------|---------------|--------|--------|
| `howto-expose-tools.adoc` | How-to Guide | Extract + consolidate from index.adoc | Create |
| `howto-expose-prompts.adoc` | How-to Guide | Extract from index.adoc | Create |
| `howto-expose-resources.adoc` | How-to Guide | Extract + consolidate from index.adoc | Create |
| `howto-secure-mcp-server.adoc` | How-to Guide | Extract + consolidate from index.adoc | Create |
| `howto-test-mcp-server.adoc` | How-to Guide | Extract from index.adoc + new content | Create |
| `howto-validate-tool-inputs.adoc` | How-to Guide | Revise from hibernate-validator.adoc | Create |
| `howto-debug-mcp-communication.adoc` | How-to Guide | Extract from index.adoc | Create |
| `howto-register-features-programmatically.adoc` | How-to Guide | Extract from index.adoc | Create |
| `howto-send-progress-notifications.adoc` | How-to Guide | Extract from index.adoc | Create |
| `howto-use-sampling-elicitation.adoc` | How-to Guide | Extract from index.adoc | Create |
| `howto-implement-guardrails.adoc` | How-to Guide | Extract from index.adoc | Create |
| `howto-cli-adapter.adoc` | How-to Guide | Revise from cli-adapter.adoc | Create |

**How-to Guide voice and structure requirements:**
- Titles state the user's goal ("How to expose tools to AI clients")
- Assumes existing competence; does not teach fundamentals
- Action-oriented steps in logical sequence
- No extended explanation (link to Explanation docs for context)
- Links to Reference for full option/parameter details instead of inlining them
- Addresses real-world complexity where relevant (forking paths, edge cases)

**Consolidation notes:**
- `howto-expose-tools.adoc`: merges "Creating a basic tool," "Handling tool arguments," and "Returning structured content" — these serve one user goal
- `howto-expose-resources.adoc`: merges "Creating a static resource," "Creating a resource template," and "Handling resource subscriptions"
- `howto-secure-mcp-server.adoc`: merges HTTP endpoint security, CORS configuration, and OIDC authentication

#### Reference

| File Name | Diataxis Type | Source | Action |
|-----------|---------------|--------|--------|
| `ref-transports.adoc` | Reference | Extract from index.adoc | Create |
| `ref-tool-annotation.adoc` | Reference | Extract from index.adoc | Create |
| `ref-prompt-annotation.adoc` | Reference | Extract from index.adoc | Create |
| `ref-resource-annotation.adoc` | Reference | Extract from index.adoc | Create |
| `ref-method-parameters.adoc` | Reference | Consolidate from index.adoc | Create |
| `ref-return-type-conversions.adoc` | Reference | Consolidate from index.adoc | Create |

**Reference voice and structure requirements:**
- Austere, factual, neutral: describes without explaining, instructing, or opining
- Structured around the product (annotations, parameters, configuration), not around user tasks
- Consistent formatting across similar items (all annotations documented the same way)
- Includes illustrative examples that clarify without becoming instructional
- Configuration properties remain as auto-generated includes, referenced from this section

**ref-method-parameters.adoc requirements:**
- Single consolidated table of injectable method parameters
- Replaces 6 repeated parameter lists in current doc
- Cross-referenced from tool/prompt/resource how-to guides

#### Explanation

| File Name | Diataxis Type | Source | Action |
|-----------|---------------|--------|--------|
| `explanation-mcp-transports.adoc` | Explanation | Extract from index.adoc lines 25-108 | Create |
| `explanation-execution-model.adoc` | Explanation | Extract from index.adoc lines 117-141 | Create |
| `explanation-quarkus-mcp-benefits.adoc` | Explanation | New | Create |

**Explanation voice and structure requirements:**
- Discursive, reflective tone; titles use "About..." framing
- Answers implicit "why" questions ("Why are there different transports?" "Why does the execution model matter?")
- Provides design rationale, trade-offs, and architectural context
- Considers alternatives and multiple approaches where relevant
- Readable away from the product; does not require hands-on context
- Links to Tutorial and How-to Guides for hands-on follow-up

**explanation-mcp-transports.adoc**: Extracts and expands the transport explanations (stdio, HTTP, SSE/WebSocket) into a standalone discussion of trade-offs, use cases, and architectural considerations.

**explanation-execution-model.adoc**: Extracts and expands the execution model and CDI request context content into a discussion of why blocking vs. non-blocking matters and how CDI scoping works in MCP context.

**explanation-quarkus-mcp-benefits.adoc**: New content covering CDI integration benefits, build-time optimization, dev mode advantages, native compilation support. Links to modelcontextprotocol.io for MCP protocol concepts rather than duplicating them.

### Navigation Structure

**Proposed nav.adoc:**
```asciidoc
* Tutorial
** xref:tutorial-getting-started.adoc[Getting Started with Quarkus MCP Server]
* How-to Guides
** xref:howto-expose-tools.adoc[How to Expose Tools]
** xref:howto-expose-prompts.adoc[How to Expose Prompt Templates]
** xref:howto-expose-resources.adoc[How to Expose Resources]
** xref:howto-secure-mcp-server.adoc[How to Secure Your MCP Server]
** xref:howto-test-mcp-server.adoc[How to Test Your MCP Server]
** xref:howto-validate-tool-inputs.adoc[How to Validate Tool Inputs]
** xref:howto-debug-mcp-communication.adoc[How to Debug MCP Communication]
** xref:howto-register-features-programmatically.adoc[How to Register Features Programmatically]
** xref:howto-send-progress-notifications.adoc[How to Send Progress Notifications]
** xref:howto-use-sampling-elicitation.adoc[How to Use Sampling and Elicitation]
** xref:howto-implement-guardrails.adoc[How to Implement Guardrails]
** xref:howto-cli-adapter.adoc[How to Set Up the CLI Adapter]
* Reference
** xref:ref-transports.adoc[Transport Configuration]
** xref:ref-tool-annotation.adoc[Tool Annotation]
** xref:ref-prompt-annotation.adoc[Prompt Annotation]
** xref:ref-resource-annotation.adoc[Resource Annotation]
** xref:ref-method-parameters.adoc[Method Parameter Types]
** xref:ref-return-type-conversions.adoc[Return Type Conversion Rules]
** Configuration Properties
*** xref:includes/quarkus-mcp-server.adoc[General]
* Explanation
** xref:explanation-mcp-transports.adoc[About MCP Transports]
** xref:explanation-execution-model.adoc[About the Execution Model]
** xref:explanation-quarkus-mcp-benefits.adoc[About Quarkus for MCP Servers]
```

---

## Phase 4: Prioritized Implementation Tasks

### Priority 1: Tutorial (Critical Path — highest-impact addition)

1. [ ] Create `tutorial-getting-started.adoc` — Guided learning experience from zero to working MCP server
2. [ ] Create `explanation-quarkus-mcp-benefits.adoc` — "About Quarkus for MCP Servers" (linked from tutorial)

### Priority 2: Core How-to Guides (High Value)

3. [ ] Create `howto-expose-tools.adoc` — Consolidate tool creation, arguments, and return types from index.adoc
4. [ ] Create `howto-secure-mcp-server.adoc` — Consolidate HTTP, CORS, and OIDC security from index.adoc
5. [ ] Create `howto-debug-mcp-communication.adoc` — Extract traffic logging and MCP Inspector from index.adoc

### Priority 3: Reference Consolidation (Eliminates duplication)

6. [ ] Create `ref-method-parameters.adoc` — Consolidate 6 repeated parameter lists into one
7. [ ] Create `ref-return-type-conversions.adoc` — Consolidate tool/prompt/resource conversion rules
8. [ ] Create `ref-tool-annotation.adoc` — Extract annotation details from index.adoc
9. [ ] Create `ref-prompt-annotation.adoc` — Extract annotation details from index.adoc
10. [ ] Create `ref-resource-annotation.adoc` — Extract annotation details from index.adoc

### Priority 4: Remaining How-to Guides

11. [ ] Create `howto-expose-prompts.adoc` — Extract from index.adoc
12. [ ] Create `howto-expose-resources.adoc` — Consolidate static resources, templates, subscriptions
13. [ ] Create `howto-test-mcp-server.adoc` — Extract McpAssured content + new integration testing content
14. [ ] Create `howto-validate-tool-inputs.adoc` — Revise from hibernate-validator.adoc

### Priority 5: Explanation and Advanced How-to Guides

15. [ ] Create `explanation-mcp-transports.adoc` — Extract and expand transport content from index.adoc
16. [ ] Create `explanation-execution-model.adoc` — Extract and expand execution model content from index.adoc
17. [ ] Create `ref-transports.adoc` — Extract transport configuration reference from index.adoc
18. [ ] Create `howto-register-features-programmatically.adoc` — Extract from index.adoc
19. [ ] Create `howto-send-progress-notifications.adoc` — Extract from index.adoc
20. [ ] Create `howto-use-sampling-elicitation.adoc` — Extract from index.adoc
21. [ ] Create `howto-implement-guardrails.adoc` — Extract from index.adoc
22. [ ] Create `howto-cli-adapter.adoc` — Revise from cli-adapter.adoc

### Priority 6: Navigation and Cleanup

23. [ ] Update `nav.adoc` with Diataxis-aligned structure
24. [ ] Archive original `index.adoc`
25. [ ] Add cross-references between all documents (tutorial ↔ reference ↔ how-to ↔ explanation)
26. [ ] Review all documents against the Diataxis quality checklist

---

## Appendix: Diataxis Quick Reference

### The Four Types

| Type | Orientation | User Need | Voice | Structure |
|------|-------------|-----------|-------|-----------|
| **Tutorial** | Learning | "Teach me" | First-person plural ("we"), imperatives, observational prompts | Single narrative path with visible results at each step |
| **How-to Guide** | Task | "Help me accomplish X" | Direct action steps, goal-framed titles | Logical step sequence, assumes competence |
| **Reference** | Information | "What are the details of X?" | Austere, factual, neutral | Mirrors product structure, consistent formatting |
| **Explanation** | Understanding | "Why does X work this way?" | Discursive, reflective | Topic-focused discussion, considers alternatives |

### File Naming Conventions

| Prefix | Diataxis Type | Example |
|--------|---------------|---------|
| `tutorial-` | Tutorial | `tutorial-getting-started.adoc` |
| `howto-` | How-to Guide | `howto-expose-tools.adoc` |
| `ref-` | Reference | `ref-tool-annotation.adoc` |
| `explanation-` | Explanation | `explanation-mcp-transports.adoc` |

### Boundary Rules (What NOT to Mix)

| In this type... | Never include... | Instead, link to... |
|-----------------|------------------|----------------------|
| Tutorial | Extended explanations, option lists, alternatives | Explanation docs, Reference docs |
| How-to Guide | Teaching, conceptual context, full option enumerations | Tutorial, Explanation docs, Reference docs |
| Reference | Instructions, opinions, explanations | How-to Guides, Explanation docs |
| Explanation | Step-by-step instructions, reference tables | Tutorial, How-to Guides, Reference docs |

### The Diataxis Compass

When unsure how to classify content, ask two questions:

1. **Action or Cognition?** Does this guide practical steps, or convey theoretical knowledge?
2. **Acquisition or Application?** Does this serve someone learning, or someone applying existing skill?

|  | Acquisition | Application |
|---|---|---|
| **Action** | Tutorial | How-to Guide |
| **Cognition** | Explanation | Reference |

### Cross-referencing Checklist

- [ ] Every Tutorial links to relevant Reference and Explanation documents
- [ ] Every How-to Guide links to relevant Reference (for details) and Explanation (for context)
- [ ] Every Reference document links to the How-to Guide that demonstrates its usage
- [ ] Every Explanation document links to the Tutorial or How-to Guide for hands-on follow-up

---

## Notes

### Project-Specific Considerations

1. **Small documentation set**: Only 3 main pages makes a complete restructure feasible
2. **Monolithic decomposition**: Primary work is splitting `index.adoc` into focused documents by Diataxis type
3. **New content focus**: The Tutorial is the highest-priority new content — it does not exist today
4. **Explanation rescue**: Conceptual content in index.adoc (transports, execution model) must be extracted into dedicated Explanation documents rather than dropped
5. **Upstream coordination**: Changes should be coordinated with Quarkiverse maintainers

### Recommended Approach

1. **Start with the Tutorial**: The single highest-impact addition. Build it first, test it end-to-end
2. **Extract Reference next**: Consolidating repeated content (method parameters, return types) yields immediate quality gains and unblocks how-to guide cross-references
3. **Build How-to Guides incrementally**: Extract and consolidate from index.adoc one user goal at a time
4. **Extract Explanation last**: The conceptual content already exists — extraction is lower effort and can happen alongside other work
5. **Preserve original**: Keep index.adoc as reference until migration is complete
6. **Validate cross-references**: Test all xrefs after each migration batch
