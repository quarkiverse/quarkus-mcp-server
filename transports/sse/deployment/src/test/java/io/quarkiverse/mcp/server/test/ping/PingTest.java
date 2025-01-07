package io.quarkiverse.mcp.server.test.ping;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;

public class PingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(McpClient.class));

    @Test
    public void testPing() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject pingMessage = newMessage("ping");

        JsonObject pingResponse = new JsonObject(given()
                .contentType(ContentType.JSON)
                .when()
                .body(pingMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200)
                .extract().body().asString());

        JsonObject pingResult = assertResponseMessage(pingMessage, pingResponse);
        assertNotNull(pingResult);
        assertTrue(pingResult.isEmpty());
    }
}
