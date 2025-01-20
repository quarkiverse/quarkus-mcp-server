package io.quarkiverse.mcp.server.test.ping;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;

public class AutoPingIntervalTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.auto-ping-interval", "1s");

    @Test
    public void testPing() throws URISyntaxException {
        URI endpoint = initClient();

        client().setRequestConsumer(r -> {
            JsonObject pongMessage = Messages.newResult(r.getValue("id"), new JsonObject());
            given().contentType(ContentType.JSON)
                    .when()
                    .body(pongMessage.encode())
                    .post(endpoint)
                    .then()
                    .statusCode(200);
        });

        List<JsonObject> requests = client().waitForRequests(2);
        JsonObject pingRequest = requests.stream().filter(r -> "ping".equals(r.getString("method"))).findFirst().orElse(null);
        assertNotNull(pingRequest);
    }
}
