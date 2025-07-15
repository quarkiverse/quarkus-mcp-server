package io.quarkiverse.mcp.server.test.complete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.PromptCompletionManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
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
        JsonObject completeMessage = client.newRequest("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/prompt")
                                .put("name", "foo"))
                        .put("argument", new JsonObject()
                                .put("name", "name")
                                .put("value", "Vo")));
        client.sendAndForget(completeMessage);

        JsonObject completeResponse = client.waitForResponse(completeMessage);
        JsonObject completeResult = completeResponse.getJsonObject("result");
        assertNotNull(completeResult);
        JsonArray values = completeResult.getJsonObject("completion").getJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("Vojtik", values.getString(0));

        completeMessage = client.newRequest("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/prompt")
                                .put("name", "foo"))
                        .put("argument", new JsonObject()
                                .put("name", "suffix")
                                .put("value", "Vo")));
        client.sendAndForget(completeMessage);

        completeResponse = client.waitForResponse(completeMessage);

        completeResult = completeResponse.getJsonObject("result");
        assertNotNull(completeResult);
        values = completeResult.getJsonObject("completion").getJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("_foo", values.getString(0));

        assertThrows(IllegalArgumentException.class,
                () -> manager.newCompletion("foo").setArgumentName("suffix").setHandler(args -> null).register());
    }
}
