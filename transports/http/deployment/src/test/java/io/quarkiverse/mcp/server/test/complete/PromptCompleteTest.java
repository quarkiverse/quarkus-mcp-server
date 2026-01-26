package io.quarkiverse.mcp.server.test.complete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.PromptCompletionManager;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class PromptCompleteTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Inject
    PromptCompletionManager manager;

    @Test
    public void testCompletion() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .message(client.newRequest(McpMessageHandler.COMPLETION_COMPLETE)
                        .put("params", new JsonObject()))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertEquals("Reference not found", error.message());
                })
                .send()
                .message(client.newRequest(McpMessageHandler.COMPLETION_COMPLETE)
                        .put("params", new JsonObject().put("ref", new JsonObject())))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertEquals("Reference type not found", error.message());
                })
                .send()
                .message(client.newRequest(McpMessageHandler.COMPLETION_COMPLETE)
                        .put("params", new JsonObject().put("ref", new JsonObject().put("type", Messages.PROMPT_REF))))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertEquals("Argument not found", error.message());
                })
                .send()
                .message(client.newRequest(McpMessageHandler.COMPLETION_COMPLETE)
                        .put("params", new JsonObject()
                                .put("argument", new JsonObject())
                                .put("ref", new JsonObject().put("type", "bum"))))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertEquals("Unsupported reference found: bum", error.message());
                })
                .send()
                .promptComplete("foo", "name", "Vo", completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("Vojtik", completionResponse.values().get(0));
                })
                .promptComplete("foo")
                .withArgument("suffix", "Vo")
                .withAssert(completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("_foo", completionResponse.values().get(0));
                })
                .send()
                .thenAssertResults();

        assertThrows(IllegalArgumentException.class,
                () -> manager.newCompletion("foo").setArgumentName("suffix").setHandler(args -> null).register());
    }
}
