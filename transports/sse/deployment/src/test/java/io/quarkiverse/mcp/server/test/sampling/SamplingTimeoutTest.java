package io.quarkiverse.mcp.server.test.sampling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.ResponseHandlers;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class SamplingTimeoutTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Inject
    ResponseHandlers responseHandlers;

    @Test
    public void testSampling() throws InterruptedException {
        McpSseTestClient client = McpAssured.newSseClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of()))
                .build()
                .connect();

        JsonObject request = client.newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "samplingDefaultTimeout"));
        client.sendAndForget(request);

        // The server should send a sampling request
        List<JsonObject> requests = client.waitForRequests(1).requests();
        assertEquals("sampling/createMessage", requests.get(0).getString("method"));
        Long id = requests.get(0).getLong("id");
        // But the client does not respond...
        assertTrue(MyTools.INIT_LATCH1.await(5, TimeUnit.SECONDS));
        assertNotNull(MyTools.ERROR1.get());
        assertFalse(responseHandlers.hasHandler(id));
    }

    @Singleton
    public static class MyTools {

        static final CountDownLatch INIT_LATCH1 = new CountDownLatch(1);
        static final AtomicReference<Throwable> ERROR1 = new AtomicReference<>();

        @Tool(description = "A tool that is using sampling...")
        Uni<String> samplingDefaultTimeout(Sampling sampling) {
            SamplingRequest samplingRequest = sampling.requestBuilder()
                    .setMaxTokens(100)
                    .addMessage(SamplingMessage.withUserRole("What's happening?"))
                    .setTimeout(Duration.ofSeconds(1))
                    .build();
            return samplingRequest.send()
                    .onFailure().recoverWithItem(t -> {
                        ERROR1.set(t);
                        return null;
                    })
                    .eventually(() -> INIT_LATCH1.countDown())
                    .replaceWith("nok");
        }

    }

}
