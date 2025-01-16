package io.quarkiverse.mcp.server.test.resources;

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

public class ResourcesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class, Checks.class));

    @Test
    public void testResources() throws URISyntaxException {
        URI endpoint = initClient();

        JsonObject resourcesListMessage = newMessage("resources/list");

        given().contentType(ContentType.JSON)
                .when()
                .body(resourcesListMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject resourcesListResponse = waitForLastResponse();

        JsonObject resourcesListResult = assertResponseMessage(resourcesListMessage, resourcesListResponse);
        assertNotNull(resourcesListResult);
        JsonArray resources = resourcesListResult.getJsonArray("resources");
        assertEquals(4, resources.size());

        // alpha, bravo, uni_alpha, uni_bravo
        assertResource(resources.getJsonObject(0), "alpha", null, "file:///project/alpha", null);
        assertResource(resources.getJsonObject(1), "bravo", null, "file:///project/bravo", null);
        assertResource(resources.getJsonObject(2), "uni_alpha", null, "file:///project/uni_alpha", null);
        assertResource(resources.getJsonObject(3), "uni_bravo", null, "file:///project/uni_bravo", null);

        assertResourceRead("1", "file:///project/alpha", endpoint, "file:///project/alpha");
        assertResourceRead("2", "file:///project/uni_alpha", endpoint, "file:///project/uni_alpha");
        assertResourceRead("3", "file:///project/bravo", endpoint, "file:///project/bravo");
        assertResourceRead("4", "file:///foo", endpoint, "file:///project/uni_bravo");
    }

    private void assertResource(JsonObject resource, String name, String description, String uri, String mimeType) {
        assertEquals(name, resource.getString("name"));
        if (description != null) {
            assertEquals(description, resource.getString("description"));
        }
        assertEquals(uri, resource.getString("uri"));
        if (mimeType != null) {
            assertEquals(description, resource.getString("mimeType"));
        }
    }

    private void assertResourceRead(String expectedText, String expectedUri, URI endpoint, String uri) {
        JsonObject resourceReadMessage = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));

        given().contentType(ContentType.JSON)
                .when()
                .body(resourceReadMessage.encode())
                .post(endpoint)
                .then()
                .statusCode(200);

        JsonObject resourceReadResponse = waitForLastResponse();

        JsonObject resourceReadResult = assertResponseMessage(resourceReadMessage, resourceReadResponse);
        assertNotNull(resourceReadResult);
        JsonArray contents = resourceReadResult.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject textContent = contents.getJsonObject(0);
        assertEquals(expectedText, textContent.getString("text"));
        assertEquals(expectedUri, textContent.getString("uri"));
    }

}
