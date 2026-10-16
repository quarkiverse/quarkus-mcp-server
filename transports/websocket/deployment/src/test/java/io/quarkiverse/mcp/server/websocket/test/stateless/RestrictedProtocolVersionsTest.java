package io.quarkiverse.mcp.server.websocket.test.stateless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.websocket.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

class RestrictedProtocolVersionsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.supported-protocol-versions", "2026-07-28");

    @Test
    void permitsModernRequestsButRejectsLegacyInitialization() {
        var client = McpAssured.newWebSocketClient().setStateless().build().connect();
        try {
            client.when().toolsList(tools -> assertNotNull(tools.findByName("echo")))
                    .message(new JsonObject().put("jsonrpc", "2.0").put("id", 101).put("method", "initialize")
                            .put("params", new JsonObject().put("protocolVersion", "2025-11-25")
                                    .put("capabilities", new JsonObject())
                                    .put("clientInfo", new JsonObject().put("name", "test").put("version", "1"))))
                    .withErrorAssert(error -> assertEquals(-32601, error.code())).send().thenAssertResults();
        } finally {
            client.disconnect();
        }
    }

    @Test
    void rejectsUnimplementedFutureRevision() {
        var client = McpAssured.newWebSocketClient().setStateless().build().connect();
        try {
            client.when().message(new JsonObject().put("jsonrpc", "2.0").put("id", 102).put("method", "tools/list")
                    .put("params", new JsonObject().put("_meta", new JsonObject()
                            .put("io.modelcontextprotocol/protocolVersion", "2099-01-01")
                            .put("io.modelcontextprotocol/clientCapabilities", new JsonObject()))))
                    .withErrorAssert(error -> assertEquals(-32022, error.code())).send().thenAssertResults();
        } finally {
            client.disconnect();
        }
    }

    public static class MyTools {
        @Tool
        String echo(String message) {
            return message;
        }
    }
}
