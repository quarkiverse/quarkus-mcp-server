package io.quarkiverse.mcp.server.test.tools;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.McpClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;

public class InvalidToolNameTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(McpClient.class, FooService.class, Options.class, Checks.class, MyTools.class));

    @Test
    public void testError() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "nonexistent")
                        .put("arguments", new JsonObject()));

        given()
                .contentType(ContentType.JSON)
                .when()
                .body(message.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .body("error.code", equalTo(JsonRPC.INVALID_PARAMS), "error.message",
                        equalTo("Invalid tool name: nonexistent"));
    }

}
