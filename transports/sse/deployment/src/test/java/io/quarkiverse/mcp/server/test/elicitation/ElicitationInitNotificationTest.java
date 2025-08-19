package io.quarkiverse.mcp.server.test.elicitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class ElicitationInitNotificationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(Notifications.class));

    @Test
    public void testElicitation() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setOpenSubsidiarySse(true)
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();

        // The server should send an elicitation request
        List<JsonObject> requests = client.waitForRequests(1).requests();
        assertEquals("elicitation/create", requests.get(0).getString("method"));
        Long id = requests.get(0).getLong("id");
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("result", new JsonObject()
                        .put("action", ElicitationResponse.Action.ACCEPT.toString().toLowerCase())
                        .put("content", new JsonObject()
                                .put("username", "mkouba")))
                .put("id", id);
        // Send the response back to the server
        client.sendAndForget(response);

        assertTrue(Notifications.INIT_LATCH.await(5, TimeUnit.SECONDS));
        assertEquals("mkouba", Notifications.USERNAME.get());
    }

    @Singleton
    public static class Notifications {

        static final CountDownLatch INIT_LATCH = new CountDownLatch(1);
        static final AtomicReference<String> USERNAME = new AtomicReference<>();

        @Notification(Type.INITIALIZED)
        Uni<Void> init(Elicitation elicitation) {
            ElicitationRequest r = elicitation.requestBuilder()
                    .setMessage("What's your github account?")
                    .addSchemaProperty("username", new StringSchema())
                    .build();
            return r.send().invoke(er -> {
                USERNAME.set(er.content().getString("username"));
                INIT_LATCH.countDown();
            }).replaceWithVoid();
        }

    }

}
