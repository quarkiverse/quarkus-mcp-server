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
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ElicitationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testElicitation() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "elicitationFoo"));
        client.sendAndForget(request);

        // The server should send an elicitation request
        JsonObject er = client.waitForRequests(1).requests().get(0);
        assertEquals("elicitation/create", er.getString("method"));
        JsonObject params = er.getJsonObject("params");
        assertEquals("What's your github account?", params.getString("message"));
        JsonObject schema = params.getJsonObject("requestedSchema");
        assertNotNull(schema);
        JsonObject schemaProperties = schema.getJsonObject("properties");
        assertEquals("string", schemaProperties.getJsonObject("username").getString("type"));
        assertEquals("username", schema.getJsonArray("required").getString(0));
        Long id = er.getLong("id");
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("_meta", new JsonObject().put("foo", "bar"))
                        .put("action", ElicitationResponse.Action.ACCEPT.toString().toLowerCase())
                        .put("content", new JsonObject()
                                .put("username", "mkouba")))
                .put("id", id);
        // Send the response back to the server
        client.sendAndForget(response);

        JsonObject toolCallResponse = client.waitForResponse(request);
        JsonObject toolCallResult = toolCallResponse.getJsonObject("result");
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("It's ok mkouba.", textContent.getString("text"));
    }

    @Singleton
    public static class MyTools {

        @Tool
        Uni<String> elicitationFoo(Elicitation elicitation) {
            if (elicitation.isSupported()) {
                ElicitationRequest request = elicitation.requestBuilder()
                        .setMessage("What's your github account?")
                        .addSchemaProperty("username", new StringSchema(true))
                        .build();
                return request.send().map(response -> {
                    if (response.actionAccepted()
                            && "bar".equals(response.meta().getValue(MetaKey.of("foo")))) {
                        return "It's ok " + response.content().getString("username") + ".";
                    } else {
                        return "Not accepted";
                    }
                });
            } else {
                return Uni.createFrom().item("You are nok");
            }
        }

    }

}
