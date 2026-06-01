package io.quarkiverse.mcp.server.test.elicitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class ElicitationCapabilityTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testEmptyCapabilityIsFormOnly() throws InterruptedException {
        // Empty elicitation: {} is equivalent to form mode only
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "capabilityCheck"));
        client.sendAndForget(request);

        // The tool sends a form mode elicitation (form is supported)
        JsonObject er = client.waitForRequests(1).requests().get(0);
        assertEquals("elicitation/create", er.getString("method"));
        assertEquals("form", er.getJsonObject("params").getString("mode"));

        Long id = er.getLong("id");
        client.sendAndForget(new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("action", ElicitationResponse.Action.ACCEPT.toString().toLowerCase())
                        .put("content", new JsonObject().put("name", "test")))
                .put("id", id));

        JsonObject toolCallResponse = client.waitForResponse(request);
        JsonObject toolCallResult = toolCallResponse.getJsonObject("result");
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        assertEquals("form=true,url=false",
                toolCallResult.getJsonArray("content").getJsonObject(0).getString("text"));
    }

    @Test
    public void testFormAndUrlCapability() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(
                        new ClientCapability(ClientCapability.ELICITATION, Map.of("form", Map.of(), "url", Map.of())))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "capabilityCheck"));
        client.sendAndForget(request);

        JsonObject er = client.waitForRequests(1).requests().get(0);
        assertEquals("elicitation/create", er.getString("method"));

        Long id = er.getLong("id");
        client.sendAndForget(new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("action", ElicitationResponse.Action.ACCEPT.toString().toLowerCase())
                        .put("content", new JsonObject().put("name", "test")))
                .put("id", id));

        JsonObject toolCallResponse = client.waitForResponse(request);
        assertEquals("form=true,url=true",
                toolCallResponse.getJsonObject("result").getJsonArray("content").getJsonObject(0).getString("text"));
    }

    @Test
    public void testUrlOnlyCapability() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(
                        new ClientCapability(ClientCapability.ELICITATION, Map.of("url", Map.of())))
                .build()
                .connect();

        client.when()
                .toolsCall("urlOnlyCheck", r -> {
                    assertFalse(r.isError());
                    assertEquals("form=false,url=true", r.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    @Singleton
    public static class MyTools {

        @Tool
        Uni<String> capabilityCheck(Elicitation elicitation) {
            boolean form = elicitation.isFormModeSupported();
            boolean url = elicitation.isUrlModeSupported();
            return elicitation.requestBuilder()
                    .setMessage("Name?")
                    .addSchemaProperty("name", new StringSchema(true))
                    .build()
                    .send()
                    .map(r -> "form=" + form + ",url=" + url);
        }

        @Tool
        String urlOnlyCheck(Elicitation elicitation) {
            boolean form = elicitation.isFormModeSupported();
            boolean url = elicitation.isUrlModeSupported();
            return "form=" + form + ",url=" + url;
        }

    }

}
