package io.quarkiverse.mcp.server.test.close;

import static io.restassured.RestAssured.given;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;

public class CloseTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication();

    @Test
    public void testCloseMessage() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject closeMessage = newMessage("q/close");
        send(closeMessage);

        given()
                .contentType(ContentType.JSON)
                .when()
                .body(closeMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(400);
    }
}
