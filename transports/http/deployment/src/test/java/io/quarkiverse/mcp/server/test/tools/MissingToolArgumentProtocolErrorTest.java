package io.quarkiverse.mcp.server.test.tools;

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

public class MissingToolArgumentProtocolErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.tools.input-validation-error", "protocol");

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("bravo")
                .withErrorAssert(e -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.code());
                    assertEquals("Missing required argument: price", e.message());
                })
                .send()
                .thenAssertResults();
    }

}
