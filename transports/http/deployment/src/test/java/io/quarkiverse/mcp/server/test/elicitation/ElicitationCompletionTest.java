package io.quarkiverse.mcp.server.test.elicitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationCompletion;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.UrlElicitationRequest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class ElicitationCompletionTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testElicitationCompletion() throws InterruptedException {
        // Open a subsidiary SSE stream so server can send notifications outside request-response cycles
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(
                        new ClientCapability(ClientCapability.ELICITATION, Map.of("form", Map.of(), "url", Map.of())))
                .setOpenSubsidiarySse(true)
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "urlElicitationWithCompletion"));
        client.sendAndForget(request);

        // Wait for the URL mode elicitation request
        JsonObject er = client.waitForRequests(1).requests().get(0);
        assertEquals("elicitation/create", er.getString("method"));
        String elicitationId = er.getJsonObject("params").getString("elicitationId");
        assertNotNull(elicitationId);

        Long id = er.getLong("id");
        // Client accepts
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("action", ElicitationResponse.Action.ACCEPT.toString().toLowerCase()))
                .put("id", id);
        client.sendAndForget(response);

        // Wait for the tool call response
        JsonObject toolCallResponse = client.waitForResponse(request);
        JsonObject toolCallResult = toolCallResponse.getJsonObject("result");
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));

        // Verify the completion notification was received
        // The first notification (index 0) is the subsidiary SSE log notification from connect()
        // The second notification (index 1) is our elicitation completion
        JsonObject notification = client.waitForNotifications(2).notifications().get(1);
        assertEquals("notifications/elicitation/complete", notification.getString("method"));
        assertEquals(elicitationId, notification.getJsonObject("params").getString("elicitationId"));
    }

    @Singleton
    public static class MyTools {

        @Inject
        ElicitationCompletion elicitationCompletion;

        @Tool
        Uni<String> urlElicitationWithCompletion(Elicitation elicitation) {
            UrlElicitationRequest urlRequest = elicitation.urlRequestBuilder()
                    .setMessage("Please authorize")
                    .setUrl("https://example.com/authorize")
                    .build();
            String elicitationId = urlRequest.elicitationId();
            return urlRequest.send().map(response -> {
                if (response.actionAccepted()) {
                    elicitationCompletion.send(elicitationId);
                    return "Accepted";
                }
                return "Not accepted";
            });
        }

    }

}
