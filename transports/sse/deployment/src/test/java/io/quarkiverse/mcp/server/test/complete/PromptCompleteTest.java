package io.quarkiverse.mcp.server.test.complete;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PromptCompleteTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Test
    public void testCompletion() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject completeMessage = newMessage("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/prompt")
                                .put("name", "foo"))
                        .put("argument", new JsonObject()
                                .put("name", "name")
                                .put("value", "Vo")));

        given().contentType(ContentType.JSON)
                .when()
                .body(completeMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject completeResponse = waitForLastJsonMessage();

        JsonObject completeResult = assertResponseMessage(completeMessage, completeResponse);
        assertNotNull(completeResult);
        JsonArray values = completeResult.getJsonObject("completion").getJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("Vojtik", values.getString(0));

        completeMessage = newMessage("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/prompt")
                                .put("name", "foo"))
                        .put("argument", new JsonObject()
                                .put("name", "suffix")
                                .put("value", "Vo")));

        given().contentType(ContentType.JSON)
                .when()
                .body(completeMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        completeResponse = waitForLastJsonMessage();

        completeResult = assertResponseMessage(completeMessage, completeResponse);
        assertNotNull(completeResult);
        values = completeResult.getJsonObject("completion").getJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("_foo", values.getString(0));
    }
}
