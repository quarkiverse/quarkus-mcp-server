package io.quarkiverse.mcp.server.test.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;

public class MetaTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testRawMessage() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("foo")
                .withArguments(Map.of("count", 2))
                .withMetadata(Map.of("count", 1))
                .withAssert(toolResponse -> {
                    assertEquals("foofoo", toolResponse.content().get(0).asText().text());
                })
                .send()
                .toolsCall("baz")
                .withArguments(Map.of("count", 3))
                .withMetadata(Map.of("count", 2))
                .withAssert(toolResponse -> {
                    assertEquals("bazbazbazbazbazbaz", toolResponse.content().get(0).asText().text());
                })
                .send()
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool(description = "Bar")
        String foo(Meta meta, int count) {
            int rawCount = meta.asJsonObject().getInteger("count");
            if (rawCount != Integer.parseInt(meta.getValue(MetaKey.of("count")).toString())) {
                throw new IllegalStateException();
            }
            return "foo".repeat(count * rawCount);
        }

        @Inject
        ToolManager toolManager;

        @Startup
        void onStart() {
            toolManager.newTool("baz")
                    .setDescription("Barbar")
                    .setHandler(toolArgs -> {
                        int rawCount = toolArgs.meta().asJsonObject().getInteger("count");
                        return ToolResponse
                                .success("baz".repeat(rawCount * Integer.parseInt(toolArgs.args().get("count").toString())));
                    })
                    .register();
        }

    }
}
