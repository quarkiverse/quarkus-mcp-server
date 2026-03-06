package io.quarkiverse.mcp.server.websocket.test.mcpservers;

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
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.websocket.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;

public class MultipleServersMultiBindingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class, CharlieFeatures.class))
            .overrideConfigKey("quarkus.mcp.server.support-multi-server-bindings", "true")
            .overrideConfigKey("quarkus.mcp.server.websocket.endpoint-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.websocket.endpoint-path", "/bravo/mcp")
            .overrideConfigKey("quarkus.mcp.server.charlie.websocket.endpoint-path", "/charlie/mcp");

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

        ResourceManager.ResourceInfo sharedResource = resourceManager.getResource("file://shared");
        assertNotNull(sharedResource);
        assertThat(sharedResource.serverNames()).containsExactlyInAnyOrder(DEFAULT, "bravo");
    }

    @Test
    public void testDefaultServer() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setEndpointPath("/alpha/mcp")
                .build()
                .connect();

        client.when()
                .toolsCall("alpha", r -> assertEquals("1", r.content().get(0).asText().text()))
                .toolsCall("shared", r -> assertEquals("shared", r.content().get(0).asText().text()))
                .toolsCall("bravo").withErrorAssert(e -> assertEquals("Invalid tool name: bravo", e.message())).send()
                .toolsCall("charlie").withErrorAssert(e -> assertEquals("Invalid tool name: charlie", e.message())).send()
                .toolsCall("delta", r -> assertEquals("4", r.content().get(0).asText().text()))
                .toolsCall("golf").withErrorAssert(e -> assertEquals("Invalid tool name: golf", e.message())).send()
                .toolsCall("echo", r -> assertEquals("5", r.content().get(0).asText().text()))
                .toolsCall("foxtrot").withErrorAssert(e -> assertEquals("Invalid tool name: foxtrot", e.message())).send()
                .promptsGet("alphaPrompt", r -> assertEquals("1", r.messages().get(0).content().asText().text()))
                .promptsGet("sharedPrompt", r -> assertEquals("shared", r.messages().get(0).content().asText().text()))
                .promptsGet("bravoPrompt").withErrorAssert(e -> assertEquals("Invalid prompt name: bravoPrompt", e.message()))
                .send()
                .resourcesRead("file://alpha", r -> assertEquals("1", r.contents().get(0).asText().text()))
                .resourcesRead("file://shared", r -> assertEquals("shared", r.contents().get(0).asText().text()))
                .resourcesRead("file://bravo")
                .withErrorAssert(e -> assertEquals("Resource not found: file://bravo", e.message()))
                .send()
                .thenAssertResults();
    }

    @Test
    public void testBravoServer() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setEndpointPath("/bravo/mcp")
                .build()
                .connect();

        client.when()
                .toolsCall("bravo", r -> assertEquals("2", r.content().get(0).asText().text()))
                .toolsCall("shared", r -> assertEquals("shared", r.content().get(0).asText().text()))
                .toolsCall("alpha").withErrorAssert(e -> assertEquals("Invalid tool name: alpha", e.message())).send()
                .toolsCall("golf", r -> assertEquals("golf", r.content().get(0).asText().text()))
                .toolsCall("foxtrot", r -> assertEquals("6", r.content().get(0).asText().text()))
                .promptsGet("sharedPrompt", r -> assertEquals("shared", r.messages().get(0).content().asText().text()))
                .promptsGet("bravoPrompt", r -> assertEquals("2", r.messages().get(0).content().asText().text()))
                .promptsGet("alphaPrompt").withErrorAssert(e -> assertEquals("Invalid prompt name: alphaPrompt", e.message()))
                .send()
                .resourcesRead("file://shared", r -> assertEquals("shared", r.contents().get(0).asText().text()))
                .resourcesRead("file://bravo", r -> assertEquals("2", r.contents().get(0).asText().text()))
                .resourcesRead("file://alpha")
                .withErrorAssert(e -> assertEquals("Resource not found: file://alpha", e.message())).send()
                .thenAssertResults();
    }

    @Test
    public void testCharlieServer() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setEndpointPath("/charlie/mcp")
                .build()
                .connect();

        client.whenBatch()
                .toolsCall("charlie", r -> assertEquals("3", r.content().get(0).asText().text()))
                .toolsCall("delta", r -> assertEquals("4", r.content().get(0).asText().text()))
                .toolsCall("golf", r -> assertEquals("golf", r.content().get(0).asText().text()))
                .toolsCall("alpha").withErrorAssert(e -> assertEquals("Invalid tool name: alpha", e.message())).send()
                .toolsCall("shared").withErrorAssert(e -> assertEquals("Invalid tool name: shared", e.message())).send()
                .promptsGet("charliePrompt", r -> assertEquals("3", r.messages().get(0).content().asText().text()))
                .promptsGet("deltaPrompt", r -> assertEquals("4", r.messages().get(0).content().asText().text()))
                .promptsGet("alphaPrompt")
                .withErrorAssert(e -> assertEquals("Invalid prompt name: alphaPrompt", e.message())).send()
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

        @Resource(uri = "file://shared")
        @McpServer(DEFAULT)
        @McpServer("bravo")
        TextResourceContents sharedResource(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "shared");
        }

    }

    @McpServer("charlie")
    public static class CharlieFeatures {

        @Tool
        String charlie() {
            return "3";
        }

        @McpServer(DEFAULT)
        @Tool
        String delta() {
            return "4";
        }

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
