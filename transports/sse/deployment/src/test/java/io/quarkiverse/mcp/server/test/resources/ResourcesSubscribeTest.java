package io.quarkiverse.mcp.server.test.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ResourcesSubscribeTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyResources.class));

    @Inject
    ResourceManager manager;

    @Test
    public void testResources() {
        String uri1 = "file:///file1.txt";
        String uri2 = "file:///file2.txt";

        manager.newResource("file2")
                .setUri(uri2)
                .setDescription("File 2 description")
                .setHandler(
                        args -> new ResourceResponse(
                                List.of(TextResourceContents.create(args.requestUri().value(), "File 2"))))
                .register();

        initClient();

        send(newMessage("resources/subscribe")
                .put("params", new JsonObject().put("uri", uri1)));
        send(newMessage("resources/subscribe")
                .put("params", new JsonObject().put("uri", uri2)));

        manager.getResource(uri1).sendUpdateAndForget();
        manager.getResource(uri2).sendUpdateAndForget();

        List<JsonObject> notifications = client().waitForNotifications(2);
        for (JsonObject n : notifications) {
            assertEquals("notifications/resources/updated", n.getString("method"));
            assertThat(n.getJsonObject("params").getString("uri")).isIn(List.of(uri1, uri2));
        }

        send(newMessage("resources/unsubscribe")
                .put("params", new JsonObject().put("uri", uri1)));

        manager.getResource(uri1).sendUpdateAndForget();
        manager.getResource(uri2).sendUpdateAndForget();

        notifications = client().waitForNotifications(3);
        assertEquals("notifications/resources/updated", notifications.get(2).getString("method"));
        assertEquals(uri2, notifications.get(2).getJsonObject("params").getString("uri"));
    }

    public static class MyResources {

        @Resource(uri = "file:///file1.txt")
        TextResourceContents file1(RequestUri uri) {
            return new TextResourceContents(uri.value(), "File 1", null);
        }
    }

}
