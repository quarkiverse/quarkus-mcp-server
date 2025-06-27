package io.quarkiverse.mcp.server.test.devmode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusDevModeTest;

public class ToolChangeTest extends McpServerTest {

    @RegisterExtension
    final static QuarkusDevModeTest test = new QuarkusDevModeTest()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testChange() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .toolsCall("bravo", Map.of("price", 10), r -> {
                    assertTrue(r.isError());
                    assertEquals("Business error", r.content().get(0).asText().text());
                })
                .thenAssertResults();
        client.disconnect();

        test.modifySourceFile(MyTools.class, new Function<String, String>() {
            @Override
            public String apply(String source) {
                return source.replace("throw new ToolCallException(\"Business error\");", "return \"\" + price * 42;");
            }
        });

        // re-init the client
        client.connect();

        client.when()
                .toolsCall("bravo", Map.of("price", 10), r -> {
                    assertFalse(r.isError());
                    assertEquals("420", r.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

}
