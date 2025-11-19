package io.quarkiverse.mcp.server.test.roots;

import java.util.Map;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;

public class RootsTest extends AbstractRootsTest {

    @Override
    protected McpTestClient<?, ?> testClient() {
        return McpAssured.newSseClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.ROOTS, Map.of()))
                .build()
                .connect();
    }

}
