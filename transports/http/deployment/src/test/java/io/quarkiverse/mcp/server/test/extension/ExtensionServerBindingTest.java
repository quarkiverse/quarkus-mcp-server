package io.quarkiverse.mcp.server.test.extension;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkiverse.mcp.server.McpExtensionSetting;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.MetaField.Type;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

/**
 * Verifies that {@link McpServer} bindings are honored for {@link McpExtension} - an extension is advertised and its custom
 * methods are handled only on the servers it is bound to. Uses a default-bound extension and one bound to multiple servers.
 */
public class ExtensionServerBindingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(DefaultExtension.class, MultiExtension.class, AllExtension.class))
            .overrideConfigKey("quarkus.mcp.server.support-multi-server-bindings", "true")
            .overrideConfigKey("quarkus.mcp.server.http.root-path", "/alpha/mcp")
            .overrideConfigKey("quarkus.mcp.server.bravo.http.root-path", "/bravo/mcp");

    @Test
    public void testDefaultServer() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/alpha/mcp")
                .build()
                .connect(initResult -> {
                    Map<String, Object> extensions = extensions(initResult);
                    // The default-bound, the multi-bound (default + bravo) and the ALL-bound extensions are advertised here
                    assertEquals(Map.of("read", Boolean.TRUE), extensions.get("com.acme/default"));
                    assertEquals(Map.of("read", Boolean.TRUE), extensions.get("com.acme/multi"));
                    assertEquals(Map.of("read", Boolean.TRUE), extensions.get("com.acme/all"));
                });
        try (client) {
            client.when()
                    .message(client.newRequest("default/ping"))
                    .withAssert(response -> assertEquals("default", response.getJsonObject("result").getString("ext")))
                    .send()
                    .message(client.newRequest("multi/ping"))
                    .withAssert(response -> assertEquals("multi", response.getJsonObject("result").getString("ext")))
                    .send()
                    .message(client.newRequest("all/ping"))
                    .withAssert(response -> assertEquals("all", response.getJsonObject("result").getString("ext")))
                    .send()
                    .thenAssertResults();
        }
    }

    @Test
    public void testBravoServer() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/bravo/mcp")
                .build()
                .connect(initResult -> {
                    Map<String, Object> extensions = extensions(initResult);
                    // The multi-bound and the ALL-bound extensions reach the bravo server
                    assertEquals(Map.of("read", Boolean.TRUE), extensions.get("com.acme/multi"));
                    assertEquals(Map.of("read", Boolean.TRUE), extensions.get("com.acme/all"));
                    assertFalse(extensions.containsKey("com.acme/default"),
                            "The default-bound extension must not be advertised on the bravo server");
                });
        try (client) {
            client.when()
                    // multi/ping is bound to bravo -> handled
                    .message(client.newRequest("multi/ping"))
                    .withAssert(response -> assertEquals("multi", response.getJsonObject("result").getString("ext")))
                    .send()
                    // all/ping is bound to every server -> handled
                    .message(client.newRequest("all/ping"))
                    .withAssert(response -> assertEquals("all", response.getJsonObject("result").getString("ext")))
                    .send()
                    // default/ping is not bound to bravo -> unknown method
                    .message(client.newRequest("default/ping"))
                    .withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.METHOD_NOT_FOUND, error.code()))
                    .send()
                    .thenAssertResults();
        }
    }

    static Map<String, Object> extensions(McpAssured.InitResult initResult) {
        ServerCapability extensions = initResult.capabilities().stream()
                .filter(c -> c.name().equals("extensions"))
                .findFirst()
                .orElse(null);
        assertNotNull(extensions, "The extensions capability should be advertised");
        assertTrue(!extensions.properties().isEmpty());
        return extensions.properties();
    }

    @McpExtension(id = "com.acme/default")
    @McpExtensionSetting(name = "read", type = Type.BOOLEAN, value = "true")
    public static class DefaultExtension {

        @McpExtensionMethod("default/ping")
        public JsonObject ping() {
            return new JsonObject().put("ext", "default");
        }
    }

    // Bound to BOTH the default server and bravo via repeatable @McpServer
    @McpServer(DEFAULT)
    @McpServer("bravo")
    @McpExtension(id = "com.acme/multi")
    @McpExtensionSetting(name = "read", type = Type.BOOLEAN, value = "true")
    public static class MultiExtension {

        @McpExtensionMethod("multi/ping")
        public JsonObject ping() {
            return new JsonObject().put("ext", "multi");
        }
    }

    // Bound to ALL servers (default + bravo) via @McpServer(McpServer.ALL)
    @McpServer(McpServer.ALL)
    @McpExtension(id = "com.acme/all")
    @McpExtensionSetting(name = "read", type = Type.BOOLEAN, value = "true")
    public static class AllExtension {

        @McpExtensionMethod("all/ping")
        public JsonObject ping() {
            return new JsonObject().put("ext", "all");
        }
    }

}
