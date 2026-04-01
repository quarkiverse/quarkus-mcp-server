package io.quarkiverse.mcp.server.test.streamablehttp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.PromptCompletionManager;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.TransportHint;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class TransportHintSkipSseTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(TransportHintSkipSseTest.class));

    @Inject
    ToolManager toolManager;

    @Inject
    PromptManager promptManager;

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceTemplateManager resourceTemplateManager;

    @Inject
    PromptCompletionManager promptCompletionManager;

    @Test
    public void testTools() throws Exception {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        HttpClient httpClient = HttpClient.newHttpClient();

        toolManager.newTool("skipSseTool")
                .setDescription("Tool that skips SSE init")
                .addHint(TransportHint.STREAMABLE_HTTP_SKIP_SSE_INIT)
                .setHandler(args -> ToolResponse.success("no-sse"))
                .register();

        toolManager.newTool("forceSseTool")
                .setDescription("Tool that forces SSE init")
                .setHandler(args -> ToolResponse.success("with-sse"))
                .register();

        assertContentType(httpClient, client, "application/json",
                newMessage("tools/call", new JsonObject().put("name", "skipSseTool").put("arguments", Map.of())));

        assertContentType(httpClient, client, "text/event-stream",
                newMessage("tools/call", new JsonObject().put("name", "forceSseTool").put("arguments", Map.of())));
    }

    @Test
    public void testPrompts() throws Exception {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        HttpClient httpClient = HttpClient.newHttpClient();

        promptManager.newPrompt("skipSsePrompt")
                .setDescription("Prompt that skips SSE init")
                .addHint(TransportHint.STREAMABLE_HTTP_SKIP_SSE_INIT)
                .setHandler(args -> new PromptResponse("test", List.of(PromptMessage.withUserRole("hello"))))
                .register();

        promptManager.newPrompt("forceSsePrompt")
                .setDescription("Prompt that forces SSE init")
                .setHandler(args -> new PromptResponse("test", List.of(PromptMessage.withUserRole("hello"))))
                .register();

        assertContentType(httpClient, client, "application/json",
                newMessage("prompts/get", new JsonObject().put("name", "skipSsePrompt")));

        assertContentType(httpClient, client, "text/event-stream",
                newMessage("prompts/get", new JsonObject().put("name", "forceSsePrompt")));
    }

    @Test
    public void testResources() throws Exception {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        HttpClient httpClient = HttpClient.newHttpClient();

        resourceManager.newResource("skipSseResource")
                .setUri("test:///skip-sse")
                .setDescription("Resource that skips SSE init")
                .addHint(TransportHint.STREAMABLE_HTTP_SKIP_SSE_INIT)
                .setHandler(
                        args -> new ResourceResponse(List.of(TextResourceContents.create(args.requestUri().value(), "no-sse"))))
                .register();

        resourceManager.newResource("forceSseResource")
                .setUri("test:///force-sse")
                .setDescription("Resource that forces SSE init")
                .setHandler(args -> new ResourceResponse(
                        List.of(TextResourceContents.create(args.requestUri().value(), "with-sse"))))
                .register();

        assertContentType(httpClient, client, "application/json",
                newMessage("resources/read", new JsonObject().put("uri", "test:///skip-sse")));

        assertContentType(httpClient, client, "text/event-stream",
                newMessage("resources/read", new JsonObject().put("uri", "test:///force-sse")));
    }

    @Test
    public void testCompletions() throws Exception {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        HttpClient httpClient = HttpClient.newHttpClient();

        // Register a prompt with an argument (needed as completion reference)
        promptManager.newPrompt("completionPrompt")
                .setDescription("Prompt for completion test")
                .addArgument("arg1", null, "test argument", false, null)
                .setHandler(args -> new PromptResponse("test", List.of(PromptMessage.withUserRole("hello"))))
                .register();

        promptCompletionManager.newCompletion("completionPrompt")
                .setArgumentName("arg1")
                .addHint(TransportHint.STREAMABLE_HTTP_SKIP_SSE_INIT)
                .setHandler(args -> new CompletionResponse(List.of("a", "b"), 2, false))
                .register();

        assertContentType(httpClient, client, "application/json",
                newMessage("completion/complete", new JsonObject()
                        .put("ref", new JsonObject().put("type", "ref/prompt").put("name", "completionPrompt"))
                        .put("argument", new JsonObject().put("name", "arg1").put("value", "a"))));

        // Register another prompt + completion without hint
        promptManager.newPrompt("completionPrompt2")
                .setDescription("Prompt for completion test 2")
                .addArgument("arg1", null, "test argument", false, null)
                .setHandler(args -> new PromptResponse("test", List.of(PromptMessage.withUserRole("hello"))))
                .register();

        promptCompletionManager.newCompletion("completionPrompt2")
                .setArgumentName("arg1")
                .setHandler(args -> new CompletionResponse(List.of("c", "d"), 2, false))
                .register();

        assertContentType(httpClient, client, "text/event-stream",
                newMessage("completion/complete", new JsonObject()
                        .put("ref", new JsonObject().put("type", "ref/prompt").put("name", "completionPrompt2"))
                        .put("argument", new JsonObject().put("name", "arg1").put("value", "c"))));
    }

    @Test
    public void testResourceTemplates() throws Exception {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        HttpClient httpClient = HttpClient.newHttpClient();

        resourceTemplateManager.newResourceTemplate("skipSseTemplate")
                .setUriTemplate("test:///skip-sse/{id}")
                .setDescription("Resource template that skips SSE init")
                .addHint(TransportHint.STREAMABLE_HTTP_SKIP_SSE_INIT)
                .setHandler(args -> new ResourceResponse(
                        List.of(TextResourceContents.create(args.requestUri().value(), "no-sse"))))
                .register();

        resourceTemplateManager.newResourceTemplate("forceSseTemplate")
                .setUriTemplate("test:///force-sse/{id}")
                .setDescription("Resource template that forces SSE init")
                .setHandler(args -> new ResourceResponse(
                        List.of(TextResourceContents.create(args.requestUri().value(), "with-sse"))))
                .register();

        assertContentType(httpClient, client, "application/json",
                newMessage("resources/read", new JsonObject().put("uri", "test:///skip-sse/1")));

        assertContentType(httpClient, client, "text/event-stream",
                newMessage("resources/read", new JsonObject().put("uri", "test:///force-sse/1")));
    }

    private void assertContentType(HttpClient httpClient, McpStreamableTestClient client, String expectedContentType,
            JsonObject message) throws Exception {
        HttpResponse<String> response = send(httpClient, client.mcpEndpoint(), client.mcpSessionId(), message);
        assertEquals(200, response.statusCode());
        assertEquals(expectedContentType, response.headers().firstValue("Content-Type").orElse(null));
    }

    private static int idCounter = 1;

    private JsonObject newMessage(String method, JsonObject params) {
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", idCounter++)
                .put("method", method)
                .put("params", params);
    }

    private HttpResponse<String> send(HttpClient httpClient, URI endpoint, String sessionId, JsonObject message)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Mcp-Session-Id", sessionId)
                .POST(BodyPublishers.ofString(message.encode()))
                .build();

        return httpClient.send(request, BodyHandlers.ofString());
    }
}
