package io.quarkiverse.mcp.server.test.initrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.MultiMap;

public class ConfiguredUnknownProtocolVersionTest extends McpServerTest {

    static final String UNKNOWN_VERSION = "2025-09-01";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            // A configured version does not have to be one of the versions implemented by this extension
            .overrideConfigKey("quarkus.mcp.server.supported-protocol-versions", UNKNOWN_VERSION)
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testUnknownProtocolVersion() {
        McpSseTestClient client = McpAssured.newSseClient()
                .setProtocolVersion(McpProtocolVersion.from(UNKNOWN_VERSION))
                .build()
                .connect();

        client.when()
                .toolsCall("verifyProtocolVersion", r -> {
                    assertFalse(r.isError());
                    assertEquals("ok", r.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    @Test
    public void testUnknownStreamableProtocolVersion() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setProtocolVersion(McpProtocolVersion.from(UNKNOWN_VERSION))
                // A stateful client sends subsequent requests without the MCP-Protocol-Version header, which then
                // assume 2025-03-26 - a version this server does not support. Send the header explicitly instead.
                .setAdditionalHeaders(m -> MultiMap.caseInsensitiveMultiMap().add("MCP-Protocol-Version", UNKNOWN_VERSION))
                .build()
                .connect();

        client.when()
                .toolsCall("verifyProtocolVersion", r -> {
                    assertFalse(r.isError());
                    assertEquals("ok", r.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String verifyProtocolVersion(McpConnection connection) {
            InitialRequest initRequest = connection.initialRequest();
            McpProtocolVersion protocolVersion = initRequest.protocolVersion();
            if (!UNKNOWN_VERSION.equals(protocolVersion.version())) {
                throw new IllegalStateException(
                        "Expected version " + UNKNOWN_VERSION + " but got " + protocolVersion.version());
            }
            if (protocolVersion.isKnown()) {
                throw new IllegalStateException("Expected unknown protocol version");
            }
            if (protocolVersion.isStateless()) {
                throw new IllegalStateException("Expected stateful protocol version");
            }
            return "ok";
        }
    }

}
