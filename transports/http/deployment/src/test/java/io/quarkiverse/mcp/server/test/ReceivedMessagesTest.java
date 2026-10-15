package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class ReceivedMessagesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void testReceivedMessageOrder(boolean streamable) {
        ClientCapability sampling = new ClientCapability(ClientCapability.SAMPLING, Map.of());
        try (McpTestClient<?, ?> client = streamable
                ? McpAssured.newStreamableClient().setClientCapabilities(sampling).build().connect()
                : McpAssured.newSseClient().setClientCapabilities(sampling).build().connect()) {
            Snapshot initial = client.snapshot();
            assertEquals(1, initial.receivedMessages().size());
            JsonObject initializeResponse = initial.responses().get(0);

            JsonObject firstList = client.newRequest("tools/list");
            client.sendAndForget(firstList);
            JsonObject firstListResponse = client.waitForResponse(firstList);
            assertEquals(2, firstListResponse.getJsonObject("result").getJsonArray("tools").size());

            JsonObject progressCall = client.newRequest("tools/call").put("params", new JsonObject()
                    .put("name", "withProgress")
                    .put("_meta", new JsonObject().put("progressToken", "ordered")));
            client.sendAndForget(progressCall);
            JsonObject progressResponse = client.waitForResponse(progressCall);
            assertEquals("done", progressResponse.getJsonObject("result").getJsonArray("content")
                    .getJsonObject(0).getString("text"));
            Snapshot afterProgress = client.snapshot();
            JsonObject progress = afterProgress.notifications().get(0);
            assertEquals("notifications/progress", progress.getString("method"));
            assertEquals("ordered", progress.getJsonObject("params").getString("progressToken"));

            JsonObject samplingCall = client.newRequest("tools/call").put("params", new JsonObject()
                    .put("name", "withSampling"));
            client.sendAndForget(samplingCall);
            JsonObject samplingRequest = client.waitForRequests(1).requests().get(0);
            assertEquals("sampling/createMessage", samplingRequest.getString("method"));
            client.sendAndForget(new JsonObject().put("jsonrpc", "2.0").put("id", samplingRequest.getValue("id"))
                    .put("result", new JsonObject().put("role", "assistant").put("model", "test-model")
                            .put("content", new JsonObject().put("type", "text").put("text", "sampled"))));
            JsonObject samplingResponse = client.waitForResponse(samplingCall);
            assertEquals("sampled", samplingResponse.getJsonObject("result").getJsonArray("content")
                    .getJsonObject(0).getString("text"));

            JsonObject secondList = client.newRequest("tools/list");
            client.sendAndForget(secondList);
            JsonObject secondListResponse = client.waitForResponse(secondList);

            Snapshot snapshot = client.snapshot();
            assertEquals(List.of(initializeResponse, firstListResponse, progress, progressResponse,
                    samplingRequest, samplingResponse, secondListResponse), snapshot.receivedMessages());
            assertEquals(List.of(initializeResponse, firstListResponse, progressResponse, samplingResponse,
                    secondListResponse), snapshot.responses());
            assertEquals(List.of(progress), snapshot.notifications());
            assertEquals(List.of(samplingRequest), snapshot.requests());

            // Later communication must not change an earlier snapshot.
            assertEquals(List.of(initializeResponse), initial.receivedMessages());
            assertEquals(List.of(initializeResponse, firstListResponse, progress, progressResponse),
                    afterProgress.receivedMessages());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.receivedMessages().clear());
        }
    }

    public static class MyTools {

        @Tool
        String withProgress(Progress progress) {
            progress.notificationBuilder().setProgress(1).setTotal(1).build().sendAndForget();
            return "done";
        }

        @Tool
        Uni<String> withSampling(Sampling sampling) {
            return sampling.requestBuilder().setMaxTokens(100)
                    .addMessage(SamplingMessage.withUserRole("Sample a reply"))
                    .build().send().map(response -> response.content().asText().text());
        }
    }
}
