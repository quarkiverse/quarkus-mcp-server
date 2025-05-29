package io.quarkiverse.mcp.server.test;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class McpServerTest {

    private static final Logger LOG = Logger.getLogger(McpServerTest.class);

    @TestHTTPResource
    protected URI testUri;

    private volatile McpSseClient client;
    protected volatile URI messageEndpoint;

    public static QuarkusUnitTest defaultConfig() {
        return defaultConfig(500);
    }

    public static QuarkusUnitTest defaultConfig(int textLimit) {
        // TODO in theory, we should also add SseClient to all test archives
        // but the test CL can see the class and we don't need Quarkus to analyze this util class
        QuarkusUnitTest config = new QuarkusUnitTest();
        if (System.getProperty("logTraffic") != null) {
            config.overrideConfigKey("quarkus.mcp.server.traffic-logging.enabled", "true");
            config.overrideConfigKey("quarkus.mcp.server.traffic-logging.text-limit", "" + textLimit);
        }
        return config;
    }

    @AfterEach
    void cleanup() {
        client = null;
        messageEndpoint = null;
    }

    protected URI initClient() {
        return initClient(null);
    }

    protected JsonObject waitForLastResponse() {
        return client.waitForLastResponse();
    }

    protected McpSseClient client() {
        return client;
    }

    protected String sseRootPath() {
        return "/mcp";
    }

    protected Map<String, Object> defaultHeaders() {
        return Map.of();
    }

    public ValidatableResponse send(JsonObject data) {
        return send(data.encode());
    }

    public ValidatableResponse send(String data) {
        return sendAndValidate(data, Map.of()).statusCode(200);
    }

    public ValidatableResponse send(JsonObject data, Map<String, Object> additionalHeaders) {
        return send(data.encode(), additionalHeaders);
    }

    public ValidatableResponse send(String data, Map<String, Object> additionalHeaders) {
        return sendAndValidate(data, additionalHeaders);
    }

    public ValidatableResponse sendAndValidate(JsonObject data, Map<String, Object> additionalHeaders) {
        return sendAndValidate(data.encode(), additionalHeaders);
    }

    public ValidatableResponse sendAndValidate(String data, Map<String, Object> additionalHeaders) {
        if (requiresClientInit() && (messageEndpoint == null || client == null)) {
            throw new IllegalStateException("SSE client not initialized");
        }
        Map<String, Object> headers = new HashMap<>(defaultHeaders());
        headers.putAll(additionalHeaders);
        return RestAssured.given()
                .when()
                .headers(headers)
                .body(data)
                .post(messageEndpoint)
                .then();
    }

    public ValidatableResponse sendSecured(JsonObject data, String username, String password) {
        if (requiresClientInit() && (messageEndpoint == null || client == null)) {
            throw new IllegalStateException("SSE client not initialized");
        }
        return RestAssured.given()
                .auth()
                .preemptive()
                .basic(username, password)
                .when()
                .body(data.encode())
                .post(messageEndpoint)
                .then()
                .statusCode(200);
    }

    protected McpSseClient newClient() {
        String testUriStr = testUri.toString();
        if (testUriStr.endsWith("/")) {
            testUriStr = testUriStr.substring(0, testUriStr.length() - 1);
        }
        String sseRootPath = sseRootPath();
        return new McpSseClient(
                URI.create(testUriStr + (sseRootPath.endsWith("/") ? sseRootPath + "sse" : sseRootPath + "/sse")));
    }

    protected Entry<String, String> initBaseAuth() {
        return null;
    }

    protected JsonObject newInitMessage() {
        JsonObject initMessage = newMessage("initialize");
        JsonObject params = new JsonObject()
                .put("clientInfo", new JsonObject()
                        .put("name", "test-client")
                        .put("version", "1.0"))
                .put("protocolVersion", "2024-11-05");
        JsonObject clientCapabilities = getClientCapabilities();
        if (clientCapabilities != null) {
            params.put("capabilities", clientCapabilities);
        }
        initMessage.put("params", params);
        return initMessage;
    }

    protected URI initClient(Consumer<JsonObject> initResultAssert) {
        return initClient(initResultAssert, Map.of());
    }

    protected URI initClient(Consumer<JsonObject> initResultAssert, Map<String, String> headers) {
        String testUriStr = testUri.toString();
        if (testUriStr.endsWith("/")) {
            testUriStr = testUriStr.substring(0, testUriStr.length() - 1);
        }
        client = newClient();
        client.connect();
        var event = client.waitForFirstEvent();
        String messagesUri = testUriStr + event.data().strip();
        URI endpoint = URI.create(messagesUri);
        this.messageEndpoint = endpoint;

        LOG.infof("Client received endpoint: %s", endpoint);

        JsonObject initMessage = newInitMessage();

        Entry<String, String> baseAuth = initBaseAuth();
        if (baseAuth != null) {
            sendSecured(initMessage, baseAuth.getKey(), baseAuth.getValue());
        } else {
            send(initMessage);
        }

        JsonObject initResponse = waitForLastResponse();

        JsonObject initResult = assertResultResponse(initMessage, initResponse);
        assertNotNull(initResult);
        assertEquals("2024-11-05", initResult.getString("protocolVersion"));

        if (initResultAssert != null) {
            initResultAssert.accept(initResult);
        }

        // Send "notifications/initialized"
        JsonObject nofitication = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/initialized");
        if (baseAuth != null) {
            sendSecured(nofitication, baseAuth.getKey(), baseAuth.getValue());
        } else {
            send(nofitication);
        }
        return endpoint;
    }

    protected void assertTool(JsonArray tools, String name, String description, Consumer<JsonObject> inputSchemaAsserter) {
        assertTool(tools, name, description, inputSchemaAsserter, null);
    }

    protected void assertTool(JsonArray tools, String name, String description, Consumer<JsonObject> inputSchemaAsserter,
            Consumer<JsonObject> annotationsAsserter) {
        JsonObject tool = null;
        for (int i = 0; i < tools.size(); i++) {
            JsonObject t = tools.getJsonObject(i);
            if (name.equals(t.getString("name"))) {
                tool = t;
            }
        }
        if (tool == null) {
            fail("Tool not found: " + name);
        }
        if (description != null) {
            assertEquals(description, tool.getString("description"));
        }
        if (inputSchemaAsserter != null) {
            inputSchemaAsserter.accept(tool.getJsonObject("inputSchema"));
        }
        if (annotationsAsserter != null) {
            annotationsAsserter.accept(tool.getJsonObject("annotations"));
        }
    }

    protected JsonObject assertResultResponse(JsonObject request, JsonObject response) {
        assertEquals(request.getInteger("id"), response.getInteger("id"));
        assertEquals("2.0", response.getString("jsonrpc"));
        JsonObject result = response.getJsonObject("result");
        assertNotNull(result);
        return result;
    }

    protected JsonObject assertErrorResponse(JsonObject request, JsonObject response) {
        assertEquals(request.getInteger("id"), response.getInteger("id"));
        assertEquals("2.0", response.getString("jsonrpc"));
        JsonObject error = response.getJsonObject("error");
        assertNotNull(error);
        return error;
    }

    protected JsonObject newMessage(String method) {
        if (requiresClientInit() && client == null) {
            throw clientNotInitialized();
        }
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("id", nextRequestId());
    }

    protected JsonObject newNotification(String method) {
        if (requiresClientInit() && client == null) {
            throw clientNotInitialized();
        }
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", method);
    }

    protected JsonObject newResult(Object requestId, JsonObject result) {
        if (requiresClientInit() && client == null) {
            throw clientNotInitialized();
        }
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", result)
                .put("id", requestId);
    }

    protected JsonObject getClientCapabilities() {
        return null;
    }

    private IllegalStateException clientNotInitialized() {
        return new IllegalStateException("SSE client not initialized");
    }

    protected boolean requiresClientInit() {
        return true;
    }

    protected int nextRequestId() {
        return client.nextRequestId();
    }
}
