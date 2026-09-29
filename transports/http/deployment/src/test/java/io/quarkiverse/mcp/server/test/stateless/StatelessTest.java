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

import io.quarkiverse.mcp.server.CacheScope;
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

    private static final String SERVER_NAME = "StatelessServer";
    private static final String SERVER_VERSION = "3.0";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.server-info.name", SERVER_NAME)
            .overrideConfigKey("quarkus.mcp.server.server-info.version", SERVER_VERSION)
            .overrideConfigKey("quarkus.mcp.server.discover.ttl-ms", "45000")
            .overrideConfigKey("quarkus.mcp.server.discover.cache-scope", "public");

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
                    // In the 2026-07-28 schema the server identity is decoded from
                    // _meta["io.modelcontextprotocol/serverInfo"]
                    assertEquals(SERVER_NAME, initResult.implementation().name());
                    assertEquals(SERVER_VERSION, initResult.implementation().version());
                    assertNotNull(initResult.cacheControl());
                    assertEquals(45000, initResult.cacheControl().ttlMs());
                    assertEquals(CacheScope.PUBLIC, initResult.cacheControl().cacheScope());
                });
        assertTrue(client.isConnected());
        assertNull(client.mcpSessionId());

        // Verify the raw wire format: the 2026-07-28 DiscoverResult carries the server identity
        // solely in _meta["io.modelcontextprotocol/serverInfo"] and has no top-level serverInfo member
        JsonObject discover = client.newRequest(McpAssured.SERVER_DISCOVER);
        McpAssured.injectStatelessMeta(discover);
        client.when()
                .message(discover)
                .withAssert(response -> {
                    JsonObject result = response.getJsonObject("result");
                    assertNotNull(result);
                    assertNull(result.getJsonObject("serverInfo"),
                            "discover result must not carry a top-level serverInfo");
                    JsonObject meta = result.getJsonObject("_meta");
                    assertNotNull(meta, "discover result must carry _meta");
                    JsonObject serverInfo = meta.getJsonObject(MetaKey.SERVER_INFO.toString());
                    assertNotNull(serverInfo, "discover result must carry serverInfo in _meta");
                    assertEquals(SERVER_NAME, serverInfo.getString("name"));
                    assertEquals(SERVER_VERSION, serverInfo.getString("version"));
                })
                .send()
                .thenAssertResults();

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
    public void testToolsListWithoutOptionalClientInfo() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        JsonObject message = client.newRequest("tools/list");
        message.put("params", new JsonObject()
                .put("_meta", new JsonObject()
                        .put(MetaKey.PROTOCOL_VERSION.toString(), McpProtocolVersion.FIRST_STATELESS.version())
                        .put(MetaKey.CLIENT_CAPABILITIES.toString(), new JsonObject())));

        client.when()
                .message(message)
                .withAssert(result -> assertEquals(3, result.getJsonObject("result").getJsonArray("tools").size()))
                .send()
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

        // Send a request with only protocolVersion in _meta, missing clientCapabilities
        JsonObject message = client.newRequest("tools/list");
        message.put("params", new JsonObject()
                .put("_meta", new JsonObject()
                        .put(MetaKey.PROTOCOL_VERSION.toString(), McpProtocolVersion.FIRST_STATELESS.version())));

        client.when()
                .message(message)
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
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
