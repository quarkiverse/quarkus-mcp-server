package io.quarkiverse.mcp.server.test.tools.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpSseClient;
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
        McpSseClient client = newClient();

        CompletableFuture<HttpResponse<Void>> cf = client.connect();
        // Unauthorized
        assertEquals(401, cf.get(5, TimeUnit.SECONDS).statusCode());

        // If it succeeds then the returned http response never completes
        client.connect(Map.of("Authorization", getBasicAuthenticationHeader("bob", "bob")));
        assertNotNull(client.waitForFirstEvent());
    }

    private static final String getBasicAuthenticationHeader(String username, String password) {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes());
    }

    public static class MyTools {

        @Tool
        TextContent alpha(int price) {
            return new TextContent("foo".repeat(price));
        }

    }

}
