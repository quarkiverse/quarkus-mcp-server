package io.quarkiverse.mcp.server.schema.validator.test.ping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;

public class PingSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testPing() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .pingPong()
                .pingPong()
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String dummy() {
            return "dummy";
        }
    }
}
