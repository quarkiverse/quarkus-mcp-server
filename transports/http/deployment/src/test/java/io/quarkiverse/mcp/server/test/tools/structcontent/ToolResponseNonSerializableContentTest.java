package io.quarkiverse.mcp.server.test.tools.structcontent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Reproducer for <a href="https://github.com/quarkiverse/quarkus-mcp-server/issues/1115">#1115</a>.
 * <p>
 * The {@link User} POJO has no getters and no public fields, hence it cannot be serialized to JSON by Jackson.
 * When such an object is used as the structured content of a tool response the server must report an error
 * back to the client instead of silently hanging until the client times out.
 */
public class ToolResponseNonSerializableContentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class, User.class));

    @Test
    public void testNonSerializableStructuredContent() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("beta")
                .withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.code()))
                .send()
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool(title = "beta", description = "Beta user", structuredContent = true)
        User beta() {
            return new User("snowdrop");
        }
    }

    public static class User {

        String name;

        public User(String userName) {
            name = userName;
        }
    }

}
