package io.quarkiverse.mcp.server.sse.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.Base64;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.quarkiverse.mcp.server.test.McpSseClient;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerFeaturesTest {

    @TestHTTPResource
    URI testUri;

    McpSseClient client;

    URI endpoint;

    @BeforeAll
    void beforeEach() {
        client = createMcpSseClient(testUri);
        endpoint = initClient(testUri);
    }

    @Test
    void testPrompt() {

        JsonObject promptListMessage = newMessage("prompts/list");

        given().contentType(ContentType.JSON)
                .when()
                .body(promptListMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject promptListResponse = client.waitForLastResponse();

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
    void testTool() {

        JsonObject toolListMessage = newMessage("tools/list");

        given().contentType(ContentType.JSON)
                .when()
                .body(toolListMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject toolListResponse = client.waitForLastResponse();

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
    void testResource() {

        JsonObject resourceListMessage = newMessage("resources/list");

        given().contentType(ContentType.JSON)
                .when()
                .body(resourceListMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject resourceListResponse = client.waitForLastResponse();

        JsonObject resourceListResult = assertResponseMessage(resourceListMessage, resourceListResponse);
        assertNotNull(resourceListResult);
        JsonArray resources = resourceListResult.getJsonArray("resources");
        assertEquals(1, resources.size());

        assertResource(resources.getJsonObject(0), "alpha", null, "file:///project/alpha", null);

        assertResourceRead(Base64.getMimeEncoder().encodeToString("data".getBytes()), "file:///project/alpha", endpoint,
                "file:///project/alpha");
    }

    protected static McpSseClient createMcpSseClient(URI testUri) {
        String testUriStr = testUri.toString();
        if (testUriStr.endsWith("/")) {
            testUriStr = testUriStr.substring(0, testUriStr.length() - 1);
        }
        return new McpSseClient(URI.create(testUriStr + "/mcp/sse"));
    }

    protected URI initClient(URI testUri) {
        String testUriStr = testUri.toString();
        if (testUriStr.endsWith("/")) {
            testUriStr = testUriStr.substring(0, testUriStr.length() - 1);
        }
        client.connect();
        var event = client.waitForFirstEvent();
        String messagesUri = testUriStr + event.data().strip();
        final var endpointUri = URI.create(messagesUri);

        JsonObject initMessage = newMessage("initialize")
                .put("params",
                        new JsonObject()
                                .put("clientInfo", new JsonObject()
                                        .put("name", "test-client")
                                        .put("version", "1.0"))
                                .put("protocolVersion", "2024-11-05"));

        given().contentType(ContentType.JSON)
                .when()
                .body(initMessage.encode())
                .post(endpointUri)
                .then()
                .statusCode(200);

        JsonObject initResponse = client.waitForLastResponse();

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
                .post(endpointUri)
                .then()
                .statusCode(200);

        return endpointUri;
    }

    protected JsonObject assertResponseMessage(JsonObject message, JsonObject response) {
        assertEquals(message.getInteger("id"), response.getInteger("id"));
        assertEquals("2.0", response.getString("jsonrpc"));
        return response.getJsonObject("result");
    }

    protected JsonObject newMessage(String method) {
        if (client == null) {
            throw new IllegalStateException();
        }
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("id", client.nextRequestId());
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

        given().contentType(ContentType.JSON)
                .when()
                .body(promptGetMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject promptGetResponse = client.waitForLastResponse();

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

        given().contentType(ContentType.JSON)
                .when()
                .body(toolGetMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject toolGetResponse = client.waitForLastResponse();

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

        given().contentType(ContentType.JSON)
                .when()
                .body(resourceReadMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject resourceReadResponse = client.waitForLastResponse();

        JsonObject resourceReadResult = assertResponseMessage(resourceReadMessage, resourceReadResponse);
        assertNotNull(resourceReadResult);
        JsonArray contents = resourceReadResult.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject blobContent = contents.getJsonObject(0);
        assertEquals(expectedBlob, blobContent.getString("blob"));
        assertEquals(expectedUri, blobContent.getString("uri"));
    }
}
