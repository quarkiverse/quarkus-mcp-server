package io.quarkiverse.mcp.server.test.prompts;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.McpClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PromptsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(McpClient.class, FooService.class, Options.class, MyPrompts.class));

    @Test
    public void testPrompts() throws URISyntaxException {
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
        assertEquals(4, prompts.size());

        assertPromptMessage("Hello Lu!", endpoint, "foo", new JsonObject()
                .put("name", "Lu")
                .put("repeat", 1)
                .put("options", new JsonObject()
                        .put("enabled", true)));
        assertPromptMessage("LU", endpoint, "BAR", new JsonObject()
                .put("val", "Lu"));
        assertPromptMessage("LU", endpoint, "uni_bar", new JsonObject()
                .put("val", "Lu"));
        assertPromptMessage("LU", endpoint, "uni_list_bar", new JsonObject()
                .put("val", "Lu"));
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

}
