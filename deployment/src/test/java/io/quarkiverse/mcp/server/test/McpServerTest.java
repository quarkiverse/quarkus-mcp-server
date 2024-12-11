package io.quarkiverse.mcp.server.test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.jboss.resteasy.reactive.client.SseEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(McpClient.class, FooService.class, MyPrompts.class));

    @TestHTTPResource
    URI testUri;

    @Test
    public void testInit() throws URISyntaxException {
        McpClient mcpClient = QuarkusRestClientBuilder.newBuilder()
                .baseUri(testUri)
                .build(McpClient.class);

        List<SseEvent<String>> sseMessages = new CopyOnWriteArrayList<>();
        mcpClient.init().subscribe().with(s -> sseMessages.add(s), e -> {
        });
        Awaitility.await().until(() -> !sseMessages.isEmpty());
        URI endpoint = new URI(sseMessages.get(0).data());

        JsonObject initMessage = newMessage("initialize")
                .put("params",
                        new JsonObject()
                                .put("clientInfo", new JsonObject()
                                        .put("name", "FooClient")
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

        assertEquals(initMessage.getInteger("id"), initResponse.getInteger("id"));
        assertEquals("2.0", initResponse.getString("jsonrpc"));
        JsonObject initResult = initResponse.getJsonObject("result");
        assertNotNull(initResult);
        assertEquals("2024-11-05", initResult.getString("protocolVersion"));

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

        JsonObject promptListMessage = newMessage("prompts/list");

        JsonObject promptListResponse = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(promptListMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

        assertEquals(promptListMessage.getInteger("id"), promptListResponse.getInteger("id"));
        assertEquals("2.0", promptListResponse.getString("jsonrpc"));
        JsonObject promptListResult = promptListResponse.getJsonObject("result");
        assertNotNull(promptListResult);
        JsonArray prompts = promptListResult.getJsonArray("prompts");
        assertEquals(2, prompts.size());

        JsonObject promptGetMessage = newMessage("prompts/get")
                .put("params", new JsonObject()
                        .put("name", "foo")
                        .put("arguments", new JsonObject()
                                .put("name", "Lu")
                                .put("repeat", 1)
                                .put("options", new JsonObject()
                                        .put("enabled", true))));

        JsonObject promptGetResponse = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(promptGetMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

        assertEquals(promptGetMessage.getInteger("id"), promptGetResponse.getInteger("id"));
        assertEquals("2.0", promptGetResponse.getString("jsonrpc"));
        JsonObject promptGetResult = promptGetResponse.getJsonObject("result");
        assertNotNull(promptGetResult);
        JsonArray messages = promptGetResult.getJsonArray("messages");
        assertEquals(1, messages.size());
        JsonObject message = messages.getJsonObject(0);
        assertEquals("user", message.getString("role"));
        JsonObject content = message.getJsonObject("content");
        assertEquals("text", content.getString("type"));
        assertEquals("Hello Lu!", content.getString("text"));
    }

    private static AtomicInteger ID = new AtomicInteger();

    private JsonObject newMessage(String method) {
        return new JsonObject().put("jsonrpc", "2.0").put("method", method).put("id", ID.incrementAndGet());
    }

}
