package io.quarkiverse.mcp.server.test.clienterrors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationCompletion;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.ServerRequests;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ServerRequestResponseTest extends McpServerTest {

    private static final String[] REQUEST_TYPES = { "sampling", "roots", "form", "url" };

    static String[] requestTypes() {
        return REQUEST_TYPES;
    }

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Inject
    ServerRequests serverRequests;

    @Inject
    ElicitationCompletion elicitationCompletion;

    @Inject
    MyTools tools;

    McpStreamableTestClient client;

    @BeforeEach
    void connect() {
        tools.outcome = new CompletableFuture<>();
        client = McpAssured.newStreamableClient()
                .setProtocolVersion(McpProtocolVersion.V_2025_11_25)
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of()),
                        new ClientCapability(ClientCapability.ROOTS, Map.of()),
                        new ClientCapability(ClientCapability.ELICITATION, Map.of("form", Map.of(), "url", Map.of())))
                .build().connect();
    }

    @AfterEach
    void disconnect() {
        if (client != null) {
            client.terminateSession();
            client.close();
        }
    }

    @ParameterizedTest
    @MethodSource("requestTypes")
    void clientErrorCompletesRequest(String type) {
        assertClientError(type, new JsonObject().put("code", -32603)
                .put("message", "Client could not complete request")
                .put("data", new JsonObject().put("reason", "unavailable")));
    }

    @Test
    void clientErrorWithoutDataCompletesRequest() {
        assertClientError("sampling", new JsonObject().put("code", -1)
                .put("message", "User rejected sampling request"));
    }

    private void assertClientError(String type, JsonObject error) {
        JsonObject toolCall = callTool(type);
        JsonObject request = serverRequest(type);
        respond(request, "error", error);

        McpException failure = assertInstanceOf(McpException.class, failure());
        assertEquals(error.getInteger("code"), failure.getJsonRpcErrorCode());
        assertEquals(error.getString("message"), failure.getMessage());
        assertEquals(error.getValue("data"), failure.getData());
        assertCleanedUp(request);
        JsonObject responseError = client.waitForResponse(toolCall).getJsonObject("error");
        assertNotNull(responseError);
        assertEquals(error, responseError);
    }

    @ParameterizedTest
    @MethodSource("requestTypes")
    void malformedResultCompletesRequest(String type) {
        JsonObject toolCall = callTool(type);
        JsonObject request = serverRequest(type);
        // Valid JSON-RPC result envelope, but invalid content for each response parser.
        respond(request, "result", new JsonObject().put("role", "invalid").put("action", "invalid"));

        Class<? extends Throwable> expected = type.equals("roots") ? NullPointerException.class
                : IllegalArgumentException.class;
        assertInstanceOf(expected, failure());
        assertCleanedUp(request);
        JsonObject error = client.waitForResponse(toolCall).getJsonObject("error");
        assertNotNull(error);
        assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.getInteger("code"));
    }

    @ParameterizedTest
    @MethodSource("requestTypes")
    void successCompletesRequest(String type) throws Exception {
        JsonObject toolCall = callTool(type);
        JsonObject request = serverRequest(type);
        JsonObject result = switch (type) {
            case "sampling" -> new JsonObject().put("role", "assistant").put("model", "test-model")
                    .put("content", new JsonObject().put("type", "text").put("text", "Hello"));
            case "roots" -> new JsonObject().put("roots", new JsonArray()
                    .add(new JsonObject().put("name", "project").put("uri", "file:///project")));
            case "form" -> new JsonObject().put("action", "accept").put("content", new JsonObject().put("name", "test"));
            case "url" -> new JsonObject().put("action", "accept");
            default -> throw new IllegalArgumentException(type);
        };
        respond(request, "result", result);

        String expected = switch (type) {
            case "sampling" -> "Hello";
            case "roots" -> "file:///project";
            case "form" -> "test";
            case "url" -> "ACCEPT";
            default -> throw new IllegalArgumentException(type);
        };
        assertEquals(expected, tools.outcome.get(5, TimeUnit.SECONDS));
        assertFalse(serverRequests.hasResponseHandler(request.getLong("id")));
        JsonObject toolResult = client.waitForResponse(toolCall).getJsonObject("result");
        assertNotNull(toolResult);
        assertFalse(toolResult.getBoolean("isError"));
        assertEquals(expected, toolResult.getJsonArray("content").getJsonObject(0).getString("text"));
        if (type.equals("url")) {
            String elicitationId = request.getJsonObject("params").getString("elicitationId");
            assertTrue(serverRequests.hasPendingElicitation(elicitationId));
            elicitationCompletion.send(elicitationId);
            assertFalse(serverRequests.hasPendingElicitation(elicitationId));
        }
    }

    private JsonObject callTool(String type) {
        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject().put("name", "request")
                        .put("arguments", new JsonObject().put("type", type)));
        client.sendAndForget(request);
        return request;
    }

    private JsonObject serverRequest(String type) {
        JsonObject request = client.waitForRequests(1).requests().get(0);
        assertEquals(switch (type) {
            case "sampling" -> "sampling/createMessage";
            case "roots" -> "roots/list";
            default -> "elicitation/create";
        }, request.getString("method"));
        assertTrue(serverRequests.hasResponseHandler(request.getLong("id")));
        if (type.equals("url")) {
            assertEquals("url", request.getJsonObject("params").getString("mode"));
            assertTrue(serverRequests.hasPendingElicitation(request.getJsonObject("params").getString("elicitationId")));
        }
        return request;
    }

    private void respond(JsonObject request, String member, JsonObject value) {
        client.sendAndForget(new JsonObject().put("jsonrpc", "2.0").put("id", request.getValue("id")).put(member, value));
        // Establish that HTTP response processing consumed the handler before checking the waiting Uni.
        await().atMost(Duration.ofSeconds(5)).until(() -> !serverRequests.hasResponseHandler(request.getLong("id")));
    }

    private Throwable failure() {
        // The request uses the default 60-second timeout; this must complete from the client response instead.
        return assertThrows(ExecutionException.class, () -> tools.outcome.get(5, TimeUnit.SECONDS)).getCause();
    }

    private void assertCleanedUp(JsonObject request) {
        assertFalse(serverRequests.hasResponseHandler(request.getLong("id")));
        JsonObject params = request.getJsonObject("params");
        if (params != null && "url".equals(params.getString("mode"))) {
            String elicitationId = params.getString("elicitationId");
            assertFalse(serverRequests.hasPendingElicitation(elicitationId));
            assertThrows(IllegalArgumentException.class, () -> elicitationCompletion.send(elicitationId));
        }
    }

    @Singleton
    public static class MyTools {

        volatile CompletableFuture<String> outcome;

        @Tool
        Uni<String> request(String type, Sampling sampling, Roots roots, Elicitation elicitation) {
            Uni<String> response = switch (type) {
                case "sampling" -> sampling.requestBuilder().setMaxTokens(10)
                        .addMessage(SamplingMessage.withUserRole("Hello")).build().send()
                        .map(r -> r.content().asText().text());
                case "roots" -> roots.list().map(r -> r.get(0).uri());
                case "form" -> elicitation.requestBuilder().setMessage("Name?")
                        .addSchemaProperty("name", new StringSchema(true)).build().send()
                        .map(r -> r.content().getString("name"));
                case "url" -> elicitation.urlRequestBuilder().setMessage("Please authorize")
                        .setUrl("https://example.com/authorize").build().send().map(r -> r.action().name());
                default -> throw new IllegalArgumentException(type);
            };
            CompletableFuture<String> currentOutcome = outcome;
            return response.onItem().invoke(currentOutcome::complete)
                    .onFailure().invoke(currentOutcome::completeExceptionally);
        }
    }
}
