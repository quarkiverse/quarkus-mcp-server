package io.quarkiverse.mcp.server.test.mcpservers;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;

public class MultipleServersMultiBindingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class, CharlieFeatures.class))
            .overrideConfigKey("quarkus.mcp.server.support-multi-server-bindings", "true")
            .overrideConfigKey("quarkus.mcp.server.sse.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.sse.root-path", "/bravo/mcp")
            .overrideConfigKey("quarkus.mcp.server.charlie.sse.root-path", "/charlie/mcp");

    @Inject
    ToolManager toolManager;

    @Inject
    PromptManager promptManager;

    @Inject
    ResourceManager resourceManager;

    @Test
    public void testServerNames() {
        ToolManager.ToolInfo alpha = toolManager.getTool("alpha");
        assertNotNull(alpha);
        assertThat(alpha.serverNames()).containsExactly(DEFAULT);

        ToolManager.ToolInfo bravo = toolManager.getTool("bravo");
        assertNotNull(bravo);
        assertThat(bravo.serverNames()).containsExactly("bravo");

        ToolManager.ToolInfo shared = toolManager.getTool("shared");
        assertNotNull(shared);
        assertThat(shared.serverNames()).containsExactlyInAnyOrder(DEFAULT, "bravo");

        ToolManager.ToolInfo charlie = toolManager.getTool("charlie");
        assertNotNull(charlie);
        assertThat(charlie.serverNames()).containsExactly("charlie");

        ToolManager.ToolInfo delta = toolManager.getTool("delta");
        assertNotNull(delta);
        assertThat(delta.serverNames()).containsExactlyInAnyOrder("charlie", DEFAULT);

        ToolManager.ToolInfo golf = toolManager.getTool("golf");
        assertNotNull(golf);
        assertThat(golf.serverNames()).containsExactlyInAnyOrder("charlie", "bravo");

        PromptManager.PromptInfo sharedPrompt = promptManager.getPrompt("sharedPrompt");
        assertNotNull(sharedPrompt);
        assertThat(sharedPrompt.serverNames()).containsExactlyInAnyOrder(DEFAULT, "bravo");

        PromptManager.PromptInfo deltaPrompt = promptManager.getPrompt("deltaPrompt");
        assertNotNull(deltaPrompt);
        assertThat(deltaPrompt.serverNames()).containsExactlyInAnyOrder("charlie", DEFAULT);

        ResourceManager.ResourceInfo sharedResource = resourceManager.getResource("file://shared");
        assertNotNull(sharedResource);
        assertThat(sharedResource.serverNames()).containsExactlyInAnyOrder(DEFAULT, "bravo");

        ResourceManager.ResourceInfo deltaResource = resourceManager.getResource("file://delta");
        assertNotNull(deltaResource);
        assertThat(deltaResource.serverNames()).containsExactlyInAnyOrder("charlie", DEFAULT);

        ToolManager.ToolInfo echo = toolManager.getTool("echo");
        assertNotNull(echo);
        assertThat(echo.serverNames()).containsExactly(DEFAULT);

        ToolManager.ToolInfo foxtrot = toolManager.getTool("foxtrot");
        assertNotNull(foxtrot);
        assertThat(foxtrot.serverNames()).containsExactly("bravo");
    }

    @Test
    public void testDefaultServer() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/alpha/mcp")
                .build()
                .connect();

        client.when()
                // alpha is bound to default → accessible
                .toolsCall("alpha", r -> assertEquals("1", r.content().get(0).asText().text()))
                // shared is bound to default AND bravo → accessible from default
                .toolsCall("shared", r -> assertEquals("shared", r.content().get(0).asText().text()))
                // bravo is bound to bravo only → NOT accessible from default
                .toolsCall("bravo").withErrorAssert(e -> assertEquals("Invalid tool name: bravo", e.message())).send()
                // charlie is bound to charlie only → NOT accessible from default
                .toolsCall("charlie").withErrorAssert(e -> assertEquals("Invalid tool name: charlie", e.message())).send()
                // delta is bound to charlie AND default → accessible from default
                .toolsCall("delta", r -> assertEquals("4", r.content().get(0).asText().text()))
                // golf is bound to charlie AND bravo → NOT accessible from default
                .toolsCall("golf").withErrorAssert(e -> assertEquals("Invalid tool name: golf", e.message())).send()
                // echo (programmatic) is bound to default → accessible
                .toolsCall("echo", r -> assertEquals("5", r.content().get(0).asText().text()))
                // foxtrot (programmatic) is bound to bravo → NOT accessible from default
                .toolsCall("foxtrot").withErrorAssert(e -> assertEquals("Invalid tool name: foxtrot", e.message())).send()
                // prompts
                .promptsGet("alphaPrompt", r -> assertEquals("1", r.messages().get(0).content().asText().text()))
                .promptsGet("sharedPrompt", r -> assertEquals("shared", r.messages().get(0).content().asText().text()))
                .promptsGet("bravoPrompt").withErrorAssert(e -> assertEquals("Invalid prompt name: bravoPrompt", e.message()))
                .send()
                .promptsGet("deltaPrompt", r -> assertEquals("4", r.messages().get(0).content().asText().text()))
                // resources
                .resourcesRead("file://alpha", r -> assertEquals("1", r.contents().get(0).asText().text()))
                .resourcesRead("file://shared", r -> assertEquals("shared", r.contents().get(0).asText().text()))
                .resourcesRead("file://bravo")
                .withErrorAssert(e -> assertEquals("Resource not found: file://bravo", e.message()))
                .send()
                .resourcesRead("file://delta", r -> assertEquals("4", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

    @Test
    public void testBravoServer() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/bravo/mcp")
                .build()
                .connect();

        client.when()
                // bravo is bound to bravo → accessible
                .toolsCall("bravo", r -> assertEquals("2", r.content().get(0).asText().text()))
                // shared is bound to default AND bravo → accessible from bravo
                .toolsCall("shared", r -> assertEquals("shared", r.content().get(0).asText().text()))
                // alpha is bound to default only → NOT accessible from bravo
                .toolsCall("alpha").withErrorAssert(e -> assertEquals("Invalid tool name: alpha", e.message())).send()
                // golf is bound to charlie AND bravo → accessible from bravo
                .toolsCall("golf", r -> assertEquals("golf", r.content().get(0).asText().text()))
                // foxtrot (programmatic) is bound to bravo → accessible
                .toolsCall("foxtrot", r -> assertEquals("6", r.content().get(0).asText().text()))
                // prompts
                .promptsGet("sharedPrompt", r -> assertEquals("shared", r.messages().get(0).content().asText().text()))
                .promptsGet("bravoPrompt", r -> assertEquals("2", r.messages().get(0).content().asText().text()))
                .promptsGet("alphaPrompt").withErrorAssert(e -> assertEquals("Invalid prompt name: alphaPrompt", e.message()))
                .send()
                // resources
                .resourcesRead("file://shared", r -> assertEquals("shared", r.contents().get(0).asText().text()))
                .resourcesRead("file://bravo", r -> assertEquals("2", r.contents().get(0).asText().text()))
                .resourcesRead("file://alpha")
                .withErrorAssert(e -> assertEquals("Resource not found: file://alpha", e.message())).send()
                .thenAssertResults();
    }

    @Test
    public void testCharlieServer() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/charlie/mcp")
                .build()
                .connect();

        client.when()
                // charlie is bound to charlie → accessible
                .toolsCall("charlie", r -> assertEquals("3", r.content().get(0).asText().text()))
                // delta is bound to charlie AND default → accessible from charlie
                .toolsCall("delta", r -> assertEquals("4", r.content().get(0).asText().text()))
                // golf is bound to charlie AND bravo → accessible from charlie
                .toolsCall("golf", r -> assertEquals("golf", r.content().get(0).asText().text()))
                // alpha is bound to default only → NOT accessible from charlie
                .toolsCall("alpha").withErrorAssert(e -> assertEquals("Invalid tool name: alpha", e.message())).send()
                // shared is bound to default AND bravo → NOT accessible from charlie
                .toolsCall("shared").withErrorAssert(e -> assertEquals("Invalid tool name: shared", e.message())).send()
                // prompts
                .promptsGet("charliePrompt", r -> assertEquals("3", r.messages().get(0).content().asText().text()))
                .promptsGet("deltaPrompt", r -> assertEquals("4", r.messages().get(0).content().asText().text()))
                .promptsGet("alphaPrompt")
                .withErrorAssert(e -> assertEquals("Invalid prompt name: alphaPrompt", e.message())).send()
                // resources
                .resourcesRead("file://charlie", r -> assertEquals("3", r.contents().get(0).asText().text()))
                .resourcesRead("file://delta", r -> assertEquals("4", r.contents().get(0).asText().text()))
                .resourcesRead("file://alpha")
                .withErrorAssert(e -> assertEquals("Resource not found: file://alpha", e.message())).send()
                .thenAssertResults();
    }

    public static class MyFeatures {

        @Inject
        ToolManager toolManager;

        @Inject
        PromptManager promptManager;

        @Inject
        ResourceManager resourceManager;

        @Startup
        void start() {
            toolManager.newTool("echo")
                    .setHandler(ta -> ToolResponse.success("5"))
                    .setDescription("echo")
                    .register();
            toolManager.newTool("foxtrot")
                    .setServerName("bravo")
                    .setHandler(ta -> ToolResponse.success("6"))
                    .setDescription("foxtrot")
                    .register();
            promptManager.newPrompt("echoPrompt")
                    .setHandler(pa -> PromptResponse.withMessages(PromptMessage.withUserRole("5")))
                    .setDescription("echoPrompt")
                    .register();
            resourceManager.newResource("echoResource")
                    .setDescription("echoResource")
                    .setUri("file://echo")
                    .setHandler(ra -> new ResourceResponse(List.of(TextResourceContents.create("file://echo", "5"))))
                    .register();
        }

        @Tool
        String alpha() {
            return "1";
        }

        @Tool
        @McpServer("bravo")
        String bravo() {
            return "2";
        }

        // Bound to BOTH default and bravo via repeatable @McpServer
        @Tool
        @McpServer(DEFAULT)
        @McpServer("bravo")
        String shared() {
            return "shared";
        }

        @Prompt
        PromptMessage alphaPrompt() {
            return PromptMessage.withUserRole("1");
        }

        @Prompt
        @McpServer("bravo")
        PromptMessage bravoPrompt() {
            return PromptMessage.withUserRole("2");
        }

        // Bound to BOTH default and bravo via repeatable @McpServer
        @Prompt
        @McpServer(DEFAULT)
        @McpServer("bravo")
        PromptMessage sharedPrompt() {
            return PromptMessage.withUserRole("shared");
        }

        @Resource(uri = "file://alpha")
        TextResourceContents alphaResource(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "1");
        }

        @Resource(uri = "file://bravo")
        @McpServer("bravo")
        TextResourceContents bravoResource(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "2");
        }

        // Bound to BOTH default and bravo via repeatable @McpServer
        @Resource(uri = "file://shared")
        @McpServer(DEFAULT)
        @McpServer("bravo")
        TextResourceContents sharedResource(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "shared");
        }

    }

    @McpServer("charlie")
    public static class CharlieFeatures {

        // No method-level @McpServer → inherits class-level "charlie" only
        @Tool
        String charlie() {
            return "3";
        }

        // Method-level @McpServer(DEFAULT) + class-level "charlie" → BOTH (union)
        @McpServer(DEFAULT)
        @Tool
        String delta() {
            return "4";
        }

        // Method-level @McpServer("bravo") + class-level "charlie" → BOTH (union)
        @McpServer("bravo")
        @Tool
        String golf() {
            return "golf";
        }

        @Prompt
        PromptMessage charliePrompt() {
            return PromptMessage.withUserRole("3");
        }

        @McpServer(DEFAULT)
        @Prompt
        PromptMessage deltaPrompt() {
            return PromptMessage.withUserRole("4");
        }

        @Resource(uri = "file://charlie")
        TextResourceContents charlieResource(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "3");
        }

        @Resource(uri = "file://delta")
        @McpServer(DEFAULT)
        TextResourceContents deltaResource(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "4");
        }

    }

}
