package io.quarkiverse.mcp.server.test.stateless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class StatelessTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testServerDiscover() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect(initResult -> {
                    assertNotNull(initResult);
                    assertNotNull(initResult.capabilities());
                    assertNotNull(initResult.implementation());
                });
        assertTrue(client.isConnected());
        assertNull(client.mcpSessionId());
        client.disconnect();
    }

    @Test
    public void testToolsList() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsList(tools -> {
                    assertNotNull(tools);
                    assertEquals(3, tools.size());
                    assertNotNull(tools.findByName("echo"));
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testToolsCall() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsCall("echo", Map.of("message", "hello stateless"), r -> {
                    // Verify no persistent connection was created
                    assertFalse(connectionManager.iterator().hasNext());
                    assertFalse(r.isError());
                    assertEquals("hello stateless", r.firstContent().asText().text());
                })
                .thenAssertResults();

        // Verify no persistent connection was created
        assertFalse(connectionManager.iterator().hasNext());
        client.disconnect();
    }

    @Test
    public void testToolCallWithConnectionAccess() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsCall("protocolInfo", r -> {
                    assertFalse(r.isError());
                    assertEquals(McpProtocolVersion.FIRST_STATELESS.version() + ":true:true",
                            r.firstContent().asText().text());
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testPerRequestLogLevel() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsCall("logLevelInfo")
                .withMetadata(Map.of("io.modelcontextprotocol/logLevel", "warning"))
                .withAssert(r -> {
                    assertFalse(r.isError());
                    assertEquals(LogLevel.WARNING.name(), r.firstContent().asText().text());
                })
                .send()
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testPingRejected() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .ping()
                .withErrorAssert(error -> {
                    assertEquals(-32601, error.code());
                })
                .send()
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testMissingMetaFieldsRejected() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        // Send a request with only protocolVersion in _meta, missing clientInfo and clientCapabilities
        JsonObject message = client.newRequest("tools/list");
        message.put("params", new JsonObject()
                .put("_meta", new JsonObject()
                        .put(MetaKey.PROTOCOL_VERSION.toString(), McpProtocolVersion.FIRST_STATELESS.version())));

        client.when()
                .message(message)
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
                    assertTrue(error.message().contains(MetaKey.CLIENT_INFO.toString()));
                    assertTrue(error.message().contains(MetaKey.CLIENT_CAPABILITIES.toString()));
                })
                .send()
                .thenAssertResults();
        client.disconnect();
    }

    public static class MyTools {

        @Tool
        String echo(String message) {
            return message;
        }

        @Tool
        String protocolInfo(McpConnection connection) {
            McpProtocolVersion version = connection.initialRequest().protocolVersion();
            return version.version() + ":" + version.isStateless() + ":" + connection.isTransient();
        }

        @Tool
        String logLevelInfo(McpConnection connection) {
            return connection.logLevel().name();
        }
    }

}
