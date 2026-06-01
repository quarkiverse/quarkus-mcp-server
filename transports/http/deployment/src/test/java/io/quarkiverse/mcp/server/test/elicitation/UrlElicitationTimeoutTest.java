package io.quarkiverse.mcp.server.test.elicitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationCompletion;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.ServerRequests;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class UrlElicitationTimeoutTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Inject
    ServerRequests serverRequests;

    @Inject
    ElicitationCompletion elicitationCompletion;

    @Test
    public void testUrlElicitationTimeout() throws InterruptedException {
        McpSseTestClient client = McpAssured.newSseClient()
                .setClientCapabilities(
                        new ClientCapability(ClientCapability.ELICITATION, Map.of("form", Map.of(), "url", Map.of())))
                .build()
                .connect();

        JsonObject request = client.newRequest("tools/call")
                .put("params", new JsonObject()
                        .put("name", "urlElicitationTimeout"));
        client.sendAndForget(request);

        // The server should send a URL mode elicitation request
        List<JsonObject> requests = client.waitForRequests(1).requests();
        assertEquals("elicitation/create", requests.get(0).getString("method"));
        assertEquals("url", requests.get(0).getJsonObject("params").getString("mode"));
        Long id = requests.get(0).getLong("id");
        String elicitationId = requests.get(0).getJsonObject("params").getString("elicitationId");

        // But the client does not respond...
        assertTrue(MyTools.LATCH.await(5, TimeUnit.SECONDS));
        assertNotNull(MyTools.ERROR.get());
        assertFalse(serverRequests.hasResponseHandler(id));

        // The pending elicitation entry should also be cleaned up
        assertFalse(serverRequests.hasPendingElicitation(elicitationId));
        try {
            elicitationCompletion.send(elicitationId);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Singleton
    public static class MyTools {

        static final CountDownLatch LATCH = new CountDownLatch(1);
        static final AtomicReference<Throwable> ERROR = new AtomicReference<>();

        @Tool
        Uni<String> urlElicitationTimeout(Elicitation elicitation) {
            return elicitation.urlRequestBuilder()
                    .setMessage("Please authorize")
                    .setUrl("https://example.com/authorize")
                    .setTimeout(Duration.ofSeconds(1))
                    .build().send()
                    .onFailure().recoverWithItem(t -> {
                        ERROR.set(t);
                        return null;
                    })
                    .eventually(() -> LATCH.countDown())
                    .replaceWith("nok");
        }

    }

}
