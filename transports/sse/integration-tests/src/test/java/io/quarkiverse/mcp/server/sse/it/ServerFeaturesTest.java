package io.quarkiverse.mcp.server.sse.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusTest
public class ServerFeaturesTest {

    @TestHTTPResource
    URI testUri;

    AtomicInteger idGenerator = new AtomicInteger();

    @Test
    public void testPrompt() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject promptListMessage = newMessage("prompts/list");

        JsonObject promptListResponse = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(promptListMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

        JsonObject promptListResult = assertResponseMessage(promptListMessage, promptListResponse);
        assertNotNull(promptListResult);
        JsonArray prompts = promptListResult.getJsonArray("prompts");
        assertEquals(1, prompts.size());

        assertPrompt(prompts.getJsonObject(0), "code_assist", null, args -> {
            assertEquals(1, args.size());
            JsonObject arg1 = args.getJsonObject(0);
            assertEquals("lang", arg1.getString("name"));
            assertEquals(true, arg1.getBoolean("required"));
        });
        assertPromptMessage("System.out.println(\"Hello world!\");", endpoint, "code_assist", new JsonObject()
                .put("lang", "java"));
    }

    @Test
    public void testTool() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject toolListMessage = newMessage("tools/list");

        JsonObject toolListResponse = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(toolListMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

        JsonObject toolListResult = assertResponseMessage(toolListMessage, toolListResponse);
        assertNotNull(toolListResult);
        JsonArray tools = toolListResult.getJsonArray("tools");
        assertEquals(1, tools.size());

        assertTool(tools.getJsonObject(0), "toLowerCase", null, schema -> {
            JsonObject properties = schema.getJsonObject("properties");
            assertEquals(1, properties.size());
            JsonObject valueProperty = properties.getJsonObject("value");
            assertNotNull(valueProperty);
            assertEquals("string", valueProperty.getString("type"));
        });

        assertToolCall(
                "loop", endpoint, "toLowerCase", new JsonObject()
                        .put("value", "LooP"));
    }

    @Test
    public void testResource() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject resourceListMessage = newMessage("resources/list");

        JsonObject resourceListResponse = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(resourceListMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

        JsonObject resourceListResult = assertResponseMessage(resourceListMessage, resourceListResponse);
        assertNotNull(resourceListResult);
        JsonArray resources = resourceListResult.getJsonArray("resources");
        assertEquals(1, resources.size());

        assertResource(resources.getJsonObject(0), "alpha", null, "file:///project/alpha", null);

        assertResourceRead(Base64.getMimeEncoder().encodeToString("data".getBytes()), "file:///project/alpha", endpoint,
                "file:///project/alpha");
    }

    protected URI initClient() throws URISyntaxException {
        URI endpoint = new URI(given().baseUri(testUri.toString())
                .contentType(ContentType.TEXT)
                .when()
                .get("test-init-mcp-client")
                .then()
                .statusCode(200)
                .extract().body().asString());

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

    private void assertPrompt(JsonObject prompt, String name, String description, Consumer<JsonArray> argumentsAsserter) {
        assertEquals(name, prompt.getString("name"));
        if (description != null) {
            assertEquals(description, prompt.getString("description"));
        }
        if (argumentsAsserter != null) {
            argumentsAsserter.accept(prompt.getJsonArray("arguments"));
        }
    }

    private void assertPromptMessage(String expectedText, URI endpoint, String name, JsonObject arguments) {
        JsonObject promptGetMessage = newMessage("prompts/get")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", arguments));

        JsonObject promptGetResponse = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(promptGetMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

        JsonObject promptGetResult = assertResponseMessage(promptGetMessage, promptGetResponse);
        assertNotNull(promptGetResult);
        JsonArray messages = promptGetResult.getJsonArray("messages");
        assertEquals(1, messages.size());
        JsonObject message = messages.getJsonObject(0);
        assertEquals("user", message.getString("role"));
        JsonObject content = message.getJsonObject("content");
        assertEquals("text", content.getString("type"));
        assertEquals(expectedText, content.getString("text"));
    }

    private void assertTool(JsonObject tool, String name, String description, Consumer<JsonObject> inputSchemaAsserter) {
        assertEquals(name, tool.getString("name"));
        if (description != null) {
            assertEquals(description, tool.getString("description"));
        }
        if (inputSchemaAsserter != null) {
            inputSchemaAsserter.accept(tool.getJsonObject("inputSchema"));
        }
    }

    private void assertToolCall(String expectedText, URI endpoint, String name, JsonObject arguments) {
        JsonObject toolGetMessage = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", arguments));

        JsonObject toolGetResponse = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(toolGetMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

        JsonObject toolGetResult = assertResponseMessage(toolGetMessage, toolGetResponse);
        assertNotNull(toolGetResult);
        JsonArray content = toolGetResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText, textContent.getString("text"));
    }

    private void assertResource(JsonObject resource, String name, String description, String uri, String mimeType) {
        assertEquals(name, resource.getString("name"));
        if (description != null) {
            assertEquals(description, resource.getString("description"));
        }
        assertEquals(uri, resource.getString("uri"));
        if (mimeType != null) {
            assertEquals(description, resource.getString("mimeType"));
        }
    }

    private void assertResourceRead(String expectedBlob, String expectedUri, URI endpoint, String uri) {
        JsonObject resourceReadMessage = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));

        JsonObject resourceReadResponse = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(resourceReadMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

        JsonObject resourceReadResult = assertResponseMessage(resourceReadMessage, resourceReadResponse);
        assertNotNull(resourceReadResult);
        JsonArray contents = resourceReadResult.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject blobContent = contents.getJsonObject(0);
        assertEquals(expectedBlob, blobContent.getString("blob"));
        assertEquals(expectedUri, blobContent.getString("uri"));
    }
}
