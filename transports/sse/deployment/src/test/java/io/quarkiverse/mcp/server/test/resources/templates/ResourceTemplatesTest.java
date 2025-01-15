package io.quarkiverse.mcp.server.test.resources.templates;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResourceTemplatesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTemplates.class, Checks.class));

    @Test
    public void testResourceTemplates() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject resourceTemplatesListMessage = newMessage("resources/templates/list");

        given().contentType(ContentType.JSON)
                .when()
                .body(resourceTemplatesListMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject resourceTemplatesListResponse = waitForLastJsonMessage();

        JsonObject resourceTemplatesListResult = assertResponseMessage(resourceTemplatesListMessage,
                resourceTemplatesListResponse);
        assertNotNull(resourceTemplatesListResult);
        JsonArray resourceTemplates = resourceTemplatesListResult.getJsonArray("resourceTemplates");
        assertEquals(2, resourceTemplates.size());

        assertResourceRead("foo:bar", "file:///bar", endpoint);
        assertResourceRead("bar:baz", "file:///bar/baz", endpoint);
    }

    private void assertResourceRead(String expectedText, String uri, URI endpoint) {
        JsonObject resourceReadMessage = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));

        given().contentType(ContentType.JSON)
                .when()
                .body(resourceReadMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject resourceReadResponse = waitForLastJsonMessage();

        JsonObject resourceReadResult = assertResponseMessage(resourceReadMessage, resourceReadResponse);
        assertNotNull(resourceReadResult);
        JsonArray contents = resourceReadResult.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject textContent = contents.getJsonObject(0);
        assertEquals(expectedText, textContent.getString("text"));
        assertEquals(uri, textContent.getString("uri"));
    }

}
