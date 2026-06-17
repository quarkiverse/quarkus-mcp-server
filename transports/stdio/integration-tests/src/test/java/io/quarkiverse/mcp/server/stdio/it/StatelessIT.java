package io.quarkiverse.mcp.server.stdio.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioTestClient;

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
                    .toolsCall("logLevelInfo")
                    .withMetadata(Map.of("io.modelcontextprotocol/logLevel", "warning"))
                    .withAssert(r -> {
                        assertFalse(r.isError());
                        assertEquals(LogLevel.WARNING.name(), r.firstContent().asText().text());
                    })
                    .send()
                    .thenAssertResults();
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
