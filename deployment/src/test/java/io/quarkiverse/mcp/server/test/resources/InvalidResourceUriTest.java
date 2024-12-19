package io.quarkiverse.mcp.server.test.resources;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;

public class InvalidResourceUriTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(McpClient.class, Checks.class, MyResources.class));

    @Test
    public void testError() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject message = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", "file:///nonexistent"));

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
        assertEquals(JsonRPC.RESOURCE_NOT_FOUND, error.getInteger("code"));
        assertEquals("Invalid resource uri: file:///nonexistent", error.getString("message"));
    }

}
