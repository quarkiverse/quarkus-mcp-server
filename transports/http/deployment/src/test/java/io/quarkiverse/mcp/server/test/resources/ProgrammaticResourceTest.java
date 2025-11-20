package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Content.Annotations;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceManager.ResourceArguments;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceInfo;
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
                    assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, e.code());
                    assertEquals("Resource not found: file:///alpha", e.message());
                })
                .send()
                .thenAssertResults();

        myResources.register("alpha", "2");
        assertThrows(IllegalArgumentException.class, () -> myResources.register("alpha", "2"));
        assertThrows(NullPointerException.class, () -> myResources.register(null, "2"));

        List<JsonObject> notifications = client.waitForNotifications(1).notifications();
        assertEquals("notifications/resources/list_changed", notifications.get(0).getString("method"));

        client.when()
                .resourcesList(p -> {
                    assertEquals(1, p.size());
                    ResourceInfo alpha = p.findByUri("file:///alpha");
                    assertNotNull(alpha);
                    assertEquals(1, alpha.size());
                    assertNotNull(alpha.annotations());
                    assertEquals(Role.ASSISTANT, alpha.annotations().audience());
                    assertEquals(0.9, alpha.annotations().priority());
                })
                .resourcesRead("file:///alpha", r -> assertEquals("2", r.contents().get(0).asText().text()))
                .thenAssertResults();

        ResourceArguments lastArgs = MyResources.lastArgs.get();
        assertNotNull(lastArgs.connection());
        assertNotNull(lastArgs.progress());
        assertNotNull(lastArgs.requestId());
        assertNotNull(lastArgs.log());
        assertNotNull(lastArgs.roots());
        assertNotNull(lastArgs.sampling());
        assertNotNull(lastArgs.cancellation());

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
                    assertEquals(JsonRpcErrorCodes.RESOURCE_NOT_FOUND, e.code());
                    assertEquals("Resource not found: file:///alpha", e.message());
                })
                .send()
                .resourcesRead("file:///bravo", r -> assertEquals("3", r.contents().get(0).asText().text()))
                .thenAssertResults();

        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> myResources.registerFailure());
        assertEquals("A resource with name [dummy] already exits", iae.getMessage());
    }

    @Singleton
    public static class MyResources {

        static final AtomicReference<ResourceArguments> lastArgs = new AtomicReference<>();

        @Inject
        ResourceManager manager;

        void register(String name, String result) {
            manager.newResource(name)
                    .setUri("file:///" + name)
                    .setDescription(name + " description!")
                    .setAnnotations(new Annotations(Role.ASSISTANT, null, .9))
                    .setSize(1)
                    .setHandler(
                            resourceArgs -> {
                                lastArgs.set(resourceArgs);
                                return new ResourceResponse(
                                        List.of(TextResourceContents.create(resourceArgs.requestUri().value(), result)));
                            })
                    .register();
        }

        void registerFailure() {
            String name = "dummy";
            // Verification must be successful
            ResourceManager.ResourceDefinition rd = manager.newResource(name)
                    .setDescription(name + " description!")
                    .setUri(name)
                    .setHandler(
                            resourceArgs -> {
                                return new ResourceResponse(
                                        List.of(TextResourceContents.create(resourceArgs.requestUri().value(), name)));
                            });
            // Add a resource with the same name but different URI
            manager.newResource(name)
                    .setUri("file:///" + name)
                    .setDescription(name + " description!")
                    .setHandler(
                            resourceArgs -> {
                                return new ResourceResponse(
                                        List.of(TextResourceContents.create(resourceArgs.requestUri().value(), name)));
                            })
                    .register();
            // Verification must fail
            rd.register();
        }

        ResourceManager.ResourceInfo remove(String name) {
            return manager.removeResource("file:///" + name);
        }

    }

}
