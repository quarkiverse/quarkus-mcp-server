package io.quarkiverse.mcp.server.test.resources;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;

public class ResourceInternalErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class));

    @Test
    public void testError() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject message = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", "file:///project/alpha"));

        given()
                .contentType(ContentType.JSON)
                .when()
                .body(message.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject response = waitForLastJsonMessage();
        assertEquals(JsonRPC.INTERNAL_ERROR, response.getJsonObject("error").getInteger("code"));
        assertEquals("Internal error", response.getJsonObject("error").getString("message"));
    }

    public static class MyResources {

        @Resource(uri = "file:///project/alpha")
        ResourceResponse alpha(String uri) {
            throw new NullPointerException();
        }

    }

}
