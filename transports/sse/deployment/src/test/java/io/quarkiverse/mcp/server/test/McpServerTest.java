package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

public abstract class McpServerTest {

    private static final Logger LOG = Logger.getLogger(McpServerTest.class);

    @TestHTTPResource
    URI testUri;

    private volatile McpSseClient client;
    private volatile URI messageEndpoint;

    public static QuarkusUnitTest defaultConfig() {
        // TODO in theory, we should also add SseClient to all test archives
        // but the test CL can see the class and we don't need Quarkus to analyze this util class
        QuarkusUnitTest config = new QuarkusUnitTest();
        if (System.getProperty("logTraffic") != null) {
            config.overrideConfigKey("quarkus.mcp.server.traffic-logging.enabled", "true");
        }
        return config;
    }

    @AfterEach
    void cleanup() {
        client = null;
        messageEndpoint = null;
    }

    protected URI initClient() throws URISyntaxException {
        return initClient(null);
    }

    protected JsonObject waitForLastResponse() {
        return client.waitForLastResponse();
    }

    protected McpSseClient client() {
        return client;
    }

    public void send(JsonObject data) {
        if (messageEndpoint == null || client == null) {
            throw new IllegalStateException("SSE client not initialized");
        }
        RestAssured.given()
                .when()
                .body(data.encode())
                .post(messageEndpoint)
                .then()
                .statusCode(200);
    }

    protected URI initClient(Consumer<JsonObject> initResultAssert) throws URISyntaxException {
        String testUriStr = testUri.toString();
        if (testUriStr.endsWith("/")) {
            testUriStr = testUriStr.substring(0, testUriStr.length() - 1);
        }
        client = new McpSseClient(URI.create(testUriStr + "/mcp/sse"));
        client.connect();
        var event = client.waitForFirstEvent();
        String messagesUri = testUriStr + event.data().strip();
        URI endpoint = URI.create(messagesUri);
        this.messageEndpoint = endpoint;

        LOG.infof("Client received endpoint: %s", endpoint);

        JsonObject initMessage = newMessage("initialize")
                .put("params",
                        new JsonObject()
                                .put("clientInfo", new JsonObject()
                                        .put("name", "test-client")
                                        .put("version", "1.0"))
                                .put("protocolVersion", "2024-11-05"));

        send(initMessage);

        JsonObject initResponse = waitForLastResponse();

        JsonObject initResult = assertResponseMessage(initMessage, initResponse);
        assertNotNull(initResult);
        assertEquals("2024-11-05", initResult.getString("protocolVersion"));

        if (initResultAssert != null) {
            initResultAssert.accept(initResult);
        }

        // Send "notifications/initialized"
        send(new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/initialized"));

        return endpoint;
    }

    protected JsonObject assertResponseMessage(JsonObject message, JsonObject response) {
        assertEquals(message.getInteger("id"), response.getInteger("id"));
        assertEquals("2.0", response.getString("jsonrpc"));
        return response.getJsonObject("result");
    }

    protected JsonObject newMessage(String method) {
        if (client == null) {
            throw new IllegalStateException("SSE client not initialized");
        }
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("id", client.nextRequestId());
    }

}
