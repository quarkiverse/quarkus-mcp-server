package io.quarkiverse.mcp.server.websocket.test.mcpservers;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
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

public class MutipleServersTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class, CharlieFeatures.class))
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
    public void testDefaultServer() {
        ToolManager.ToolInfo bravo = toolManager.getTool("bravo");
        assertNotNull(bravo);
        assertEquals("bravo", bravo.serverName());
        ToolManager.ToolInfo echo = toolManager.getTool("echo");
        assertNotNull(echo);
        assertEquals(McpServer.DEFAULT, echo.serverName());
        PromptManager.PromptInfo bravoPrompt = promptManager.getPrompt("bravoPrompt");
        assertNotNull(bravoPrompt);
        assertEquals("bravo", bravoPrompt.serverName());
        ResourceManager.ResourceInfo bravoResource = resourceManager.getResource("file://2");
        assertNotNull(bravoResource);
        assertEquals("bravo", bravoResource.serverName());

        // Init client for the default server
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setEndpointPath("/alpha/mcp")
                .build()
                .connect();

        client.whenBatch()
                // tools
                .toolsCall("alpha", r -> assertEquals("1", r.content().get(0).asText().text()))
                .toolsCall("bravo").withErrorAssert(e -> assertEquals("Invalid tool name: bravo", e.message())).send()
                .toolsCall("charlie").withErrorAssert(e -> assertEquals("Invalid tool name: charlie", e.message())).send()
                .toolsCall("delta", r -> assertEquals("4", r.content().get(0).asText().text()))
                .toolsCall("echo", r -> assertEquals("5", r.content().get(0).asText().text()))
                .toolsCall("foxtrot").withErrorAssert(e -> assertEquals("Invalid tool name: foxtrot", e.message())).send()
                // prompts
                .promptsGet("alphaPrompt", r -> assertEquals("1", r.messages().get(0).content().asText().text()))
                .promptsGet("bravoPrompt").withErrorAssert(e -> assertEquals("Invalid prompt name: bravoPrompt", e.message()))
                .send()
                .promptsGet("charliePrompt")
                .withErrorAssert(e -> assertEquals("Invalid prompt name: charliePrompt", e.message())).send()
                .promptsGet("deltaPrompt", r -> assertEquals("4", r.messages().get(0).content().asText().text()))
                .promptsGet("echoPrompt", r -> assertEquals("5", r.messages().get(0).content().asText().text()))
                .promptsGet("foxtrotPrompt")
                .withErrorAssert(e -> assertEquals("Invalid prompt name: foxtrotPrompt", e.message())).send()
                // resources
                .resourcesRead("file://1", r -> assertEquals("1", r.contents().get(0).asText().text()))
                .resourcesRead("file://2").withErrorAssert(e -> assertEquals("Resource not found: file://2", e.message()))
                .send()
                .resourcesRead("file://3").withErrorAssert(e -> assertEquals("Resource not found: file://3", e.message()))
                .send()
                .resourcesRead("file://4", r -> assertEquals("4", r.contents().get(0).asText().text()))
                .resourcesRead("file://5", r -> assertEquals("5", r.contents().get(0).asText().text()))
                .resourcesRead("file://6").withErrorAssert(e -> assertEquals("Resource not found: file://6", e.message()))
                .send()
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
            promptManager.newPrompt("foxtrotPrompt")
                    .setServerName("bravo")
                    .setHandler(pa -> PromptResponse.withMessages(PromptMessage.withUserRole("6")))
                    .setDescription("foxtrot")
                    .register();
            resourceManager.newResource("echoResource")
                    .setDescription("echoResource")
                    .setUri("file://5")
                    .setHandler(ra -> new ResourceResponse(List.of(TextResourceContents.create("file://5", "5"))))
                    .register();
            resourceManager.newResource("foxtrotResource")
                    .setDescription("foxtrotResource")
                    .setUri("file://6")
                    .setServerName("bravo")
                    .setHandler(ra -> new ResourceResponse(List.of(TextResourceContents.create("file://6", "6"))))
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

        @Prompt
        PromptMessage alphaPrompt() {
            return PromptMessage.withUserRole("1");
        }

        @Prompt
        @McpServer("bravo")
        PromptMessage bravoPrompt() {
            return PromptMessage.withUserRole("2");
        }

        @Resource(uri = "file://1")
        TextResourceContents alphaResource(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "1");
        }

        @Resource(uri = "file://2")
        @McpServer("bravo")
        TextResourceContents bravoResource(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "2");
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

        @Prompt
        PromptMessage charliePrompt() {
            return PromptMessage.withUserRole("3");
        }

        @McpServer(DEFAULT)
        @Prompt
        PromptMessage deltaPrompt() {
            return PromptMessage.withUserRole("4");
        }

        @Resource(uri = "file://3")
        TextResourceContents charlieResource(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "3");
        }

        @Resource(uri = "file://4")
        @McpServer(DEFAULT)
        TextResourceContents deltaResource(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "4");
        }

    }

}
