package io.quarkiverse.mcp.server.websocket.test.stateless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.websocket.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class StatelessTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testServerDiscover() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setStateless()
                .build()
                .connect(initResult -> {
                    assertNotNull(initResult);
                    assertNotNull(initResult.capabilities());
                    assertNotNull(initResult.implementation());
                });
        assertTrue(client.isConnected());
        client.disconnect();
    }

    @Test
    public void testToolsList() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsList(tools -> {
                    assertNotNull(tools);
                    assertTrue(tools.size() > 0);
                    assertNotNull(tools.findByName("echo"));
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testToolsCall() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsCall("echo", Map.of("message", "hello stateless"), r -> {
                    assertFalse(r.isError());
                    assertEquals("hello stateless", r.firstContent().asText().text());
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testToolCallWithConnectionAccess() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
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
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
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
    public void testRemovedMethodRejected() {
        McpWebSocketTestClient client = McpAssured.newWebSocketClient()
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
