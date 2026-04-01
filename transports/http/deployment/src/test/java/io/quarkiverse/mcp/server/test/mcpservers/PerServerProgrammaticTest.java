package io.quarkiverse.mcp.server.test.mcpservers;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests programmatic registration of features with the same name on different servers,
 * and verifies per-server get/remove and deprecated API behavior.
 */
public class PerServerProgrammaticTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(PerServerProgrammaticTest.class))
            .overrideConfigKey("quarkus.mcp.server.support-multi-server-bindings", "true")
            .overrideConfigKey("quarkus.mcp.server.http.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.http.root-path", "/bravo/mcp");

    @Inject
    ToolManager toolManager;

    @Inject
    PromptManager promptManager;

    @Inject
    ResourceManager resourceManager;

    @BeforeEach
    void cleanup() {
        // Remove all programmatically registered features
        for (String server : List.of(DEFAULT, "bravo")) {
            for (String name : List.of("query", "duplicate", "shared", "summarize", "data", "ambiguous")) {
                toolManager.removeTool(name, server);
                promptManager.removePrompt(name, server);
                resourceManager.removeResource(name, server);
                resourceManager.removeResource("file://" + name, server);
            }
        }
    }

    @Test
    public void testSameToolNameDifferentServers() {
        // Register tool "query" on default server
        toolManager.newTool("query")
                .setServerName(DEFAULT)
                .setDescription("alpha query")
                .setHandler(ta -> ToolResponse.success("alpha"))
                .register();

        // Register tool "query" on bravo server — same name, different server
        toolManager.newTool("query")
                .setServerName("bravo")
                .setDescription("bravo query")
                .setHandler(ta -> ToolResponse.success("bravo"))
                .register();

        // Server-aware get returns the correct tool
        ToolManager.ToolInfo alphaQuery = toolManager.getTool("query", DEFAULT);
        assertNotNull(alphaQuery);
        assertThat(alphaQuery.serverNames()).containsExactly(DEFAULT);
        assertEquals("alpha query", alphaQuery.description());

        ToolManager.ToolInfo bravoQuery = toolManager.getTool("query", "bravo");
        assertNotNull(bravoQuery);
        assertThat(bravoQuery.serverNames()).containsExactly("bravo");
        assertEquals("bravo query", bravoQuery.description());

        // Each server sees its own tool
        McpStreamableTestClient alphaClient = McpAssured.newStreamableClient()
                .setMcpPath("/alpha/mcp")
                .build()
                .connect();

        alphaClient.when()
                .toolsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("query", page.tools().get(0).name());
                })
                .toolsCall("query", r -> assertEquals("alpha", r.firstContent().asText().text()))
                .thenAssertResults();

        McpStreamableTestClient bravoClient = McpAssured.newStreamableClient()
                .setMcpPath("/bravo/mcp")
                .build()
                .connect();

        bravoClient.when()
                .toolsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("query", page.tools().get(0).name());
                })
                .toolsCall("query", r -> assertEquals("bravo", r.firstContent().asText().text()))
                .thenAssertResults();

        // Remove tool only from bravo — alpha should still work
        ToolManager.ToolInfo removed = toolManager.removeTool("query", "bravo");
        assertNotNull(removed);
        assertEquals("bravo query", removed.description());

        assertNull(toolManager.getTool("query", "bravo"));
        assertNotNull(toolManager.getTool("query", DEFAULT));
    }

    @Test
    public void testSameToolNameSameServerFails() {
        toolManager.newTool("duplicate")
                .setServerName(DEFAULT)
                .setDescription("first")
                .setHandler(ta -> ToolResponse.success("1"))
                .register();

        // Same name, same server — should fail
        assertThrows(IllegalArgumentException.class, () -> toolManager.newTool("duplicate")
                .setServerName(DEFAULT)
                .setDescription("second")
                .setHandler(ta -> ToolResponse.success("2"))
                .register());
    }

    @Test
    public void testSamePromptNameDifferentServers() {
        promptManager.newPrompt("summarize")
                .setServerName(DEFAULT)
                .setDescription("alpha summarize")
                .setHandler(pa -> PromptResponse.withMessages(PromptMessage.withUserRole("alpha")))
                .register();

        promptManager.newPrompt("summarize")
                .setServerName("bravo")
                .setDescription("bravo summarize")
                .setHandler(pa -> PromptResponse.withMessages(PromptMessage.withUserRole("bravo")))
                .register();

        assertNotNull(promptManager.getPrompt("summarize", DEFAULT));
        assertNotNull(promptManager.getPrompt("summarize", "bravo"));

        McpStreamableTestClient alphaClient = McpAssured.newStreamableClient()
                .setMcpPath("/alpha/mcp")
                .build()
                .connect();

        alphaClient.when()
                .promptsGet("summarize", r -> assertEquals("alpha", r.messages().get(0).content().asText().text()))
                .thenAssertResults();

        McpStreamableTestClient bravoClient = McpAssured.newStreamableClient()
                .setMcpPath("/bravo/mcp")
                .build()
                .connect();

        bravoClient.when()
                .promptsGet("summarize", r -> assertEquals("bravo", r.messages().get(0).content().asText().text()))
                .thenAssertResults();

        // Remove from alpha only
        PromptManager.PromptInfo removed = promptManager.removePrompt("summarize", DEFAULT);
        assertNotNull(removed);
        assertNull(promptManager.getPrompt("summarize", DEFAULT));
        assertNotNull(promptManager.getPrompt("summarize", "bravo"));
    }

    @Test
    public void testSameResourceUriDifferentServers() {
        resourceManager.newResource("data")
                .setServerName(DEFAULT)
                .setUri("file://data")
                .setDescription("alpha data")
                .setHandler(ra -> new ResourceResponse(
                        List.of(TextResourceContents.create(ra.requestUri().value(), "alpha"))))
                .register();

        resourceManager.newResource("data")
                .setServerName("bravo")
                .setUri("file://data")
                .setDescription("bravo data")
                .setHandler(ra -> new ResourceResponse(
                        List.of(TextResourceContents.create(ra.requestUri().value(), "bravo"))))
                .register();

        assertNotNull(resourceManager.getResource("file://data", DEFAULT));
        assertNotNull(resourceManager.getResource("file://data", "bravo"));

        McpStreamableTestClient alphaClient = McpAssured.newStreamableClient()
                .setMcpPath("/alpha/mcp")
                .build()
                .connect();

        alphaClient.when()
                .resourcesRead("file://data", r -> assertEquals("alpha", r.contents().get(0).asText().text()))
                .thenAssertResults();

        McpStreamableTestClient bravoClient = McpAssured.newStreamableClient()
                .setMcpPath("/bravo/mcp")
                .build()
                .connect();

        bravoClient.when()
                .resourcesRead("file://data", r -> assertEquals("bravo", r.contents().get(0).asText().text()))
                .thenAssertResults();

        // Remove from bravo only
        ResourceManager.ResourceInfo removed = resourceManager.removeResource("file://data", "bravo");
        assertNotNull(removed);
        assertNull(resourceManager.getResource("file://data", "bravo"));
        assertNotNull(resourceManager.getResource("file://data", DEFAULT));
    }

    @Test
    public void testGetWithoutServerThrowsIseWhenAmbiguous() {
        // Register same name on two servers
        toolManager.newTool("ambiguous")
                .setServerName(DEFAULT)
                .setDescription("alpha")
                .setHandler(ta -> ToolResponse.success("alpha"))
                .register();
        toolManager.newTool("ambiguous")
                .setServerName("bravo")
                .setDescription("bravo")
                .setHandler(ta -> ToolResponse.success("bravo"))
                .register();

        promptManager.newPrompt("ambiguous")
                .setServerName(DEFAULT)
                .setDescription("alpha")
                .setHandler(pa -> PromptResponse.withMessages(PromptMessage.withUserRole("alpha")))
                .register();
        promptManager.newPrompt("ambiguous")
                .setServerName("bravo")
                .setDescription("bravo")
                .setHandler(pa -> PromptResponse.withMessages(PromptMessage.withUserRole("bravo")))
                .register();

        resourceManager.newResource("ambiguous")
                .setServerName(DEFAULT)
                .setUri("file://ambiguous")
                .setDescription("alpha")
                .setHandler(ra -> new ResourceResponse(
                        List.of(TextResourceContents.create(ra.requestUri().value(), "alpha"))))
                .register();
        resourceManager.newResource("ambiguous")
                .setServerName("bravo")
                .setUri("file://ambiguous")
                .setDescription("bravo")
                .setHandler(ra -> new ResourceResponse(
                        List.of(TextResourceContents.create(ra.requestUri().value(), "bravo"))))
                .register();

        // Single-arg get throws ISE when ambiguous
        assertThrows(IllegalStateException.class, () -> toolManager.getTool("ambiguous"));
        assertThrows(IllegalStateException.class, () -> promptManager.getPrompt("ambiguous"));
        assertThrows(IllegalStateException.class, () -> resourceManager.getResource("file://ambiguous"));
    }

    @SuppressWarnings("null")
    @Test
    public void testMultiServerToolSharedInstance() {
        // Register a tool bound to both servers
        ToolManager.ToolInfo info = toolManager.newTool("shared")
                .setServerNames(DEFAULT, "bravo")
                .setDescription("shared tool")
                .setHandler(ta -> ToolResponse.success("shared"))
                .register();

        // Both server-aware lookups return the same instance
        ToolManager.ToolInfo fromAlpha = toolManager.getTool("shared", DEFAULT);
        assertNotNull(fromAlpha);
        ToolManager.ToolInfo fromBravo = toolManager.getTool("shared", "bravo");
        assertNotNull(fromBravo);
        assertThat(fromAlpha).isSameAs(fromBravo).isSameAs(info);
    }
}
