package io.quarkiverse.mcp.server.test.tools.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

public class BearerTokenMcpEndpointTest extends McpServerTest {

    private static final String TOKEN = "test-token-007";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, TestBearerAuthMechanism.class, TestIdentityProvider.class,
                            TestIdentityController.class))
            .overrideConfigKey("quarkus.http.auth.permission.secured.paths", "/mcp*")
            .overrideConfigKey("quarkus.http.auth.permission.secured.policy", "authenticated");

    @BeforeAll
    public static void setupUsers() {
        TestIdentityController.resetRoles()
                .add("alice", "alice", "admin");
    }

    @Test
    public void testStreamableClientWithBearerToken() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setExpectConnectFailure(response -> assertEquals(401, response.statusCode()))
                .build()
                .connect();
        assertFalse(client.isConnected());

        client = McpAssured.newStreamableClient()
                .setBearerToken("nonsense")
                .setExpectConnectFailure(response -> assertEquals(401, response.statusCode()))
                .build()
                .connect();
        assertFalse(client.isConnected());

        client = McpAssured.newStreamableClient()
                .setBearerToken(TOKEN)
                .build()
                .connect();

        client.when()
                .toolsList(page -> {
                    assertEquals(1, page.tools().size());
                })
                .thenAssertResults();
    }

    @Test
    public void testSseClientWithBearerToken() {
        McpSseTestClient client = McpAssured.newSseClient()
                .setExpectSseConnectionFailure()
                .build()
                .connect();
        assertFalse(client.isConnected());

        client = McpAssured.newSseClient()
                .setBearerToken(TOKEN)
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

    /**
     * Authenticates requests whose bearer token is {@link #TOKEN} as the user "alice".
     */
    @ApplicationScoped
    public static class TestBearerAuthMechanism implements HttpAuthenticationMechanism {

        @Override
        public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
            String authorization = context.request().getHeader("Authorization");
            if (("Bearer " + TOKEN).equals(authorization)) {
                return identityProviderManager.authenticate(
                        new UsernamePasswordAuthenticationRequest("alice", new PasswordCredential("alice".toCharArray())));
            }
            return Uni.createFrom().nullItem();
        }

        @Override
        public Uni<ChallengeData> getChallenge(RoutingContext context) {
            return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer"));
        }
    }

}
