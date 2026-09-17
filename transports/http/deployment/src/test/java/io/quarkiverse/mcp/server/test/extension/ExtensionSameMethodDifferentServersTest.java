package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

/**
 * Verifies that two extension methods sharing the same JSON-RPC name but bound to different servers are allowed (no build
 * failure) and each is dispatched on its own server.
 */
public class ExtensionSameMethodDifferentServersTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(AlphaExtension.class, BravoExtension.class))
            .overrideConfigKey("quarkus.mcp.server.http.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.http.root-path", "/bravo/mcp");

    @Test
    public void testDefaultServer() {
        assertGet("/alpha/mcp", "alpha");
    }

    @Test
    public void testBravoServer() {
        assertGet("/bravo/mcp", "bravo");
    }

    static void assertGet(String mcpPath, String expectedServer) {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath(mcpPath)
                .build()
                .connect();
        try (client) {
            client.when()
                    .message(client.newRequest("skills/get"))
                    .withAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertNotNull(result);
                        assertEquals(expectedServer, result.getString("server"));
                    })
                    .send()
                    .thenAssertResults();
        }
    }

    // Bound to the default server
    @McpExtension(id = "com.acme/alpha")
    public static class AlphaExtension {

        @McpExtensionMethod("skills/get")
        public JsonObject get() {
            return new JsonObject().put("server", "alpha");
        }
    }

    // Same method name but bound to the bravo server
    @McpServer("bravo")
    @McpExtension(id = "com.acme/bravo")
    public static class BravoExtension {

        @McpExtensionMethod("skills/get")
        public JsonObject get() {
            return new JsonObject().put("server", "bravo");
        }
    }

}
