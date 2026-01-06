package io.quarkiverse.mcp.server.test.elicitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationRequest.EnumSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.MultiSelectEnumSchema;
import io.quarkiverse.mcp.server.ElicitationRequest.SingleSelectEnumSchema;
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

public class ElicitationEnumSchemaTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
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

        JsonObject legacy = schemaProperties.getJsonObject("legacy");
        assertEquals("string", legacy.getString("type"));
        assertEquals(List.of("foo"), legacy.getJsonArray("enum").stream().map(Object::toString).toList());

        JsonObject singleUntitled = schemaProperties.getJsonObject("singleUntitled");
        assertEquals("string", singleUntitled.getString("type"));
        assertEquals(List.of("alpha", "bravo"), singleUntitled.getJsonArray("enum").stream().map(Object::toString).toList());
        assertEquals("bravo", singleUntitled.getString("default"));

        JsonObject singleTitled = schemaProperties.getJsonObject("singleTitled");
        assertEquals("string", singleTitled.getString("type"));
        assertEquals(List.of("alpha:Title alpha", "bravo:Title bravo"), singleTitled.getJsonArray("oneOf").stream().map(o -> {
            if (o instanceof JsonObject obj) {
                return obj.getString("const") + ":" + obj.getString("title");
            }
            return o.toString();
        }).toList());

        JsonObject multiUntitled = schemaProperties.getJsonObject("multiUntitled");
        assertEquals("array", multiUntitled.getString("type"));
        assertEquals("Muj title", multiUntitled.getString("title"));
        assertEquals(2, multiUntitled.getInteger("maxItems"));
        assertEquals(List.of("alpha", "bravo"),
                multiUntitled.getJsonObject("items").getJsonArray("enum").stream().map(Object::toString).toList());

        JsonObject multiTitled = schemaProperties.getJsonObject("multiTitled");
        assertEquals("array", multiTitled.getString("type"));
        assertEquals("Muj description", multiTitled.getString("description"));
        assertEquals(1, multiTitled.getInteger("minItems"));
        assertEquals(List.of("alpha:Title alpha", "bravo:Title bravo"),
                multiTitled.getJsonObject("items").getJsonArray("anyOf").stream().map(o -> {
                    if (o instanceof JsonObject obj) {
                        return obj.getString("const") + ":" + obj.getString("title");
                    }
                    return o.toString();
                }).toList());

        Long id = er.getLong("id");
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("_meta", new JsonObject().put("foo", "bar"))
                        .put("action", ElicitationResponse.Action.ACCEPT.toString().toLowerCase())
                        .put("content", new JsonObject()
                                .put("legacy", "foo")
                                .put("singleUntitled", "bravo")
                                .put("singleTitled", "alpha")
                                .put("multiUntitled", new JsonArray(List.of("alpha")))
                                .put("multiTitled", new JsonArray(List.of("bravo", "alpha")))))
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
        assertEquals("It's ok: foo:bravo:alpha:alpha:bravo", textContent.getString("text"));
    }

    @Singleton
    public static class MyTools {

        @Tool
        Uni<String> elicitationFoo(Elicitation elicitation) {
            if (elicitation.isSupported()) {
                ElicitationRequest request = elicitation.requestBuilder()
                        .setMessage("What's your github account?")
                        .addSchemaProperty("legacy", new EnumSchema(List.of("foo")))
                        .addSchemaProperty("singleUntitled",
                                new SingleSelectEnumSchema(List.of("alpha", "bravo"), "bravo"))
                        .addSchemaProperty("singleTitled",
                                new SingleSelectEnumSchema(List.of("alpha", "bravo"), List.of("Title alpha", "Title bravo")))
                        .addSchemaProperty("multiUntitled",
                                new MultiSelectEnumSchema("Muj title", "Muj description", List.of("alpha", "bravo"), null, 1, 2,
                                        false, List.of("alpha", "bravo")))
                        .addSchemaProperty("multiTitled",
                                new MultiSelectEnumSchema("Muj title", "Muj description", List.of("alpha", "bravo"),
                                        List.of("Title alpha", "Title bravo"), 1, 2, false, List.of("alpha", "bravo")))
                        .build();
                return request.send().map(response -> {
                    if (response.actionAccepted()
                            && "bar".equals(response.meta().getValue(MetaKey.of("foo")))) {
                        return "It's ok: " + response.content().getString("legacy") +
                                ":" + response.content().getString("singleUntitled") +
                                ":" + response.content().getString("singleTitled") +
                                ":" + response.content().getStrings("multiUntitled").get(0) +
                                ":" + response.content().getStrings("multiTitled").get(0);
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
