package io.quarkiverse.mcp.server.sse.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusTest
class ServerFeaturesTest {

    @Test
    void testPrompt() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .promptsList(p -> {
                    assertEquals(1, p.size());
                    PromptInfo codeAssist = p.findByName("code_assist");
                    assertEquals(1, codeAssist.arguments().size());
                    assertEquals("lang", codeAssist.arguments().get(0).name());
                    assertTrue(codeAssist.arguments().get(0).required());

                })
                .promptsGet("code_assist", Map.of("lang", "java"), r -> {
                    assertEquals("System.out.println(\"Hello world!\");", r.messages().get(0).content().asText().text());
                })
                .thenAssertResults();
    }

    @Test
    void testTool() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(p -> {
                    assertEquals(5, p.size());
                    JsonObject schema = p.findByName("toLowerCase").inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertEquals(1, properties.size());
                    JsonObject valueProperty = properties.getJsonObject("value");
                    assertNotNull(valueProperty);
                    assertEquals("string", valueProperty.getString("type"));
                    assertNotNull(p.findByName("answer"));

                })
                .toolsCall("toLowerCase", Map.of("value", "LooP"), r -> {
                    assertEquals("loop", r.firstContent().asText().text());
                })
                .toolsCall("answer", Map.of("question", "Are you ok?"), r -> {
                    assertEquals("{\"value\":\"are you ok?\"}", r.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    @Test
    void testToolIcons() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(p -> {
                    JsonArray icons = p.findByName("toLowerCase").icons();
                    assertNotNull(icons);
                    assertEquals(1, icons.size());
                    assertEquals("file://tool-icon", icons.getJsonObject(0).getString("src"));
                    assertEquals("image/png", icons.getJsonObject(0).getString("mimeType"));
                })
                .thenAssertResults();
    }

    @Test
    void testResource() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesList(p -> {
                    assertEquals(1, p.size());
                    assertEquals("alpha", p.findByUri("file:///project/alpha").name());
                })
                .resourcesRead("file:///project/alpha", r -> {
                    assertEquals(Base64.getMimeEncoder().encodeToString("data".getBytes()),
                            r.contents().get(0).asBlob().blob());
                })
                .thenAssertResults();
    }

    @Test
    void testSampling() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of()))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "samplingTool"));
        client.sendAndForget(request);

        // The server should send a sampling request
        JsonObject sr = client.waitForRequests(1).requests().get(0);
        assertEquals("sampling/createMessage", sr.getString("method"));
        Long id = sr.getLong("id");
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("role", "assistant")
                        .put("model", "test-model")
                        .put("content", new JsonObject()
                                .put("type", "text")
                                .put("text", "Hello from sampling.")))
                .put("id", id);
        client.sendAndForget(response);

        JsonObject toolCallResponse = client.waitForResponse(request);
        JsonObject toolCallResult = toolCallResponse.getJsonObject("result");
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        assertEquals("Hello from sampling.", content.getJsonObject(0).getString("text"));
    }

    @Test
    void testElicitation() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "elicitationTool"));
        client.sendAndForget(request);

        // The server should send an elicitation request
        JsonObject er = client.waitForRequests(1).requests().get(0);
        assertEquals("elicitation/create", er.getString("method"));
        JsonObject params = er.getJsonObject("params");
        assertEquals("What's your name?", params.getString("message"));
        JsonObject schemaProperties = params.getJsonObject("requestedSchema").getJsonObject("properties");
        assertEquals("string", schemaProperties.getJsonObject("name").getString("type"));

        Long id = er.getLong("id");
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("action", ElicitationResponse.Action.ACCEPT.toString().toLowerCase())
                        .put("content", new JsonObject()
                                .put("name", "Quarkus")))
                .put("id", id);
        client.sendAndForget(response);

        JsonObject toolCallResponse = client.waitForResponse(request);
        JsonObject toolCallResult = toolCallResponse.getJsonObject("result");
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        assertEquals("Hello Quarkus!", content.getJsonObject(0).getString("text"));
    }
}
