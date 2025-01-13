package io.quarkiverse.mcp.server.test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;

public abstract class McpServerTest {

    private static final Logger LOG = Logger.getLogger(McpServerTest.class);

    @TestHTTPResource
    URI testUri;

    SseClient sseClient;

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
        sseClient = null;
    }

    protected URI initClient() throws URISyntaxException {
        return initClient(null);
    }

    protected JsonObject waitForLastJsonMessage() {
        SseClient.SseEvent event = sseClient.waitForLastEvent();
        if ("message".equals(event.name())) {
            return new JsonObject(event.data());
        }
        throw new IllegalStateException("Message event not received: " + event);
    }

    protected URI initClient(Consumer<JsonObject> initResultAssert) throws URISyntaxException {
        String testUriStr = testUri.toString();
        if (testUriStr.endsWith("/")) {
            testUriStr = testUriStr.substring(0, testUriStr.length() - 1);
        }
        sseClient = new SseClient(URI.create(testUriStr + "/mcp/sse"));
        sseClient.connect();
        var event = sseClient.waitForFirstEvent();
        String messagesUri = testUriStr + event.data().strip();
        URI endpoint = URI.create(messagesUri);

        LOG.infof("Client received endpoint: %s", endpoint);

        JsonObject initMessage = newMessage("initialize")
                .put("params",
                        new JsonObject()
                                .put("clientInfo", new JsonObject()
                                        .put("name", "test-client")
                                        .put("version", "1.0"))
                                .put("protocolVersion", "2024-11-05"));

        given()
                .contentType(ContentType.JSON)
                .when()
                .body(initMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject initResponse = waitForLastJsonMessage();

        JsonObject initResult = assertResponseMessage(initMessage, initResponse);
        assertNotNull(initResult);
        assertEquals("2024-11-05", initResult.getString("protocolVersion"));

        if (initResultAssert != null) {
            initResultAssert.accept(initResult);
        }

        // Send "notifications/initialized"
        given()
                .contentType(ContentType.JSON)
                .when()
                .body(new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("method", "notifications/initialized").encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        return endpoint;
    }

    protected JsonObject assertResponseMessage(JsonObject message, JsonObject response) {
        assertEquals(message.getInteger("id"), response.getInteger("id"));
        assertEquals("2.0", response.getString("jsonrpc"));
        return response.getJsonObject("result");
    }

    protected JsonObject newMessage(String method) {
        if (sseClient == null) {
            throw new IllegalStateException("SSE client not initialized");
        }
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("id", sseClient.nextId());
    }

}
