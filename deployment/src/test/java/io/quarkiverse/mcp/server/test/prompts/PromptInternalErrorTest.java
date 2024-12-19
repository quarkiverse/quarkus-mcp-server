package io.quarkiverse.mcp.server.test.prompts;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class PromptInternalErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(McpClient.class, MyPrompts.class));

    @Test
    public void testError() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject message = newMessage("prompts/get")
                .put("params", new JsonObject()
                        .put("name", "uni_bar")
                        .put("arguments", new JsonObject().put("val", "lav")));

        JsonObject response = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(message.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

        JsonObject error = response.getJsonObject("error");
        assertNotNull(error);
        assertEquals(JsonRPC.INTERNAL_ERROR, error.getInteger("code"));
        assertEquals("Internal error", error.getString("message"));
    }

    public static class MyPrompts {

        @Prompt
        Uni<PromptMessage> uni_bar(String val) {
            return Uni.createFrom().failure(new NullPointerException());
        }

    }

}