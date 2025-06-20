package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
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
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .resourcesList(p -> assertEquals(0, p.size()))
                .resourcesRead("file:///alpha")
                .withErrorAssert(e -> {
                    assertEquals(JsonRPC.RESOURCE_NOT_FOUND, e.code());
                    assertEquals("Invalid resource uri: file:///alpha", e.message());
                })
                .send()
                .thenAssertResults();

        myResources.register("alpha", "2");
        assertThrows(IllegalArgumentException.class, () -> myResources.register("alpha", "2"));
        assertThrows(NullPointerException.class, () -> myResources.register(null, "2"));

        List<JsonObject> notifications = client.waitForNotifications(1).notifications();
        assertEquals("notifications/resources/list_changed", notifications.get(0).getString("method"));

        client.when()
                .resourcesList(p -> assertEquals(1, p.size()))
                .resourcesRead("file:///alpha", r -> assertEquals("2", r.contents().get(0).asText().text()))
                .thenAssertResults();

        myResources.register("bravo", "3");

        client.when()
                .resourcesList(p -> assertEquals(2, p.size()))
                .resourcesRead("file:///bravo", r -> assertEquals("3", r.contents().get(0).asText().text()))
                .thenAssertResults();

        assertEquals("notifications/resources/list_changed",
                client.waitForNotifications(2).notifications().get(1).getString("method"));

        myResources.remove("alpha");

        client.when()
                .resourcesList(p -> assertEquals(1, p.size()))
                .resourcesRead("file:///alpha")
                .withErrorAssert(e -> {
                    assertEquals(JsonRPC.RESOURCE_NOT_FOUND, e.code());
                    assertEquals("Invalid resource uri: file:///alpha", e.message());
                })
                .send()
                .resourcesRead("file:///bravo", r -> assertEquals("3", r.contents().get(0).asText().text()))
                .thenAssertResults();
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
