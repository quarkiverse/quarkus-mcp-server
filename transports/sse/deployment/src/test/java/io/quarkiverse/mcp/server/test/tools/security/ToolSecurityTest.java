package io.quarkiverse.mcp.server.test.tools.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;

public class ToolSecurityTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, TestIdentityProvider.class, TestIdentityController.class));

    @BeforeAll
    public static void setupUsers() {
        TestIdentityController.resetRoles()
                .add("alice", "alice", "admin")
                .add("bob", "bob", "user");
    }

    @Test
    public void testSecuredTool() {
        McpSseTestClient client = McpAssured.newSseClient()
                .setBasicAuth("bob", "bob")
                .build()
                .connect();

        client.when()
                // Test injected SecurityIdentity
                .toolsCall("bravo", toolResponse -> {
                    assertEquals("bob", toolResponse.content().get(0).asText().text());
                })
                .basicAuth("alice", "alice")
                .toolsCall("bravo", toolResponse -> {
                    assertEquals("alice", toolResponse.content().get(0).asText().text());
                })
                .noBasicAuth()
                // Test @Authenticated declared on class
                .toolsCall("charlie")
                .withErrorAssert(error -> {
                    assertEquals(JsonRPC.SECURITY_ERROR, error.code());
                    assertEquals("io.quarkus.security.UnauthorizedException", error.message());
                })
                .send()
                // Test @RolesAllowed("admin") declared on method
                .toolsCall("alpha")
                .withArguments(Map.of("price", 2))
                .withErrorAssert(error -> {
                    assertEquals(JsonRPC.SECURITY_ERROR, error.code());
                    assertEquals("io.quarkus.security.UnauthorizedException", error.message());
                })
                .send()
                .basicAuth("bob", "bob")
                .toolsCall("alpha")
                .withArguments(Map.of("price", 2))
                .withErrorAssert(error -> {
                    assertEquals(JsonRPC.SECURITY_ERROR, error.code());
                    assertEquals("io.quarkus.security.ForbiddenException", error.message());
                })
                .send()
                .basicAuth("alice", "alice")
                .toolsCall("alpha", Map.of("price", 2), toolResponse -> {
                    assertEquals("foofoo", toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    @Authenticated
    public static class MyTools {

        @Inject
        SecurityIdentity securityIdentity;

        @RolesAllowed("admin") // bob is user, alice is admin
        @Tool
        TextContent alpha(int price) {
            return new TextContent("foo".repeat(price));
        }

        @Tool
        TextContent bravo() {
            return new TextContent(securityIdentity.getPrincipal().getName());
        }

        @Tool
        TextContent charlie() {
            return new TextContent("ok");
        }

    }

}
