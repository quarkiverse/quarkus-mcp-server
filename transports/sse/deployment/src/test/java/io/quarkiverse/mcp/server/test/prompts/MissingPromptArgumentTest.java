package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkus.test.QuarkusUnitTest;

public class MissingPromptArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyPrompts.class));

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .promptsGet("uni_bar")
                .withErrorAssert(e -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.code());
                    assertEquals("Missing required argument: val", e.message());
                })
                .send()
                .thenAssertResults();
    }

}
