package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class PromptInternalErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .promptsGet("uni_bar")
                .withArguments(Map.of("val", "lav"))
                .withErrorAssert(e -> {
                    assertEquals(JsonRPC.INTERNAL_ERROR, e.code());
                    assertEquals("Internal error", e.message());
                })
                .send()
                .thenAssertResults();

    }

    public static class MyPrompts {

        @Prompt
        Uni<PromptMessage> uni_bar(String val) {
            return Uni.createFrom().failure(new NullPointerException());
        }

    }

}
