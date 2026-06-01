package io.quarkiverse.mcp.server.test.elicitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.Map;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.UrlElicitationRequest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class UrlElicitationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testUrlElicitation() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(
                        new ClientCapability(ClientCapability.ELICITATION, Map.of("form", Map.of(), "url", Map.of())))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "urlElicitationTool"));
        client.sendAndForget(request);

        // The server should send a URL mode elicitation request
        JsonObject er = client.waitForRequests(1).requests().get(0);
        assertEquals("elicitation/create", er.getString("method"));
        JsonObject params = er.getJsonObject("params");
        assertEquals("url", params.getString("mode"));
        assertEquals("Please provide your API key", params.getString("message"));
        assertEquals("https://example.com/provide-key", params.getString("url"));
        assertNotNull(params.getString("elicitationId"));

        Long id = er.getLong("id");
        // Client accepts the URL mode elicitation (no content for URL mode)
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("action", ElicitationResponse.Action.ACCEPT.toString().toLowerCase()))
                .put("id", id);
        client.sendAndForget(response);

        JsonObject toolCallResponse = client.waitForResponse(request);
        JsonObject toolCallResult = toolCallResponse.getJsonObject("result");
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("User accepted the URL elicitation", textContent.getString("text"));
    }

    @Test
    public void testUrlElicitationDeclined() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(
                        new ClientCapability(ClientCapability.ELICITATION, Map.of("form", Map.of(), "url", Map.of())))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "urlElicitationTool"));
        client.sendAndForget(request);

        JsonObject er = client.waitForRequests(1).requests().get(0);
        Long id = er.getLong("id");
        // Client declines
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("action", ElicitationResponse.Action.DECLINE.toString().toLowerCase()))
                .put("id", id);
        client.sendAndForget(response);

        JsonObject toolCallResponse = client.waitForResponse(request);
        JsonObject toolCallResult = toolCallResponse.getJsonObject("result");
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals("User declined", content.getJsonObject(0).getString("text"));
    }

    @Singleton
    public static class MyTools {

        @Tool
        Uni<String> urlElicitationTool(Elicitation elicitation) {
            if (elicitation.isUrlModeSupported()) {
                // Verify that completionTimeout < timeout throws IllegalStateException
                try {
                    elicitation.urlRequestBuilder()
                            .setMessage("test")
                            .setUrl("https://example.com")
                            .setTimeout(Duration.ofMinutes(5))
                            .setCompletionTimeout(Duration.ofSeconds(10))
                            .build();
                    throw new AssertionError("Should have thrown IllegalStateException");
                } catch (IllegalStateException expected) {
                }

                UrlElicitationRequest urlRequest = elicitation.urlRequestBuilder()
                        .setMessage("Please provide your API key")
                        .setUrl("https://example.com/provide-key")
                        .build();
                return urlRequest.send().map(response -> {
                    if (response.actionAccepted()) {
                        return "User accepted the URL elicitation";
                    } else {
                        return "User declined";
                    }
                });
            } else {
                return Uni.createFrom().item("URL mode not supported");
            }
        }

    }

}
