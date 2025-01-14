package io.quarkiverse.mcp.server.test.complete;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;

public class InvalidPromptCompleteTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Test
    public void testError() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject completeMessage = newMessage("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/prompt")
                                .put("name", "bar"))
                        .put("argument", new JsonObject()
                                .put("name", "name")
                                .put("value", "Vo")));

        given().contentType(ContentType.JSON)
                .when()
                .body(completeMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject response = waitForLastJsonMessage();
        assertEquals(JsonRPC.INVALID_PARAMS, response.getJsonObject("error").getInteger("code"));
        assertEquals("Prompt completion does not exist: bar_name", response.getJsonObject("error").getString("message"));
    }

}
