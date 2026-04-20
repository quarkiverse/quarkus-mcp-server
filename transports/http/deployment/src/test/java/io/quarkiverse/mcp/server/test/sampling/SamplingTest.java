package io.quarkiverse.mcp.server.test.sampling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.ToolChoice;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolUseContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SamplingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testSampling() throws InterruptedException {
        McpSseTestClient client = McpAssured.newSseClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of("tools", Map.of())))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "samplingFoo"));
        client.sendAndForget(request);

        // The server should send a sampling request
        List<JsonObject> requests = client.waitForRequests(1).requests();
        assertEquals("sampling/createMessage", requests.get(0).getString("method"));
        Long id = requests.get(0).getLong("id");
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("role", "assistant")
                        .put("model", "claude-3-sonnet-20240307")
                        .put("content", new JsonObject()
                                .put("type", "text")
                                .put("text", "It's ok buddy.")))
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
        assertEquals("It's ok buddy.", textContent.getString("text"));
    }

    @Test
    public void testSamplingWithTools() throws InterruptedException {
        McpSseTestClient client = McpAssured.newSseClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of("tools", Map.of())))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "samplingWithTools"));
        client.sendAndForget(request);

        List<JsonObject> requests = client.waitForRequests(1).requests();
        JsonObject samplingRequest = requests.get(0);
        assertEquals("sampling/createMessage", samplingRequest.getString("method"));
        JsonObject params = samplingRequest.getJsonObject("params");
        assertEquals("required", params.getJsonObject("toolChoice").getString("mode"));
        JsonArray tools = params.getJsonArray("tools");
        assertEquals(1, tools.size());
        assertEquals("weather_lookup", tools.getJsonObject(0).getString("name"));
        assertEquals("text", params.getJsonArray("messages").getJsonObject(0).getJsonObject("content").getString("type"));

        Long id = samplingRequest.getLong("id");
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("role", "assistant")
                        .put("model", "claude-3-sonnet-20240307")
                        .put("stopReason", "toolUse")
                        .put("content", new JsonArray().add(new JsonObject()
                                .put("type", "tool_use")
                                .put("id", "call_1")
                                .put("name", "weather_lookup")
                                .put("input", new JsonObject().put("city", "Brno")))))
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
        assertEquals("toolUse:weather_lookup:Brno", textContent.getString("text"));
    }

    @Singleton
    public static class MyTools {

        @Inject
        ToolManager toolManager;

        @Tool(description = "A tool that is using sampling...")
        Uni<String> samplingFoo(Sampling sampling) {
            if (sampling.isSupported()) {
                SamplingRequest samplingRequest = sampling.requestBuilder()
                        .setMaxTokens(100)
                        .addMessage(SamplingMessage.withUserRole("What's happening?"))
                        .build();
                return samplingRequest.send().map(sr -> sr.content().asText().text());
            } else {
                return Uni.createFrom().item("You are nok");
            }
        }

        @Tool(name = "weather_lookup", description = "Get weather for a city")
        String weatherLookup(String city) {
            return city;
        }

        @Tool(description = "A tool that is using sampling with tools...")
        Uni<String> samplingWithTools(Sampling sampling) {
            SamplingRequest samplingRequest = sampling.requestBuilder()
                    .setMaxTokens(100)
                    .addMessage(SamplingMessage.withUserRole("What's the weather?"))
                    .addTool(toolManager.getTool("weather_lookup"))
                    .setToolChoice(new ToolChoice(ToolChoice.Mode.REQUIRED))
                    .build();
            return samplingRequest.send().map(sr -> {
                ToolUseContent toolUse = sr.content().asToolUse();
                return sr.stopReason() + ":" + toolUse.name() + ":" + toolUse.input().get("city");
            });
        }

    }

}
