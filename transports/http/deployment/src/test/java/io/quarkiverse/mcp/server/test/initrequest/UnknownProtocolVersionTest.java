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
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class UnknownProtocolVersionTest extends McpServerTest {

    static final String UNKNOWN_VERSION = "2025-09-01";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
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
