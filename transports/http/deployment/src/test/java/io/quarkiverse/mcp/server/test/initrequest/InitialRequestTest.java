package io.quarkiverse.mcp.server.test.initrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.InitialRequest.Transport;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class InitialRequestTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testInitRequest() {
        McpSseTestClient client = McpAssured.newSseClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of()))
                .build()
                .connect();

        client.when()
                .toolsCall("testInitRequest", r -> {
                    assertFalse(r.isError());
                    assertEquals("ok", r.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String testInitRequest(McpConnection connection) {
            InitialRequest initRequest = connection.initialRequest();
            if (initRequest != null) {
                if (!initRequest.protocolVersion().equals("2024-11-05")) {
                    throw new IllegalStateException();
                }
                if (!initRequest.implementation().name().equals("test-client")) {
                    throw new IllegalStateException();
                }
                if (initRequest.clientCapabilities().size() != 1
                        || !initRequest.clientCapabilities().get(0).name().equals("sampling")) {
                    throw new IllegalStateException();
                }
                if (initRequest.transport() != Transport.SSE) {
                    throw new IllegalStateException();
                }
            }
            return "ok";
        }
    }

}
