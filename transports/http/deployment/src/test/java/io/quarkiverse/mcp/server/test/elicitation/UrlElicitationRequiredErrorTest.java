package io.quarkiverse.mcp.server.test.elicitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.UrlElicitationRequiredException;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class UrlElicitationRequiredErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testUrlElicitationRequiredError() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(
                        new ClientCapability(ClientCapability.ELICITATION, Map.of("form", Map.of(), "url", Map.of())))
                .build()
                .connect();

        // Use McpAssured for the error code/message assertion
        client.when()
                .toolsCall("toolRequiringElicitation")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.URL_ELICITATION_REQUIRED, error.code());
                    assertEquals("Authorization required", error.message());
                })
                .send()
                .thenAssertResults();

        // Also verify the error data using the lower-level API
        client = McpAssured.newStreamableClient()
                .setClientCapabilities(
                        new ClientCapability(ClientCapability.ELICITATION, Map.of("form", Map.of(), "url", Map.of())))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "toolRequiringElicitation"));
        client.sendAndForget(request);

        JsonObject response = client.waitForResponse(request);
        JsonObject error = response.getJsonObject("error");
        JsonObject data = error.getJsonObject("data");
        assertNotNull(data);
        JsonArray elicitations = data.getJsonArray("elicitations");
        assertEquals(1, elicitations.size());

        JsonObject elicitation = elicitations.getJsonObject(0);
        assertEquals("url", elicitation.getString("mode"));
        assertEquals("https://example.com/authorize", elicitation.getString("url"));
        assertEquals("Please authorize access to Example service", elicitation.getString("message"));
        assertNotNull(elicitation.getString("elicitationId"));
    }

    @Test
    public void testUrlElicitationRequiredErrorMultiple() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(
                        new ClientCapability(ClientCapability.ELICITATION, Map.of("form", Map.of(), "url", Map.of())))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "toolRequiringMultipleElicitations"));
        client.sendAndForget(request);

        JsonObject response = client.waitForResponse(request);
        JsonObject error = response.getJsonObject("error");
        assertNotNull(error);
        assertEquals(JsonRpcErrorCodes.URL_ELICITATION_REQUIRED, error.getInteger("code"));

        JsonArray elicitations = error.getJsonObject("data").getJsonArray("elicitations");
        assertEquals(2, elicitations.size());
        assertEquals("https://example.com/auth1", elicitations.getJsonObject(0).getString("url"));
        assertEquals("https://example.com/auth2", elicitations.getJsonObject(1).getString("url"));
    }

    @Singleton
    public static class MyTools {

        @Tool
        String toolRequiringElicitation() {
            var builder = UrlElicitationRequiredException.builder()
                    .setMessage("Authorization required");
            builder.addElicitation("https://example.com/authorize", "Please authorize access to Example service");
            throw builder.build();
        }

        @Tool
        String toolRequiringMultipleElicitations() {
            var builder = UrlElicitationRequiredException.builder()
                    .setMessage("Multiple authorizations required");
            builder.addElicitation("https://example.com/auth1", "Authorize service 1");
            builder.addElicitation("https://example.com/auth2", "Authorize service 2");
            throw builder.build();
        }

    }

}
