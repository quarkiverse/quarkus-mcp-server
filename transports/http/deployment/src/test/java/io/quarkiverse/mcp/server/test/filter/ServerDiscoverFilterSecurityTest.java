package io.quarkiverse.mcp.server.test.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptFilter;
import io.quarkiverse.mcp.server.PromptManager.PromptInfo;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceFilter;
import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Reproducer for <a href="https://github.com/quarkiverse/quarkus-mcp-server/issues/997">#997</a>:
 * {@code server/discover} must evaluate feature filters with the same active CDI request context and
 * associated identity as {@code tools/list}, {@code prompts/list} and {@code resources/list}.
 */
public class ServerDiscoverFilterSecurityTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class, RoleBasedFilter.class, TestIdentityProvider.class,
                            TestIdentityController.class));

    @BeforeAll
    public static void setupUsers() {
        TestIdentityController.resetRoles()
                .add("alice", "alice", "my-role")
                .add("bob", "bob", "other-role");
    }

    @Test
    public void testDiscoverWithMatchingRole() {
        McpAssured.newStreamableClient()
                .setStateless()
                .setBasicAuth("alice", "alice")
                .build()
                .connect(initResult -> {
                    // alice has my-role, so all filtered features must be advertised
                    assertTrue(hasCapability(initResult, "tools"));
                    assertTrue(hasCapability(initResult, "prompts"));
                    assertTrue(hasCapability(initResult, "resources"));
                });
    }

    @Test
    public void testDiscoverWithoutMatchingRole() {
        McpAssured.newStreamableClient()
                .setStateless()
                .setBasicAuth("bob", "bob")
                .build()
                .connect(initResult -> {
                    // bob does not have my-role, so the filtered features must NOT be advertised
                    // Before the fix the filter threw ContextNotActiveException and the features were
                    // advertised anyway (fail-open)
                    assertFalse(hasCapability(initResult, "tools"));
                    assertFalse(hasCapability(initResult, "prompts"));
                    assertFalse(hasCapability(initResult, "resources"));
                });
    }

    private static boolean hasCapability(InitResult initResult, String name) {
        List<ServerCapability> capabilities = initResult.capabilities();
        return capabilities != null && capabilities.stream().map(ServerCapability::name).anyMatch(Predicate.isEqual(name));
    }

    public static class MyFeatures {

        @Tool
        String alpha() {
            return "alpha";
        }

        @Prompt
        PromptResponse bravo() {
            return PromptResponse.withMessages(PromptMessage.withUserRole("bravo"));
        }

        @Resource(uri = "file:///charlie")
        TextResourceContents charlie(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "charlie");
        }
    }

    // @Singleton added automatically
    public static class RoleBasedFilter implements ToolFilter, PromptFilter, ResourceFilter {

        @Inject
        SecurityIdentity identity;

        @Override
        public boolean test(ToolInfo tool, McpConnection connection) {
            return identity.hasRole("my-role");
        }

        @Override
        public boolean test(PromptInfo prompt, McpConnection connection) {
            return identity.hasRole("my-role");
        }

        @Override
        public boolean test(ResourceInfo resource, McpConnection connection) {
            return identity.hasRole("my-role");
        }
    }

}
