package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ProgrammaticResourceTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class));

    @Inject
    MyResources myResources;

    @Test
    public void testResources() {
        initClient();
        assertResources(0);
        assertResourceReadResponseError("file:///alpha");

        myResources.register("alpha", "2");
        assertThrows(IllegalArgumentException.class, () -> myResources.register("alpha", "2"));
        assertThrows(NullPointerException.class, () -> myResources.register(null, "2"));

        List<JsonObject> notifications = client().waitForNotifications(1);
        assertEquals("notifications/resources/list_changed", notifications.get(0).getString("method"));

        assertResources(1);
        assertResourceReadResponse("file:///alpha", "2");

        myResources.register("bravo", "3");

        assertResources(2);
        assertEquals("notifications/resources/list_changed", client().waitForNotifications(1).get(0).getString("method"));
        assertResourceReadResponse("file:///bravo", "3");

        myResources.remove("alpha");
        assertResources(1);
        assertResourceReadResponseError("file:///alpha");
        assertResourceReadResponse("file:///bravo", "3");
    }

    private void assertResources(int expectedSize) {
        JsonObject resourcesListMessage = newMessage("resources/list");
        send(resourcesListMessage);

        JsonObject resourcesListResponse = waitForLastResponse();

        JsonObject resourcesListResult = assertResponseMessage(resourcesListMessage, resourcesListResponse);
        assertNotNull(resourcesListResult);
        JsonArray resources = resourcesListResult.getJsonArray("resources");
        assertEquals(expectedSize, resources.size());
    }

    private void assertResourceReadResponseError(String uri) {
        JsonObject message = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));
        send(message);
        JsonObject response = waitForLastResponse();
        assertEquals(JsonRPC.RESOURCE_NOT_FOUND, response.getJsonObject("error").getInteger("code"));
        assertEquals("Invalid resource uri: " + uri, response.getJsonObject("error").getString("message"));

    }

    private void assertResourceReadResponse(String uri, String expectedText) {
        JsonObject message = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));
        send(message);
        JsonObject resourceResponse = waitForLastResponse();
        JsonObject resourceResult = assertResponseMessage(message, resourceResponse);
        assertNotNull(resourceResult);
        JsonArray contents = resourceResult.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject textContent = contents.getJsonObject(0);
        assertEquals(uri, textContent.getString("uri"));
        assertEquals(expectedText, textContent.getString("text"));
    }

    @Singleton
    public static class MyResources {

        @Inject
        ResourceManager manager;

        void register(String name, String result) {
            manager.newResource(name)
                    .setUri("file:///" + name)
                    .setDescription(name + " description!")
                    .setHandler(
                            args -> new ResourceResponse(
                                    List.of(TextResourceContents.create(args.requestUri().value(), result))))
                    .register();
        }

        ResourceManager.ResourceInfo remove(String name) {
            return manager.removeResource("file:///" + name);
        }

    }

}
