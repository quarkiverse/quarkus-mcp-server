package io.quarkiverse.mcp.server.test.mrtr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.InputResponses;
import io.quarkiverse.mcp.server.Root;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.SamplingResponse;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class InputRequiredTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testElicitationInputRequired() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();

        // 1. First call — no inputResponses, tool throws InputRequiredException
        client.when()
                .toolsCall("elicitationMrtr")
                .withRawAssert(firstResponse -> {
                    JsonObject result = firstResponse.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("input_required", result.getString("resultType"));

                    JsonObject inputRequests = result.getJsonObject("inputRequests");
                    assertNotNull(inputRequests);

                    JsonObject userInput = inputRequests.getJsonObject("userInput");
                    assertNotNull(userInput);
                    assertEquals("elicitation/create", userInput.getString("method"));
                    JsonObject params = userInput.getJsonObject("params");
                    assertEquals("form", params.getString("mode"));
                    assertEquals("Please provide your name", params.getString("message"));
                    JsonObject schema = params.getJsonObject("requestedSchema");
                    assertNotNull(schema);
                    assertEquals("string", schema.getJsonObject("properties").getJsonObject("name").getString("type"));
                    assertEquals("name", schema.getJsonArray("required").getString(0));

                    assertEquals("some-state", result.getString("requestState"));
                })
                .send()
                .thenAssertResults();

        // 2. Second call — with inputResponses and requestState, tool returns result
        client.when()
                .toolsCall("elicitationMrtr")
                .withInputResponses(new JsonObject()
                        .put("userInput", new JsonObject()
                                .put("action", "accept")
                                .put("content", new JsonObject()
                                        .put("name", "Alice"))))
                .withRequestState("some-state")
                .withAssert(r -> {
                    assertFalse(r.isError());
                    assertEquals("Hello Alice! [some-state]", r.firstContent().asText().text());
                })
                .send()
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    public void testSamplingInputRequired() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of()))
                .build()
                .connect();

        // 1. First call — tool throws InputRequiredException with sampling request
        client.when()
                .toolsCall("samplingMrtr")
                .withRawAssert(firstResponse -> {
                    JsonObject result = firstResponse.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("input_required", result.getString("resultType"));

                    JsonObject inputRequests = result.getJsonObject("inputRequests");
                    assertNotNull(inputRequests);

                    JsonObject llmInput = inputRequests.getJsonObject("llmInput");
                    assertNotNull(llmInput);
                    assertEquals("sampling/createMessage", llmInput.getString("method"));
                    JsonObject params = llmInput.getJsonObject("params");
                    assertNotNull(params);
                    assertEquals(100, params.getInteger("maxTokens"));
                    JsonArray messages = params.getJsonArray("messages");
                    assertNotNull(messages);
                    assertEquals(1, messages.size());
                    assertEquals("user", messages.getJsonObject(0).getString("role"));

                    assertNull(result.getString("requestState"));
                })
                .send()
                .thenAssertResults();

        // 2. Second call — with sampling inputResponse
        client.when()
                .toolsCall("samplingMrtr")
                .withInputResponses(new JsonObject()
                        .put("llmInput", new JsonObject()
                                .put("role", "assistant")
                                .put("model", "test-model")
                                .put("content", new JsonObject()
                                        .put("type", "text")
                                        .put("text", "The answer is 42"))))
                .withAssert(r -> {
                    assertFalse(r.isError());
                    assertEquals("LLM said: The answer is 42", r.firstContent().asText().text());
                })
                .send()
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    public void testRootsInputRequired() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(new ClientCapability(ClientCapability.ROOTS, Map.of()))
                .build()
                .connect();

        // 1. First call — tool throws InputRequiredException with roots request
        client.when()
                .toolsCall("rootsMrtr")
                .withRawAssert(firstResponse -> {
                    JsonObject result = firstResponse.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("input_required", result.getString("resultType"));

                    JsonObject inputRequests = result.getJsonObject("inputRequests");
                    assertNotNull(inputRequests);

                    JsonObject rootsInput = inputRequests.getJsonObject("rootsInput");
                    assertNotNull(rootsInput);
                    assertEquals("roots/list", rootsInput.getString("method"));
                    assertNotNull(rootsInput.getJsonObject("params"));
                })
                .send()
                .thenAssertResults();

        // 2. Second call — with roots inputResponse
        client.when()
                .toolsCall("rootsMrtr")
                .withInputResponses(new JsonObject()
                        .put("rootsInput", new JsonObject()
                                .put("roots", new JsonArray()
                                        .add(new JsonObject()
                                                .put("name", "project")
                                                .put("uri", "file:///home/user/project"))
                                        .add(new JsonObject()
                                                .put("name", "docs")
                                                .put("uri", "file:///home/user/docs")))))
                .withAssert(r -> {
                    assertFalse(r.isError());
                    assertEquals("Roots: project, docs", r.firstContent().asText().text());
                })
                .send()
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    public void testCombinedInputRequired() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(
                        new ClientCapability(ClientCapability.ELICITATION, Map.of()),
                        new ClientCapability(ClientCapability.SAMPLING, Map.of()))
                .build()
                .connect();

        // 1. First call — tool throws InputRequiredException with both elicitation and sampling requests
        client.when()
                .toolsCall("combinedMrtr")
                .withRawAssert(firstResponse -> {
                    JsonObject result = firstResponse.getJsonObject("result");
                    assertNotNull(result);
                    assertEquals("input_required", result.getString("resultType"));

                    JsonObject inputRequests = result.getJsonObject("inputRequests");
                    assertNotNull(inputRequests);

                    // Elicitation request
                    JsonObject userInput = inputRequests.getJsonObject("userInput");
                    assertNotNull(userInput);
                    assertEquals("elicitation/create", userInput.getString("method"));

                    // Sampling request
                    JsonObject llmInput = inputRequests.getJsonObject("llmInput");
                    assertNotNull(llmInput);
                    assertEquals("sampling/createMessage", llmInput.getString("method"));
                })
                .send()
                .thenAssertResults();

        // 2. Second call — with both inputResponses
        client.when()
                .toolsCall("combinedMrtr")
                .withInputResponses(new JsonObject()
                        .put("userInput", new JsonObject()
                                .put("action", "accept")
                                .put("content", new JsonObject()
                                        .put("name", "Bob")))
                        .put("llmInput", new JsonObject()
                                .put("role", "assistant")
                                .put("model", "test-model")
                                .put("content", new JsonObject()
                                        .put("type", "text")
                                        .put("text", "Hi Bob!"))))
                .withAssert(r -> {
                    assertFalse(r.isError());
                    assertEquals("Bob heard: Hi Bob!", r.firstContent().asText().text());
                })
                .send()
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    public void testIsServerInitiatedRequestSupported() {
        // Stateless client: isServerInitiatedRequestSupported() should return false
        McpStreamableTestClient statelessClient = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();

        statelessClient.when()
                .toolsCall("checkSupport", r -> {
                    assertFalse(r.isError());
                    assertEquals("false", r.firstContent().asText().text());
                })
                .thenAssertResults();
        statelessClient.disconnect();

        // Stateful client: isServerInitiatedRequestSupported() should return true
        McpStreamableTestClient statefulClient = McpAssured.newStreamableClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();

        statefulClient.when()
                .toolsCall("checkSupport", r -> {
                    assertFalse(r.isError());
                    assertEquals("true", r.firstContent().asText().text());
                })
                .thenAssertResults();
        statefulClient.disconnect();
    }

    @Test
    public void testInputResponsesEmptyOnInitialRequest() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();

        client.when()
                .toolsCall("checkInputResponses", r -> {
                    assertFalse(r.isError());
                    assertEquals("true", r.firstContent().asText().text());
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Singleton
    public static class MyTools {

        @Tool
        String elicitationMrtr(Elicitation elicitation) {
            InputResponses inputResponses = elicitation.inputResponses();
            if (!inputResponses.isEmpty() && inputResponses.has("userInput")) {
                ElicitationResponse response = inputResponses.getElicitationResponse("userInput");
                if (response.actionAccepted()) {
                    String state = elicitation.requestState() != null ? " [" + elicitation.requestState() + "]" : "";
                    return "Hello " + response.content().getString("name") + "!" + state;
                }
                return "Declined";
            }

            ElicitationRequest request = elicitation.requestBuilder()
                    .setMessage("Please provide your name")
                    .addSchemaProperty("name", new StringSchema(true))
                    .build();
            throw elicitation.inputRequired()
                    .addElicitationRequest("userInput", request)
                    .setRequestState("some-state")
                    .build();
        }

        @Tool
        String samplingMrtr(Sampling sampling) {
            InputResponses inputResponses = sampling.inputResponses();
            if (!inputResponses.isEmpty() && inputResponses.has("llmInput")) {
                SamplingResponse response = inputResponses.getSamplingResponse("llmInput");
                return "LLM said: " + response.content().asText().text();
            }

            SamplingRequest request = sampling.requestBuilder()
                    .addMessage(SamplingMessage.withUserRole("What is the meaning of life?"))
                    .setMaxTokens(100)
                    .build();
            throw sampling.inputRequired()
                    .addSamplingRequest("llmInput", request)
                    .build();
        }

        @Tool
        String rootsMrtr(Roots roots) {
            InputResponses inputResponses = roots.inputResponses();
            if (!inputResponses.isEmpty() && inputResponses.has("rootsInput")) {
                List<Root> rootList = inputResponses.getRootsResponse("rootsInput");
                String names = rootList.stream().map(Root::name).reduce((a, b) -> a + ", " + b).orElse("");
                return "Roots: " + names;
            }

            throw roots.inputRequired()
                    .addRootsRequest("rootsInput")
                    .build();
        }

        @Tool
        String combinedMrtr(Elicitation elicitation, Sampling sampling) {
            InputResponses inputResponses = elicitation.inputResponses();
            if (!inputResponses.isEmpty() && inputResponses.has("userInput") && inputResponses.has("llmInput")) {
                ElicitationResponse elicitationResponse = inputResponses.getElicitationResponse("userInput");
                SamplingResponse samplingResponse = inputResponses.getSamplingResponse("llmInput");
                String name = elicitationResponse.content().getString("name");
                String llmText = samplingResponse.content().asText().text();
                return name + " heard: " + llmText;
            }

            ElicitationRequest elicitationRequest = elicitation.requestBuilder()
                    .setMessage("What is your name?")
                    .addSchemaProperty("name", new StringSchema(true))
                    .build();
            SamplingRequest samplingRequest = sampling.requestBuilder()
                    .addMessage(SamplingMessage.withUserRole("Greet the user"))
                    .setMaxTokens(50)
                    .build();
            throw elicitation.inputRequired()
                    .addElicitationRequest("userInput", elicitationRequest)
                    .addSamplingRequest("llmInput", samplingRequest)
                    .build();
        }

        @Tool
        String checkSupport(Elicitation elicitation) {
            return String.valueOf(elicitation.isServerInitiatedRequestSupported());
        }

        @Tool
        String checkInputResponses(Elicitation elicitation) {
            return String.valueOf(elicitation.inputResponses().isEmpty());
        }
    }

}
