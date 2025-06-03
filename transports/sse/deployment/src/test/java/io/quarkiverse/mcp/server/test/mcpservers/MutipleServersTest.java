package io.quarkiverse.mcp.server.test.mcpservers;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MutipleServersTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class, CharlieFeatures.class))
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
        initClient();
        JsonArray batch = new JsonArray();
        // Tool calls
        JsonObject msg1 = newToolCallMessage("alpha");
        batch.add(msg1);
        JsonObject msg2 = newToolCallMessage("bravo");
        batch.add(msg2);
        JsonObject msg3 = newToolCallMessage("charlie");
        batch.add(msg3);
        JsonObject msg4 = newToolCallMessage("delta");
        batch.add(msg4);
        JsonObject msg5 = newToolCallMessage("echo");
        batch.add(msg5);
        JsonObject msg6 = newToolCallMessage("foxtrot");
        batch.add(msg6);
        // Prompt gets
        JsonObject msg7 = newPromptGetMessage("alphaPrompt");
        batch.add(msg7);
        JsonObject msg8 = newPromptGetMessage("bravoPrompt");
        batch.add(msg8);
        JsonObject msg9 = newPromptGetMessage("charliePrompt");
        batch.add(msg9);
        JsonObject msg10 = newPromptGetMessage("deltaPrompt");
        batch.add(msg10);
        JsonObject msg11 = newPromptGetMessage("echoPrompt");
        batch.add(msg11);
        JsonObject msg12 = newPromptGetMessage("foxtrotPrompt");
        batch.add(msg12);
        // Resource reads
        JsonObject msg13 = newResourceReadMessage("file://1");
        batch.add(msg13);
        JsonObject msg14 = newResourceReadMessage("file://2");
        batch.add(msg14);
        JsonObject msg15 = newResourceReadMessage("file://3");
        batch.add(msg15);
        JsonObject msg16 = newResourceReadMessage("file://4");
        batch.add(msg16);
        JsonObject msg17 = newResourceReadMessage("file://5");
        batch.add(msg17);
        JsonObject msg18 = newResourceReadMessage("file://6");
        batch.add(msg18);

        send(batch.encode());

        assertToolTextContent(msg1, "1");
        assertErrorMessage(msg2, client().waitForResponse(msg2), "Invalid tool name: bravo");
        assertErrorMessage(msg3, client().waitForResponse(msg3), "Invalid tool name: charlie");
        assertToolTextContent(msg4, "4");
        assertToolTextContent(msg5, "5");
        assertErrorMessage(msg6, client().waitForResponse(msg6), "Invalid tool name: foxtrot");

        assertPromptMessageContent(msg7, "1");
        assertErrorMessage(msg8, client().waitForResponse(msg8), "Invalid prompt name: bravoPrompt");
        assertErrorMessage(msg9, client().waitForResponse(msg9), "Invalid prompt name: charliePrompt");
        assertPromptMessageContent(msg10, "4");
        assertPromptMessageContent(msg11, "5");
        assertErrorMessage(msg12, client().waitForResponse(msg12), "Invalid prompt name: foxtrotPrompt");

        assertResourceContent(msg13, "1");
        assertErrorMessage(msg14, client().waitForResponse(msg14), "Invalid resource uri: file://2");
        assertErrorMessage(msg15, client().waitForResponse(msg15), "Invalid resource uri: file://3");
        assertResourceContent(msg16, "4");
        assertResourceContent(msg17, "5");
        assertErrorMessage(msg18, client().waitForResponse(msg18), "Invalid resource uri: file://6");
    }

    private void assertToolTextContent(JsonObject msg, String expectedText) {
        JsonObject response = client().waitForResponse(msg);
        JsonObject result = assertResultResponse(msg, response);
        assertNotNull(result);
        assertFalse(result.getBoolean("isError"));
        JsonArray content = result.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        String text4 = textContent.getString("text");
        assertEquals(expectedText, text4);
    }

    private void assertPromptMessageContent(JsonObject msg, String expectedText) {
        JsonObject response = client().waitForResponse(msg);
        JsonObject result = assertResultResponse(msg, response);
        assertNotNull(result);
        JsonArray messages = result.getJsonArray("messages");
        assertEquals(1, messages.size());
        JsonObject textContent = messages.getJsonObject(0).getJsonObject("content");
        assertEquals(expectedText, textContent.getString("text"));
    }

    private void assertResourceContent(JsonObject msg, String expectedText) {
        JsonObject response = client().waitForResponse(msg);
        JsonObject result = assertResultResponse(msg, response);
        JsonArray contents = result.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject textContent = contents.getJsonObject(0);
        assertEquals(expectedText, textContent.getString("text"));
    }

    @Override
    protected String sseRootPath() {
        return "/alpha/mcp";
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
