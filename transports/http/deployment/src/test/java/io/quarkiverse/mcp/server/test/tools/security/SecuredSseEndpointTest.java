package io.quarkiverse.mcp.server.test.tools.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;

public class SecuredSseEndpointTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, TestIdentityProvider.class, TestIdentityController.class))
            .overrideConfigKey("quarkus.http.auth.permission.secured.paths", "/mcp/sse")
            .overrideConfigKey("quarkus.http.auth.permission.secured.policy", "authenticated");

    @BeforeAll
    public static void setupUsers() {
        TestIdentityController.resetRoles()
                .add("alice", "alice", "admin")
                .add("bob", "bob", "user");
    }

    @Test
    public void testSseEndpoint() throws InterruptedException, ExecutionException, TimeoutException {
        McpSseTestClient client = McpAssured.newSseClient()
                .setExpectSseConnectionFailure()
                .build()
                .connect();
        assertFalse(client.isConnected());

        client = McpAssured.newSseClient()
                .setBasicAuth("bob", "bob")
                .build()
                .connect();
        assertTrue(client.isConnected());
    }

    public static class MyTools {

        @Tool
        TextContent alpha(int price) {
            return new TextContent("foo".repeat(price));
        }

    }

}
