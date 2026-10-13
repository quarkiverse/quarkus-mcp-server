package io.quarkiverse.mcp.server.stdio.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioTestClient;
import io.vertx.core.json.JsonObject;

public class StatelessIT {

    @Test
    public void testServerDiscover() {
        try (McpStdioTestClient client = McpAssured.newStdioClient()
                .setStateless()
                .build()
                .connect(initResult -> {
                    assertNotNull(initResult);
                    assertNotNull(initResult.capabilities());
                    assertNotNull(initResult.implementation());
                })) {
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void testToolsCall() {
        try (McpStdioTestClient client = McpAssured.newStdioClient()
                .setStateless()
                .build()
                .connect()) {

            client.when()
                    .toolsCall("toLowerCase", Map.of("value", "HELLO"), r -> {
                        assertFalse(r.isError());
                        assertEquals("hello", r.firstContent().asText().text());
                    })
                    .thenAssertResults();
        }
    }

    @Test
    public void testToolCallWithConnectionAccess() {
        try (McpStdioTestClient client = McpAssured.newStdioClient()
                .setStateless()
                .build()
                .connect()) {

            client.when()
                    .toolsCall("protocolInfo", r -> {
                        assertFalse(r.isError());
                        assertEquals(McpProtocolVersion.FIRST_STATELESS.version() + ":true:true",
                                r.firstContent().asText().text());
                    })
                    .thenAssertResults();
        }
    }

    @Test
    public void testPerRequestLogLevel() {
        try (McpStdioTestClient client = McpAssured.newStdioClient()
                .setStateless()
                .build()
                .connect()) {

            client.when()
                    // The client opts in to log notifications for this request => the level is honored
                    .toolsCall("logLevelInfo")
                    .withMetadata(Map.of(MetaKey.LOG_LEVEL.toString(), LogLevel.WARNING.name().toLowerCase()))
                    .withAssert(r -> {
                        assertFalse(r.isError());
                        assertEquals(LogLevel.WARNING.name(), r.firstContent().asText().text());
                    })
                    .send()
                    // The client did not opt in => McpLog#level() returns null and no log notifications are sent
                    .toolsCall("logLevelInfo")
                    .withAssert(r -> {
                        assertFalse(r.isError());
                        assertEquals("none", r.firstContent().asText().text());
                    })
                    .send()
                    .thenAssertResults();
        }
    }

    @Test
    public void testLogNotificationOptIn() {
        try (McpStdioTestClient client = McpAssured.newStdioClient()
                .setStateless()
                .build()
                .connect()) {

            // The client did not opt in => the tool logs server-side but no notifications/message is delivered
            client.when()
                    .toolsCall("logMessage")
                    .withAssert(r -> {
                        assertFalse(r.isError());
                        assertEquals("ok", r.firstContent().asText().text());
                    })
                    .send()
                    .thenAssertResults();
            assertTrue(client.snapshot().notifications().isEmpty(),
                    "No log notification must be sent for a request that does not include the logLevel _meta field");

            // The client opts in at INFO => a single notifications/message is delivered
            client.when()
                    .toolsCall("logMessage")
                    .withMetadata(Map.of(MetaKey.LOG_LEVEL.toString(), LogLevel.INFO.name().toLowerCase()))
                    .withAssert(r -> {
                        assertFalse(r.isError());
                        assertEquals("ok", r.firstContent().asText().text());
                    })
                    .send()
                    .thenAssertResults();
            List<JsonObject> notifications = client.waitForNotifications(1).notifications();
            assertEquals(1, notifications.size());
            JsonObject params = notifications.get(0).getJsonObject("params");
            assertEquals(LogLevel.INFO.toString().toLowerCase(), params.getString("level"));
            assertEquals("tool:logMessage", params.getString("logger"));
            assertEquals("Hello from logMessage", params.getString("data"));
        }
    }

    @Test
    public void testRemovedMethodRejected() {
        try (McpStdioTestClient client = McpAssured.newStdioClient()
                .setStateless()
                .build()
                .connect()) {

            client.when()
                    .ping()
                    .withErrorAssert(error -> {
                        assertEquals(-32601, error.code());
                    })
                    .send()
                    .thenAssertResults();
        }
    }

}
