package io.quarkiverse.mcp.server.test.sampling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SamplingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testSampling() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            initClient();
            JsonObject toolCallMessage = newMessage("tools/call")
                    .put("params", new JsonObject()
                            .put("name", "samplingFoo"));

            // We need to send the request on a separate thread
            // because the response is not completed until the sampling response is sent
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    send(toolCallMessage);
                }
            });

            // The server should send a sampling request
            List<JsonObject> requests = client().waitForRequests(1);
            assertEquals("sampling/createMessage", requests.get(0).getString("method"));
            Long id = requests.get(0).getLong("id");
            JsonObject message = newResult(id, new JsonObject()
                    .put("role", "assistant")
                    .put("model", "claude-3-sonnet-20240307")
                    .put("content", new JsonObject()
                            .put("type", "text")
                            .put("text", "It's ok buddy.")));
            // Send the response back to the server
            send(message);

            JsonObject toolCallResponse = waitForLastResponse();
            JsonObject toolCallResult = assertResultResponse(toolCallMessage, toolCallResponse);
            assertNotNull(toolCallResult);
            assertFalse(toolCallResult.getBoolean("isError"));
            JsonArray content = toolCallResult.getJsonArray("content");
            assertEquals(1, content.size());
            JsonObject textContent = content.getJsonObject(0);
            assertEquals("text", textContent.getString("type"));
            assertEquals("It's ok buddy.", textContent.getString("text"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Singleton
    public static class MyTools {

        @Tool(description = "A tool that is using sampling...")
        Uni<String> samplingFoo(Sampling sampling) {
            if (sampling.isSupported()) {
                SamplingRequest samplingRequest = sampling.requestBuilder()
                        .setMaxTokens(100)
                        .addMessage(SamplingMessage.withUserRole("What's happening?"))
                        .build();
                return samplingRequest.send().map(sr -> sr.content().asText().text());
            } else {
                return Uni.createFrom().item("You are nok");
            }
        }

    }

    @Override
    protected JsonObject getClientCapabilities() {
        return new JsonObject().put("sampling", Map.of());
    }

}
