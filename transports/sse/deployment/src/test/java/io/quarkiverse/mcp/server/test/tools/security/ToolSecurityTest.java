package io.quarkiverse.mcp.server.test.tools.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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
    public void testSecuredTool() throws URISyntaxException {
        initClient();

        // Test injected SecurityIdentity
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo"));

        sendSecured(message, "bob", "bob");
        assertToolResponse(message, waitForLastResponse(), "bob");

        message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo"));
        sendSecured(message, "alice", "alice");
        assertToolResponse(message, waitForLastResponse(), "alice");

        // Test @Authenticated declared on class
        message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "charlie"));
        send(message);
        JsonObject response = waitForLastResponse();
        assertEquals(JsonRPC.SECURITY_ERROR, response.getJsonObject("error").getInteger("code"));
        assertEquals("io.quarkus.security.UnauthorizedException", response.getJsonObject("error").getString("message"));

        // Test @RolesAllowed("admin") declared on method
        message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "alpha")
                        .put("arguments", new JsonObject()
                                .put("price", 2)));
        send(message);
        response = waitForLastResponse();
        assertEquals(JsonRPC.SECURITY_ERROR, response.getJsonObject("error").getInteger("code"));
        assertEquals("io.quarkus.security.UnauthorizedException", response.getJsonObject("error").getString("message"));

        message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "alpha")
                        .put("arguments", new JsonObject()
                                .put("price", 2)));
        sendSecured(message, "bob", "bob");
        response = waitForLastResponse();
        assertEquals(JsonRPC.SECURITY_ERROR, response.getJsonObject("error").getInteger("code"));
        assertEquals("io.quarkus.security.ForbiddenException", response.getJsonObject("error").getString("message"));

        message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "alpha")
                        .put("arguments", new JsonObject()
                                .put("price", 2)));
        sendSecured(message, "alice", "alice");
        assertToolResponse(message, waitForLastResponse(), "foofoo");
    }

    private void assertToolResponse(JsonObject message, JsonObject response, String expectedText) {
        JsonObject toolResult = assertResponseMessage(message, response);
        assertNotNull(toolResult);
        assertFalse(toolResult.getBoolean("isError"));
        JsonArray content = toolResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText, textContent.getString("text"));
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
