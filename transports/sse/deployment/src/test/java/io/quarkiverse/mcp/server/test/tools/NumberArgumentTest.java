package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class NumberArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("get_list_of_strings", new JsonObject()
                        .put("id", 10)
                        .put("alpha", 1L)
                        .put("bravo", 10.1)
                        .put("delta", 20.1)
                        .put("charlie", 11)
                        .put("echo", 42.1).getMap(), r -> {
                            assertFalse(r.isError());
                            assertEquals("10:1:10.1:20.1:11:42.1", r.content().get(0).asText().text());
                        })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool(name = "get_list_of_strings")
        public String getListOfStrings(Long id, short alpha, Float bravo, Double delta, Byte charlie, Optional<Double> echo) {
            return id + ":" + alpha + ":" + bravo + ":" + delta + ":" + charlie + ":" + echo.orElse(1.0);
        }
    }
}
