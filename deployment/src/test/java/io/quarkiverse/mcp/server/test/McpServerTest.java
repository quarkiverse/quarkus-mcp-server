package io.quarkiverse.mcp.server.test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.awaitility.Awaitility;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.SseEvent;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;

public abstract class McpServerTest {

    private static final Logger LOG = Logger.getLogger(McpServerTest.class);

    @TestHTTPResource
    URI testUri;

    public static QuarkusUnitTest defaultConfig() {
        QuarkusUnitTest config = new QuarkusUnitTest();
        if (System.getProperty("logTraffic") != null) {
            config.overrideConfigKey("quarkus.mcp.server.traffic-logging.enabled", "true");
            config.overrideConfigKey("quarkus.log.category.\"io.quarkus.mcp.server.traffic\".level", "DEBUG");
        }
        return config;
    }

    protected List<SseEvent<String>> sseMessages;

    AtomicInteger idGenerator = new AtomicInteger();

    protected URI initClient() throws URISyntaxException {
        return initClient(null);
    }

    protected URI initClient(Consumer<JsonObject> initResultAssert) throws URISyntaxException {
        McpClient mcpClient = QuarkusRestClientBuilder.newBuilder()
                .baseUri(testUri)
                .build(McpClient.class);

        sseMessages = new CopyOnWriteArrayList<>();
        mcpClient.init().subscribe().with(s -> sseMessages.add(s), e -> {
        });
        Awaitility.await().until(() -> !sseMessages.isEmpty());
        URI endpoint = new URI(sseMessages.get(0).data());
        LOG.infof("Client received endpoint: %s", endpoint);

        JsonObject initMessage = newMessage("initialize")
                .put("params",
                        new JsonObject()
                                .put("clientInfo", new JsonObject()
                                        .put("name", "test-client")
                                        .put("version", "1.0"))
                                .put("protocolVersion", "2024-11-05"));

        JsonObject initResponse = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(initMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

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
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("id", idGenerator.incrementAndGet());
    }

}
